package org.integratedmodelling.klab.ide.components;

import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Asynchronous callers traverse bounded neighborhoods using revision-scoped reasoner caches. */
final class WorldviewGraphModel {
  enum Relation {
    SUBCLASS("Subclass", true), AFFECTS("Affects", true), CREATES("Creates", true),
    MARKS("Marks", true), INCREASES_WITH("Increases with", true), DECREASES_WITH("Decreases with", true),
    DISCRETIZES("Discretizes", true), CLASSIFIES("Classifies", true),
    DESCRIBES("Described type", true), TRAITS("Inherited predicates", false), ROLES("Roles", false),
    INHERENT("Of / within", false), ADJACENT("Adjacent to", false),
    CAUSED("Caused by", false), CAUSANT("Causing", false), COMPRESENT("With", false),
    GOAL("For", false), COOCCURRENT("During", false), RELATIVE("Relative to", false),
    SOURCE("Links: source", true), TARGET("Links: target", true),
    APPLIES("Applies to", false), IMPLIES("Implies roles", false), OPERANDS("Logical operands", false);
    final String label;
    final boolean selected;
    Relation(String label, boolean selected) { this.label = label; this.selected = selected; }
  }

  record Link(String source, String target, String label) {}
  record Snapshot(Map<String, Concept> concepts, Set<Link> links, List<String> unresolved, boolean truncated) {}
  private record Neighborhood(Concept concept, Set<Link> links, String unresolved) {}
  private static final int CONCURRENCY = 6;
  private static final class Cache {
    final Map<String, Concept> concepts = new ConcurrentHashMap<>();
    final Map<String, Map<Relation, Set<Link>>> relations = new ConcurrentHashMap<>();
    final Map<String, Collection<SemanticInfluence>> influences = new ConcurrentHashMap<>();
  }
  private final Reasoner reasoner;
  private volatile Cache cache = new Cache();

  WorldviewGraphModel(Reasoner reasoner) { this.reasoner = reasoner; }

  void accept(Concept concept) {
    invalidate();
    cache.concepts.put(concept.getUrn(), concept);
  }

  void invalidate() {
    var replacement = new Cache();
    replacement.concepts.putAll(cache.concepts);
    cache = replacement;
  }

  // In-flight requests retain their old cache; late replies cannot repopulate a new revision.
  void refreshKnowledge() { cache = new Cache(); }

  Concept resolve(String urn) { return resolve(urn, cache); }

  private Concept resolve(String urn, Cache state) {
    checkCancelled();
    var known = state.concepts.get(urn);
    if (known != null) return known;
    var concept = reasoner.resolveConcept(urn);
    checkCancelled();
    if (!valid(concept)) return null; // Failed resolution is retryable, never cached as owl:Nothing.
    state.concepts.put(urn, concept);
    state.concepts.put(concept.getUrn(), concept);
    return concept;
  }

  Snapshot load(Collection<String> roots, int depth, Set<Relation> relations) {
    return load(roots, depth, relations, 150, partial -> {});
  }

  Snapshot load(Collection<String> roots, int depth, Set<Relation> relations, int limit,
      java.util.function.Consumer<Snapshot> progress) {
    if (limit < 1 || depth < 0) throw new IllegalArgumentException("Invalid graph bounds");
    var state = cache;
    var nodes = new LinkedHashMap<String, Concept>();
    var links = new LinkedHashSet<Link>();
    var unresolved = new LinkedHashSet<String>();
    var admitted = new LinkedHashSet<String>();
    boolean truncated = false;
    for (var root : roots) {
      if (admitted.size() < limit) admitted.add(root);
      else if (!admitted.contains(root)) truncated = true;
    }
    var frontier = new LinkedHashSet<>(admitted);
    var visited = new HashSet<String>();
    var requests = Executors.newFixedThreadPool(CONCURRENCY, Thread.ofVirtual().factory());
    var pending = new ArrayList<Future<Neighborhood>>();
    try {
      // Reveal the focus before waiting for its (potentially remote) relationship queries.
      if (frontier.size() == 1) {
        var root = resolve(frontier.iterator().next(), state);
        if (root != null) {
          admitted.clear(); admitted.add(root.getUrn());
          frontier.clear(); frontier.add(root.getUrn());
          nodes.put(root.getUrn(), root);
          progress.accept(snapshot(nodes, links, unresolved, truncated));
        }
      }
      for (int level = 0; level <= depth && !frontier.isEmpty(); level++) {
        var next = new LinkedHashSet<String>();
        var batch = new ArrayList<>(frontier);
        boolean expand = level < depth;
        for (int offset = 0; offset < batch.size(); offset += CONCURRENCY) {
          checkCancelled();
          pending.clear();
          for (var urn : batch.subList(offset, Math.min(offset + CONCURRENCY, batch.size()))) {
            if (visited.add(urn)) pending.add(requests.submit(() -> neighborhood(urn, expand, relations, state)));
          }
          for (var request : pending) {
            var result = request.get();
            if (result.unresolved() != null) { unresolved.add(result.unresolved()); continue; }
            nodes.put(result.concept().getUrn(), result.concept());
            for (var edge : result.links()) {
              for (var endpoint : List.of(edge.source(), edge.target())) {
                if (!admitted.contains(endpoint)) {
                  if (admitted.size() < limit) admitted.add(endpoint);
                  else truncated = true;
                }
              }
              if (admitted.contains(edge.source()) && admitted.contains(edge.target())) {
                links.add(edge);
                if (!visited.contains(edge.source())) next.add(edge.source());
                if (!visited.contains(edge.target())) next.add(edge.target());
              }
            }
          }
        }
        progress.accept(snapshot(nodes, links, unresolved, truncated));
        frontier = next;
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new CancellationException();
    } catch (ExecutionException failure) {
      if (failure.getCause() instanceof CancellationException cancelled) throw cancelled;
      throw new IllegalStateException("Reasoner graph query failed", failure.getCause());
    } finally {
      pending.forEach(request -> request.cancel(true));
      requests.shutdownNow();
    }
    return snapshot(nodes, links, unresolved, truncated);
  }

  private static Snapshot snapshot(Map<String, Concept> nodes, Set<Link> links,
      Collection<String> unresolved, boolean truncated) {
    var visibleLinks = new LinkedHashSet<>(links);
    visibleLinks.removeIf(edge -> !nodes.containsKey(edge.source()) || !nodes.containsKey(edge.target()));
    return new Snapshot(Collections.unmodifiableMap(new LinkedHashMap<>(nodes)),
        Collections.unmodifiableSet(visibleLinks), List.copyOf(unresolved), truncated);
  }

  private Neighborhood neighborhood(String urn, boolean expand, Set<Relation> relations, Cache state) {
    var concept = resolve(urn, state);
    if (concept == null) return new Neighborhood(null, Set.of(), urn);
    var links = new LinkedHashSet<Link>();
    if (expand) {
      var byRelation = state.relations.computeIfAbsent(concept.getUrn(), key -> new ConcurrentHashMap<>());
      for (var relation : relations) {
        checkCancelled();
        links.addAll(byRelation.computeIfAbsent(relation, key -> query(concept, key, state)));
      }
    }
    return new Neighborhood(concept, links, null);
  }

  private static boolean valid(Concept concept) {
    return concept != null && concept.getUrn() != null && !concept.is(SemanticType.NOTHING)
        && !"owl:Nothing".equals(concept.getUrn());
  }
  private Set<Link> query(Concept concept, Relation relation, Cache state) {
    checkCancelled();
    var ret = new LinkedHashSet<Link>();
    switch (relation) {
      case SUBCLASS -> {
        for (var parent : reasoner.parents(concept)) add(state, ret, concept, parent, "is");
        for (var child : reasoner.children(concept)) add(state, ret, child, concept, "is");
      }
      case AFFECTS, CREATES, MARKS, INCREASES_WITH, DECREASES_WITH, DISCRETIZES, CLASSIFIES -> {
        for (var influence : state.influences.computeIfAbsent(concept.getUrn(), key -> reasoner.influences(concept)))
          if (influence.kind().name().equals(relation.name()))
            add(state, ret, influence.source() == null ? concept : influence.source(), influence.target(),
                influence.kind().name().toLowerCase(Locale.ROOT).replace('_', ' '));
      }
      case DESCRIBES -> add(state, ret, concept, reasoner.describedType(concept), "describes");
      case TRAITS -> addAll(state, ret, concept, reasoner.directTraits(concept), "inherits");
      case ROLES -> addAll(state, ret, concept, reasoner.directRoles(concept), "role");
      case INHERENT -> add(state, ret, concept, reasoner.directInherent(concept), "of");
      case ADJACENT -> add(state, ret, concept, reasoner.directAdjacent(concept), "adjacent to");
      case CAUSED -> add(state, ret, concept, reasoner.directCaused(concept), "caused by");
      case CAUSANT -> add(state, ret, concept, reasoner.directCausant(concept), "causing");
      case COMPRESENT -> add(state, ret, concept, reasoner.directCompresent(concept), "with");
      case GOAL -> add(state, ret, concept, reasoner.directGoal(concept), "for");
      case COOCCURRENT -> add(state, ret, concept, reasoner.directCooccurrent(concept), "during");
      case RELATIVE -> add(state, ret, concept, reasoner.directRelativeTo(concept), "relative to");
      case SOURCE -> addAll(state, ret, concept, reasoner.relationshipSources(concept), "links source");
      case TARGET -> addAll(state, ret, concept, reasoner.relationshipTargets(concept), "links target");
      case APPLIES -> addAll(state, ret, concept, reasoner.applicableObservables(concept), "applies to");
      case IMPLIES -> {
        if (concept.is(SemanticType.ROLE)) addAll(state, ret, concept, reasoner.impliedRoles(concept, false), "implies");
      }
      case OPERANDS -> addAll(state, ret, concept, reasoner.operands(concept), "operand");
    }
    return ret;
  }

  private void addAll(Cache state, Set<Link> links, Concept source, Collection<Concept> targets, String label) {
    for (var target : targets) add(state, links, source, target, label);
  }

  private void add(Cache state, Set<Link> links, Concept source, Concept target, String label) {
    if (!valid(source) || !valid(target)) return;
    state.concepts.put(source.getUrn(), source);
    state.concepts.put(target.getUrn(), target);
    links.add(new Link(source.getUrn(), target.getUrn(), label));
  }

  private static void checkCancelled() {
    if (Thread.currentThread().isInterrupted()) throw new CancellationException();
  }
}

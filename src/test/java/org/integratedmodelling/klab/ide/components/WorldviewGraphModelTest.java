package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class WorldviewGraphModelTest {
  @Test void boundsBreadthPublishesFocusFirstAndReusesRevisionCache() {
    var root = concept("test:Subject");
    var children = java.util.stream.IntStream.range(0, 1000)
        .mapToObj(i -> concept("test:Child" + i)).toList();
    var calls = new AtomicInteger();
    var progress = new ArrayList<WorldviewGraphModel.Snapshot>();
    var model = new WorldviewGraphModel(reasoner(Map.of(root.getUrn(), root), (method, concept) -> {
      assertFalse(progress.isEmpty(), "Focus must be published before remote edge queries");
      calls.incrementAndGet();
      return method.equals("children") && concept == root ? children : List.of();
    }));
    var relations = EnumSet.of(WorldviewGraphModel.Relation.SUBCLASS);
    var result = model.load(List.of(root.getUrn()), 1, relations, 10, progress::add);
    assertEquals(Set.of(root.getUrn()), progress.getFirst().concepts().keySet());
    assertEquals(10, result.concepts().size());
    assertEquals(9, result.links().size());
    assertTrue(result.truncated());
    assertEquals(2, calls.get(), "Boundary nodes must not query relationships");
    model.load(List.of(root.getUrn()), 1, relations, 10, partial -> {});
    assertEquals(2, calls.get(), "Revisiting a neighborhood uses cached queries");
    model.refreshKnowledge();
    model.load(List.of(root.getUrn()), 1, relations, 10, partial -> {});
    assertEquals(4, calls.get(), "Worldview revisions invalidate cached queries");
  }

  @Test void failedResolutionIsReportedWithoutRenderingOrCachingNothing() {
    var nothing = new ConceptImpl(); nothing.setUrn("owl:Nothing"); nothing.getType().add(SemanticType.NOTHING);
    var resolved = new java.util.concurrent.atomic.AtomicReference<Concept>(nothing);
    var reasoner = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class},
        (proxy, method, args) -> {
          assertEquals("resolveConcept", method.getName());
          return resolved.get();
        });
    var model = new WorldviewGraphModel(reasoner);
    var failed = model.load(List.of("test:Missing"), 1, EnumSet.of(WorldviewGraphModel.Relation.SUBCLASS));
    assertTrue(failed.concepts().isEmpty()); assertTrue(failed.links().isEmpty());
    assertEquals(List.of("test:Missing"), failed.unresolved());
    resolved.set(concept("test:Missing"));
    assertEquals(1, model.load(List.of("test:Missing"), 0,
        EnumSet.noneOf(WorldviewGraphModel.Relation.class)).concepts().size());
  }

  @Test void delayedRemoteResolutionsOverlapButRemainBounded() {
    var active = new AtomicInteger(); var maximum = new AtomicInteger();
    var overlap = new java.util.concurrent.CountDownLatch(2);
    var reasoner = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class},
        (proxy, method, args) -> {
          int current = active.incrementAndGet(); maximum.accumulateAndGet(current, Math::max);
          try {
            overlap.countDown();
            assertTrue(overlap.await(5, java.util.concurrent.TimeUnit.SECONDS), "Requests should overlap");
            Thread.sleep(20);
            return concept((String) args[0]);
          } finally { active.decrementAndGet(); }
        });
    var roots = java.util.stream.IntStream.range(0, 18).mapToObj(i -> "test:Concept" + i).toList();
    var graph = new WorldviewGraphModel(reasoner).load(roots, 0, EnumSet.noneOf(WorldviewGraphModel.Relation.class));
    assertEquals(18, graph.concepts().size());
    assertTrue(maximum.get() > 1); assertTrue(maximum.get() <= 6);
  }

  private Concept concept(String urn) {
    var concept = new ConceptImpl();
    concept.setUrn(urn);
    concept.setType(EnumSet.of(SemanticType.SUBJECT));
    return concept;
  }

  private Reasoner reasoner(Map<String, Concept> concepts,
      java.util.function.BiFunction<String, Concept, Object> query) {
    return (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[] {Reasoner.class},
        (proxy, method, args) -> {
          if (method.getName().equals("resolveConcept")) return concepts.get(args[0]);
          return query.apply(method.getName(), (Concept) args[0]);
        });
  }

  @Test void neighborhoodHonorsDepthAndTraversesSubclassBothWaysWithoutCycles() {
    var a = concept("test:A"); var b = concept("test:B"); var c = concept("test:C");
    var model = new WorldviewGraphModel(reasoner(Map.of(a.getUrn(), a, b.getUrn(), b, c.getUrn(), c),
        (method, concept) -> switch (method) {
          case "parents" -> concept == a ? List.of(b) : concept == b ? List.of(c) : List.of(a);
          case "children" -> concept == a ? List.of(c) : concept == b ? List.of(a) : List.of(b);
          default -> throw new AssertionError(method);
        }));
    var shallow = model.load(List.of(a.getUrn()), 0, EnumSet.of(WorldviewGraphModel.Relation.SUBCLASS));
    assertEquals(Set.of(a.getUrn()), shallow.concepts().keySet());
    assertTrue(shallow.links().isEmpty());
    var graph = model.load(List.of(a.getUrn()), 4, EnumSet.of(WorldviewGraphModel.Relation.SUBCLASS));
    assertEquals(3, graph.concepts().size());
    assertEquals(3, graph.links().size());
    assertTrue(graph.links().contains(new WorldviewGraphModel.Link(a.getUrn(), b.getUrn(), "is")));
  }

  @Test void typedInfluencesKeepDirectionAndParallelRelationshipLabels() {
    var a = concept("test:A"); var b = concept("test:B");
    var model = new WorldviewGraphModel(reasoner(Map.of(a.getUrn(), a, b.getUrn(), b),
        (method, concept) -> List.of(
            new SemanticInfluence(a, b, SemanticInfluence.Kind.MARKS, "asserted"),
            new SemanticInfluence(a, b, SemanticInfluence.Kind.INCREASES_WITH, "asserted"))));
    var graph = model.load(List.of(b.getUrn()), 1, EnumSet.of(WorldviewGraphModel.Relation.MARKS));
    assertEquals(Set.of(new WorldviewGraphModel.Link(a.getUrn(), b.getUrn(), "marks")), graph.links());
    var both = model.load(List.of(b.getUrn()), 1,
        EnumSet.of(WorldviewGraphModel.Relation.MARKS, WorldviewGraphModel.Relation.INCREASES_WITH));
    assertEquals(2, both.links().size());
  }

  @Test void overviewIncludesIsolatedConceptsAndNeverQueriesDisabledRelations() {
    var a = concept("test:A"); var b = concept("test:B");
    var model = new WorldviewGraphModel(reasoner(Map.of(a.getUrn(), a, b.getUrn(), b),
        (method, concept) -> { throw new AssertionError("Unexpected query: " + method); }));
    var graph = model.load(List.of(a.getUrn(), b.getUrn()), 1, EnumSet.noneOf(WorldviewGraphModel.Relation.class));
    assertEquals(2, graph.concepts().size());
    assertTrue(graph.links().isEmpty());
  }

  @Test void acceptingComposedConceptInvalidatesCachedEdgesAndUsesReturnedConcept() {
    var a = concept("test:A"); var b = concept("test:B");
    var calls = new AtomicInteger();
    var model = new WorldviewGraphModel(reasoner(Map.of(a.getUrn(), a), (method, concept) -> {
      calls.incrementAndGet(); return List.of();
    }));
    var relations = EnumSet.of(WorldviewGraphModel.Relation.SUBCLASS);
    model.load(List.of(a.getUrn()), 1, relations);
    model.load(List.of(a.getUrn()), 1, relations);
    assertEquals(2, calls.get());
    model.accept(b);
    var graph = model.load(List.of(a.getUrn(), b.getUrn()), 1, relations);
    assertSame(b, graph.concepts().get(b.getUrn()));
    assertEquals(6, calls.get());
  }
}

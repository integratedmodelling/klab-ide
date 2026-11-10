package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class WorldviewGraphModelTest {
  @Test void ofDistinguishesNativeBearersFromPredicateApplicationAndConfigurations() {
    var bearer = concept("test:Tree");
    var quality = typedConcept("test:Height", SemanticType.QUALITY);
    var process = typedConcept("test:Growth", SemanticType.PROCESS);
    var predicate = typedConcept("test:Species", SemanticType.IDENTITY);
    var configuration = typedConcept("test:Network", SemanticType.CONFIGURATION);
    var queries = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var model = new WorldviewGraphModel(reasoner(Map.of(quality.getUrn(), quality,
        process.getUrn(), process, predicate.getUrn(), predicate,
        configuration.getUrn(), configuration, bearer.getUrn(), bearer), (method, concept) -> {
      queries.add(method + ":" + concept.getUrn());
      return switch (method) {
        case "directInherent" -> bearer;
        case "applicableObservables" -> List.of(bearer);
        default -> throw new AssertionError(method);
      };
    }));
    var nativeOnly = model.load(List.of(quality.getUrn(), process.getUrn(), predicate.getUrn(),
        configuration.getUrn(), bearer.getUrn()), 1, EnumSet.of(WorldviewGraphModel.Relation.INHERENT));
    assertEquals(Set.of(new WorldviewGraphModel.Link(quality.getUrn(), bearer.getUrn(), "of"),
        new WorldviewGraphModel.Link(process.getUrn(), bearer.getUrn(), "of")), nativeOnly.links());
    assertEquals(Set.of("directInherent:" + quality.getUrn(), "directInherent:" + process.getUrn()),
        new HashSet<>(queries));
    var application = model.load(List.of(predicate.getUrn()), 1,
        EnumSet.of(WorldviewGraphModel.Relation.APPLIES));
    assertEquals(Set.of(new WorldviewGraphModel.Link(predicate.getUrn(), bearer.getUrn(), "applies to")),
        application.links(), "The two predicate applicability spellings must not duplicate edges");
  }

  @Test void containmentIsAnIncidentDirectedDeclarationIndependentOfInherency() {
    var region = concept("test:Region"); var stream = concept("test:Stream");
    var reach = concept("test:Reach");
    var gradient = typedConcept("test:Gradient", SemanticType.QUALITY);
    var within = statement("Stream");
    within.getObservationalContainments().add(new KimConceptStatement.ObservationalContainment(
        syntax(region.getUrn()), syntax(stream.getUrn())));
    var contains = statement("Stream");
    contains.getObservationalContainments().add(new KimConceptStatement.ObservationalContainment(
        syntax(stream.getUrn()), syntax(reach.getUrn())));
    var model = new WorldviewGraphModel(reasoner(Map.of(region.getUrn(), region,
        stream.getUrn(), stream, reach.getUrn(), reach, gradient.getUrn(), gradient),
        (method, concept) -> {
          assertEquals("directInherent", method);
          return stream;
        }), worldview(within, contains));
    var containment = EnumSet.of(WorldviewGraphModel.Relation.CONTAINS);
    var graph = model.load(List.of(stream.getUrn()), 1, containment);
    assertEquals(Set.of(new WorldviewGraphModel.Link(region.getUrn(), stream.getUrn(), "contains (declared)"),
        new WorldviewGraphModel.Link(stream.getUrn(), reach.getUrn(), "contains (declared)")), graph.links());
    assertFalse(graph.links().contains(new WorldviewGraphModel.Link(region.getUrn(), reach.getUrn(),
        "contains (declared)")), "Containment contracts are not transitive");
    var nativeGraph = model.load(List.of(gradient.getUrn()), 1,
        EnumSet.of(WorldviewGraphModel.Relation.INHERENT, WorldviewGraphModel.Relation.CONTAINS));
    assertEquals(Set.of(new WorldviewGraphModel.Link(gradient.getUrn(), stream.getUrn(), "of")),
        nativeGraph.links(), "The container must never replace the native bearer");
    model.refreshKnowledge(worldview());
    assertTrue(model.load(List.of(stream.getUrn()), 1, containment).links().isEmpty(),
        "Refreshing the worldview must retract removed declarations");
  }

  @Test void emergenceKeepsBooleanIngredientsTogetherAndApplicationScopeSeparate() {
    var configuration = typedConcept("test:Network", SemanticType.CONFIGURATION);
    var ingredients = typedConcept("(test:Connection or test:Flow)", SemanticType.OBSERVABLE);
    var scope = concept("test:Region");
    var declaration = statement("Network");
    declaration.getEmergenceTriggers().add(syntax(ingredients.getUrn()));
    var model = new WorldviewGraphModel(reasoner(Map.of(configuration.getUrn(), configuration,
        ingredients.getUrn(), ingredients, scope.getUrn(), scope), (method, concept) -> {
      assertEquals("applicableObservables", method);
      return concept == configuration ? List.of(scope) : List.of();
    }), worldview(declaration));
    var graph = model.load(List.of(configuration.getUrn()), 1,
        EnumSet.of(WorldviewGraphModel.Relation.EMERGENCE, WorldviewGraphModel.Relation.APPLIES,
            WorldviewGraphModel.Relation.INHERENT));
    assertEquals(Set.of(new WorldviewGraphModel.Link(configuration.getUrn(), ingredients.getUrn(),
            "emerges from (declared)"),
        new WorldviewGraphModel.Link(configuration.getUrn(), scope.getUrn(), "applies to")), graph.links());
    assertEquals(3, graph.concepts().size(), "OR alternatives must remain one scoped expression");
  }

  @Test void predicateQualificationsAndBearerRequirementsAreSeparateLayers() {
    var quality = typedConcept("test:Height", SemanticType.QUALITY);
    var identity = typedConcept("test:Areal", SemanticType.IDENTITY);
    var attribute = typedConcept("test:Average", SemanticType.ATTRIBUTE);
    var realm = typedConcept("test:Crust", SemanticType.REALM);
    var declaration = statement("Height");
    declaration.getRequiredIdentities().add(syntax(identity.getUrn()));
    declaration.getRequiredRealms().add(syntax(realm.getUrn()));
    var model = new WorldviewGraphModel(reasoner(Map.of(quality.getUrn(), quality), (method, concept) ->
        switch (method) {
          case "directIdentities" -> List.of();
          case "directAttributes" -> List.of(attribute);
          case "directRealms" -> List.of(realm);
          default -> throw new AssertionError(method);
        }), worldview(declaration));
    model.accept(identity); model.accept(realm);
    var graph = model.load(List.of(quality.getUrn()), 1,
        EnumSet.of(WorldviewGraphModel.Relation.IDENTITIES, WorldviewGraphModel.Relation.ATTRIBUTES,
            WorldviewGraphModel.Relation.REALMS, WorldviewGraphModel.Relation.REQUIRED_IDENTITIES,
            WorldviewGraphModel.Relation.REQUIRED_REALMS));
    assertEquals(Set.of(new WorldviewGraphModel.Link(quality.getUrn(), identity.getUrn(), "requires identity (declared)"),
        new WorldviewGraphModel.Link(quality.getUrn(), realm.getUrn(), "requires realm (declared)"),
        new WorldviewGraphModel.Link(quality.getUrn(), attribute.getUrn(), "attribute"),
        new WorldviewGraphModel.Link(quality.getUrn(), realm.getUrn(), "realm")), graph.links());
  }

  @Test void disabledSourceLayersResolveNoEndpointsAndInvalidContainmentDoesNotSupplyABearer() {
    var region = concept("test:Region");
    var quality = typedConcept("test:Height", SemanticType.QUALITY);
    var declaration = statement("Region");
    declaration.getObservationalContainments().add(new KimConceptStatement.ObservationalContainment(
        syntax(region.getUrn()), syntax(quality.getUrn())));
    var resolutions = new AtomicInteger();
    var remote = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(),
        new Class<?>[]{Reasoner.class}, (proxy, method, args) -> {
          assertEquals("resolveConcept", method.getName());
          resolutions.incrementAndGet();
          return region.getUrn().equals(args[0]) ? region : quality;
        });
    var model = new WorldviewGraphModel(remote, worldview(declaration));
    model.load(List.of(region.getUrn()), 1, EnumSet.noneOf(WorldviewGraphModel.Relation.class));
    assertEquals(1, resolutions.get());
    assertTrue(model.load(List.of(region.getUrn()), 1,
        EnumSet.of(WorldviewGraphModel.Relation.CONTAINS)).links().isEmpty());
  }

  private static KimConceptImpl syntax(String urn) {
    var result = new KimConceptImpl(); result.setUrn(urn); return result;
  }

  private static KimConceptStatementImpl statement(String name) {
    var result = new KimConceptStatementImpl(); result.setUrn(name); return result;
  }

  private static WorldviewImpl worldview(KimConceptStatement... statements) {
    var result = new WorldviewImpl();
    var ontology = new KimOntologyImpl(); ontology.setUrn("test");
    ontology.getStatements().addAll(List.of(statements)); result.getOntologies().add(ontology);
    return result;
  }

  private static ConceptImpl typedConcept(String urn, SemanticType type) {
    var result = new ConceptImpl(); result.setUrn(urn); result.getType().add(type); return result;
  }
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

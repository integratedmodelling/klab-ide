package org.integratedmodelling.klab.ide.components.treeviews;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ReconnectedGraphTest {
  @Test void contextRootLoadsPersistedCohortsAndMembersWithoutSessionActivityOrFocus() {
    var cohort = new CohortImpl(); cohort.setId(10);
    var observation = new ObservationImpl(); observation.setId(11);
    var links = new ArrayList<KnowledgeGraph.Link>();
    links.add(link(RuntimeAsset.CONTEXT_ASSET, cohort, GraphModel.Relationship.HAS_CHILD));
    links.add(link(cohort, observation, GraphModel.Relationship.HAS_MEMBER));
    var kg = proxy(KnowledgeGraph.class, (object, method, args) -> {
      if (!method.getName().equals("getLinks")) throw new AssertionError(method);
      long id = ((RuntimeAsset) args[0]).getId();
      boolean outgoing = args[1] == GraphModel.Relationship.Direction.OUTGOING;
      return links.stream().filter(l -> (outgoing ? l.source() : l.target()).getId() == id).toList();
    });
    var twin = proxy(DigitalTwin.class, (object, method, args) -> {
      if (!method.getName().equals("getKnowledgeGraph")) throw new AssertionError(method);
      return kg;
    });
    var scope = proxy(ContextScope.class, (object, method, args) -> {
      if (!method.getName().equals("getDigitalTwin")) throw new AssertionError(method);
      return twin;
    });
    var types = Set.of(RuntimeAsset.Type.OBSERVATION, RuntimeAsset.Type.COHORT);
    var relationships = Set.of(GraphModel.Relationship.HAS_CHILD, GraphModel.Relationship.HAS_MEMBER);
    var graph = TreeModel.createGraph(RuntimeAsset.CONTEXT_ASSET, 2, scope, types, relationships, null);
    assertEquals(Set.of(RuntimeAsset.CONTEXT_ASSET, cohort, observation), graph.vertexSet());
    assertEquals(2, graph.edgeSet().size());
    // A shared partner adds a member. A subsequent refresh is rooted in persisted state,
    // without requiring this client to have submitted any observation or received an activity.
    var shared = new ObservationImpl(); shared.setId(12);
    links.add(link(cohort, shared, GraphModel.Relationship.HAS_MEMBER));
    var refreshed = TreeModel.createGraph(RuntimeAsset.CONTEXT_ASSET, 2, scope, types, relationships, null);
    assertTrue(refreshed.containsVertex(shared));
    assertEquals(3, refreshed.edgeSet().size());
  }
  private static KnowledgeGraph.Link link(RuntimeAsset from, RuntimeAsset to, GraphModel.Relationship type) {
    var link = new LinkImpl(); link.setSource(from); link.setTarget(to); link.setRelationship(type);
    return link;
  }
  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }
}

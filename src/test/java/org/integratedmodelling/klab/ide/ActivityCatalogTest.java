package org.integratedmodelling.klab.ide;

import org.junit.jupiter.api.Test;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.documentation.FlowChart;
import static org.junit.jupiter.api.Assertions.*;

class ActivityCatalogTest {
  @Test void completionReplacesAllFieldsAndReconnectsOutOfOrderChildren() {
    var catalog = new ActivityCatalog();
    var parent = Activity.of(Activity.Type.RESOLUTION, "Parent");
    var started = Activity.of(Activity.Type.MEASURE, "Started");
    catalog.accept(started, false);
    var before = catalog.snapshot();
    var finished = Activity.of(Activity.Type.MEASURE, "Completed", parent, Activity.Outcome.SUCCESS);
    finished.setTransientId(started.getTransientId());
    finished.setId(123);
    finished.getMetadata().put(Metadata.IM_DATAFLOW_GRAPH, new FlowChart());
    catalog.accept(finished, true);
    catalog.accept(parent, false);
    catalog.accept(started, false);
    var graph = catalog.snapshot();
    assertEquals(2, graph.vertexSet().size());
    assertTrue(graph.containsEdge(parent, finished));
    assertFalse(graph.containsVertex(started));
    assertEquals(123, finished.getId());
    assertTrue(finished.getMetadata().get(Metadata.IM_DATAFLOW_GRAPH) instanceof FlowChart);
    assertEquals(1, before.vertexSet().size());
    assertTrue(before.containsVertex(started));
  }

  @Test void transportedActivitiesKeepHierarchyAndTypedChartsForEveryType() throws Exception {
    var parent = Activity.of(Activity.Type.SUBMISSION, "Submission");
    for (var type : Activity.Type.values()) {
      var catalog = new ActivityCatalog();
      var activity = Activity.of(type, parent, "Description", Activity.Outcome.SUCCESS);
      activity.getMetadata().put(Metadata.IM_DATAFLOW_GRAPH,
          FlowChart.builder("plan").root(root -> root.node("step", node -> node.label("Step"))).build());
      var message = org.integratedmodelling.klab.api.services.runtime.Message.create("client",
          org.integratedmodelling.klab.api.services.runtime.Message.MessageClass.DigitalTwin,
          org.integratedmodelling.klab.api.services.runtime.Message.MessageType.ActivityFinished, activity);
      var restored = org.integratedmodelling.common.utils.Utils.Json.parseObject(
          org.integratedmodelling.common.utils.Utils.Json.asString(message),
          org.integratedmodelling.klab.api.services.runtime.Message.class).getPayload(Activity.class);
      catalog.accept(restored, true);
      catalog.accept(parent, false);
      assertTrue(catalog.snapshot().containsEdge(parent, restored));
      assertEquals(type, restored.getType());
      assertEquals("Description", ActivityPresentation.description(restored));
      assertInstanceOf(FlowChart.class, restored.getMetadata().get(Metadata.IM_DATAFLOW_GRAPH)).validate();
    }
  }

  @Test void durableTriggerCanLinkWithoutLiveParentIdentity() {
    var catalog = new ActivityCatalog();
    var parent = Activity.of(Activity.Type.SUBMISSION);
    parent.setUrn("activity:parent");
    var child = Activity.of(Activity.Type.CLASSIFICATION);
    child.setTriggeringActivityUrn(parent.getUrn());
    catalog.accept(child, true);
    catalog.accept(parent, true);
    assertTrue(catalog.snapshot().containsEdge(parent, child));
  }

  @Test void allTypesAndMissingDescriptionsHaveReadableLabels() {
    for (var type : Activity.Type.values()) {
      var activity = Activity.of(type);
      assertFalse(ActivityPresentation.description(activity).isBlank());
      assertFalse(ActivityPresentation.type(activity).contains("_"));
    }
    assertEquals("Activity", ActivityPresentation.description(null));
    assertEquals("Activity", ActivityPresentation.description(Activity.of()));
  }
}

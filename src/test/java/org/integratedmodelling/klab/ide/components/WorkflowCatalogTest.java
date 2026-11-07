package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.junit.jupiter.api.Test;

class WorkflowCatalogTest {
  @Test void latestOnlyUsesNumericVersionsAndStableIdentity() {
    var old = workflow("review", "0.2");
    var current = workflow("review", "0.10");
    var other = workflow("other", "1.0");
    assertEquals(List.of(current, other), WorkflowCatalog.latest(Arrays.asList(old, null, current, old, other)));
    assertEquals("Review (v0.10)", WorkflowCatalog.label(current));
  }
  @Test void handlesMissingCatalogAndEqualVersions() {
    assertTrue(WorkflowCatalog.latest(null).isEmpty());
    var first = workflow("review", "0.3");
    assertEquals(List.of(first), WorkflowCatalog.latest(List.of(first, workflow("review", "0.3"))));
  }
  @Test void releaseSortsAbovePrereleaseWithoutNullModifierFailure() {
    assertTrue(WorkflowCatalog.compare("0.3", "0.3-rc1") > 0);
    assertTrue(WorkflowCatalog.compare("0.3-rc1", "0.3") < 0);
    assertEquals(0, WorkflowCatalog.compare("0.3", "0.3.0"));
  }
  private static WorkflowImpl workflow(String id, String version) {
    var workflow = new WorkflowImpl(); workflow.setId(id); workflow.setVersion(version);
    workflow.setName("Review"); return workflow;
  }
}

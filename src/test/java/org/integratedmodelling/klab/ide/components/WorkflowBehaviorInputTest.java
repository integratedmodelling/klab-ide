package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior;
import org.junit.jupiter.api.Test;

class WorkflowBehaviorInputTest {
  private WorkflowBehavior.Parameter parameter(String type) {
    return new WorkflowBehavior.Parameter("value", type, null);
  }
  @Test void parsesTextBooleanAndNumericModalInputs() {
    assertEquals("hello", WorkflowEditor.behaviorInput(parameter(null), "hello"));
    assertEquals(true, WorkflowEditor.behaviorInput(parameter("java.lang.Boolean"), "true"));
    assertEquals(42L, WorkflowEditor.behaviorInput(parameter("Long"), "42"));
    assertEquals(1.5d, WorkflowEditor.behaviorInput(parameter("double"), "1.5"));
  }
  @Test void rejectsInvalidAndUnsupportedInputsBeforeSubmitting() {
    assertThrows(IllegalArgumentException.class, () -> WorkflowEditor.behaviorInput(parameter("boolean"), "yes"));
    assertThrows(IllegalArgumentException.class, () -> WorkflowEditor.behaviorInput(parameter("Integer"), "4.5"));
    assertThrows(IllegalArgumentException.class, () -> WorkflowEditor.behaviorInput(parameter("java.time.Instant"), "now"));
    assertThrows(IllegalArgumentException.class, () -> WorkflowEditor.behaviorInput(
        new WorkflowBehavior.Parameter("agent", null, "examples.agent"), "agent-urn"));
  }
}

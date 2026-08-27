package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceEditorTest {

  @Test
  void matchesEachParsedResultToTheSourceThatWasActuallySaved() {
    Map<String, Deque<String>> pending = new HashMap<>();
    pending.put("demo", new ArrayDeque<>());
    pending.get("demo").add("first save");
    pending.get("demo").add("second save");

    assertTrue(WorkspaceEditor.consumePendingSave(pending, "demo", "first save"));
    assertTrue(pending.containsKey("demo"));
    assertFalse(WorkspaceEditor.consumePendingSave(pending, "demo", "external change"));
    assertTrue(WorkspaceEditor.consumePendingSave(pending, "demo", "second save"));
    assertFalse(pending.containsKey("demo"));
  }
}

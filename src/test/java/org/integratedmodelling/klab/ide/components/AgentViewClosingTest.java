package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.integratedmodelling.klab.api.actors.Agent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AgentViewClosingTest {
  @BeforeAll
  static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try {
      Platform.startup(() -> {
        Platform.setImplicitExit(false);
        ready.complete(null);
      });
    } catch (IllegalStateException alreadyStarted) {
      Platform.runLater(() -> ready.complete(null));
    }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test
  void closesEditorWithoutADebugTargetOrAgents() throws Exception {
    checkClose(Set.of(), null, null);
  }

  @Test
  void closesEditorWithAgentsButNoDebugTarget() throws Exception {
    // Set.copyOf matches BehaviorEditor.getDebugAgents(), including its null rejection.
    checkClose(Set.copyOf(Set.of(mock(Agent.class))), null, null);
  }

  @Test
  void closingTheDebugTargetSelectsARemainingAgent() throws Exception {
    var target = mock(Agent.class);
    var remaining = mock(Agent.class);
    checkClose(Set.of(target), target, remaining);
  }

  @Test
  void closingTheLastDebugTargetClearsIt() throws Exception {
    var target = mock(Agent.class);
    checkClose(Set.of(target), target, null);
  }

  @Test
  void closingAnUnrelatedEditorPreservesTheDebugTarget() throws Exception {
    var remaining = mock(Agent.class);
    checkClose(Set.of(mock(Agent.class)), remaining, remaining);
  }

  private static void checkClose(Set<Agent> removed, Agent target, Agent remaining)
      throws Exception {
    var result = new CompletableFuture<Void>();
    Platform.runLater(() -> {
      try {
        var view = new AgentView();
        var editor = mock(BehaviorEditor.class);
        var otherEditor = mock(BehaviorEditor.class);
        var path = Path.of("closing.kactor");
        when(editor.getFile()).thenReturn(path);
        when(editor.getDebugAgents()).thenReturn(removed);
        Map<Path, BehaviorEditor> editors = field(view, "openEditors");
        editors.put(path, editor);
        editors.put(Path.of("remaining.kactor"), otherEditor);
        Set<Agent> agents = field(view, "debugAgents");
        agents.addAll(removed);
        if (remaining != null) agents.add(remaining);
        var targetField = AgentView.class.getDeclaredField("currentDebugTarget");
        targetField.setAccessible(true);
        targetField.set(view, target);

        assertDoesNotThrow(() -> view.assetEditorClosed(editor));

        verify(editor).close();
        assertFalse(editors.containsKey(path));
        assertEquals(remaining == null ? Set.of() : Set.of(remaining), agents);
        assertSame(remaining, view.getCurrentDebugTarget());
        if (target != null && removed.contains(target)) {
          verify(otherEditor).setCurrentDebugTarget(remaining);
        } else {
          verifyNoInteractions(otherEditor);
        }
        result.complete(null);
      } catch (Throwable failure) {
        result.completeExceptionally(failure);
      }
    });
    result.get(15, TimeUnit.SECONDS);
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(AgentView view, String name) throws Exception {
    var field = AgentView.class.getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(view);
  }
}

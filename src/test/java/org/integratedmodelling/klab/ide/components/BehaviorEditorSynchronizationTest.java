package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klabeditor.MonacoEditorView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorEditorSynchronizationTest {
  @TempDir Path temporaryDirectory;

  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void refreshedTestsAndBehaviorsCompileTheNewSnapshotAndRemainRunnable() throws Exception {
    var previous = KlabIDEController.instance();
    var user = mock(UserScope.class);
    var runtime = mock(RuntimeService.class);
    when(runtime.isLocal()).thenReturn(true);
    when(user.getServices(RuntimeService.class)).thenReturn(List.of(runtime));
    var agent = mock(Agent.class);
    when(agent.getNotifications()).thenReturn(List.of());
    when(runtime.createAgent(any(), anyString(), any(), eq(user))).thenReturn(agent);
    try {
      fx(() -> {
        new KlabIDEController() { @Override public UserScope user() { return user; } };
        var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
        for (var type : List.of(KActorsBehavior.Type.UNITTEST, KActorsBehavior.Type.BEHAVIOR,
            KActorsBehavior.Type.SCRIPT)) {
          var original = document(type, "original", 100);
          var checkout = mirrors.checkout("resources", "project", "behavior", type, "original", 100);
          var editor = new BehaviorEditor(checkout.file(), original, null, null, null, null, null,
              mirrors, checkout.origin());
          var toolbar = BehaviorEditor.class.getDeclaredMethod("createEditorToolbar");
          toolbar.setAccessible(true); toolbar.invoke(editor);
          var sourceEditor = mock(MonacoEditorView.class);
          when(sourceEditor.getText()).thenReturn("original");
          var field = BehaviorEditor.class.getDeclaredField("monacoEditor");
          field.setAccessible(true); field.set(editor, sourceEditor);
          var updated = document(type, "updated", 200);
          mirrors.synchronize("resources", "project", "behavior", type, "updated", 200);
          editor.refreshManagedBehavior(updated);
          verify(runtime).createAgent(same(updated), anyString(),
              argThat(options -> options.contains(RuntimeAgent.CompilationOptions.DO_NOT_COMPILE_JAVA)), eq(user));
          var compiled = BehaviorEditor.class.getDeclaredField("compilationSuccessful");
          compiled.setAccessible(true);
          assertTrue(compiled.getBoolean(editor));
          // Duplicate notifications must not disable Run by clearing successful compilation.
          editor.refreshManagedBehavior(updated);
          assertTrue(compiled.getBoolean(editor));
          // A queued older notification must not replace the compiled snapshot.
          editor.refreshManagedBehavior(original);
          verify(runtime, times(1)).createAgent(same(updated), anyString(), any(), eq(user));
          verify(sourceEditor, never()).setText("original");
        }
        return null;
      });
      fx(() -> null);
    } finally {
      fx(() -> {
        var singleton = KlabIDEController.class.getDeclaredField("_this");
        singleton.setAccessible(true); singleton.set(null, previous);
        return null;
      });
    }
  }

  private static KActorsBehaviorImpl document(KActorsBehavior.Type type, String source, long timestamp) {
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("behavior"); behavior.setProjectName("project"); behavior.setBehaviorType(type);
    behavior.setSourceCode(source); behavior.setLastUpdateTimestamp(timestamp);
    return behavior;
  }

  private static <T> T fx(Callable<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.call()); }
      catch (Throwable failure) { result.completeExceptionally(failure); } });
    return result.get(15, TimeUnit.SECONDS);
  }
}

package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ResourcesNavigatorController;
import org.integratedmodelling.klab.ide.components.WorkspaceView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ViewSelectionTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void reselectingTheAttachedWorkspaceViewDoesNotDetachItsScene() throws Exception {
    var previous = KlabIDEController.instance();
    var transitions = new AtomicInteger();
    try {
      var controller = fx(() -> {
        var navigation = mock(ResourcesNavigatorController.class);
        var result = new KlabIDEController() {
          @Override public <T extends ViewController<?>> T viewController(Class<T> type) {
            return type.cast(navigation);
          }
        };
        result.mainArea = new StackPane();
        new Scene(result.mainArea);
        var workspace = new WorkspaceView();
        workspace.sceneProperty().addListener((property, before, after) -> transitions.incrementAndGet());
        var field = KlabIDEController.class.getDeclaredField("workspaceView");
        field.setAccessible(true); field.set(result, workspace);
        result.selectView(KlabIDEController.View.WORKSPACES);
        return result;
      });
      fx(() -> {
        assertEquals(1, transitions.get(), "First selection must attach the view");
        controller.selectView(KlabIDEController.View.WORKSPACES);
        controller.selectView(KlabIDEController.View.WORKSPACES);
        return null;
      });
      fx(() -> {
        assertEquals(1, transitions.get(), "Same-view selection must keep the scene attached");
        controller.mainArea.getChildren().clear();
        controller.selectView(KlabIDEController.View.WORKSPACES);
        return null;
      });
      fx(() -> {
        assertEquals(3, transitions.get(), "A selected but detached view must still be reattached");
        return null;
      });
    } finally {
      fx(() -> {
        var field = KlabIDEController.class.getDeclaredField("_this");
        field.setAccessible(true); field.set(null, previous); return null;
      });
    }
  }

  private static <T> T fx(Callable<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> {
      try { result.complete(action.call()); }
      catch (Throwable failure) { result.completeExceptionally(failure); }
    });
    return result.get(10, TimeUnit.SECONDS);
  }
}

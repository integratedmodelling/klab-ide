package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationBrowserOptionsTest {
  @TempDir Path temporaryDirectory;
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void editorMenuAndRunningChoiceShareTheSelectedBrowser() throws Exception {
    fx(() -> {
      var options = new ApplicationBrowserOptions();
      var menu = options.createMenu(() -> null); var choice = options.createChoice(() -> null);
      assertNotNull(menu.getGraphic()); assertEquals("Application browser", menu.getAccessibleText());
      assertEquals(ApplicationBrowserOptions.INTERNAL, choice.getValue());
      ((RadioMenuItem) menu.getItems().get(1)).fire();
      assertEquals("Default browser", options.selected().name());
      assertEquals(options.selected(), choice.getValue());
      choice.setValue(ApplicationBrowserOptions.INTERNAL);
      assertTrue(((RadioMenuItem) menu.getItems().getFirst()).isSelected());
      ((Runnable) choice.getProperties().get("dispose-browser-options")).run();
      return null;
    });
  }

  @Test void browserIconMenuIsBesideEditorDebugAndRunBeforeAnApplicationIsCreated() throws Exception {
    var previous = KlabIDEController.instance();
    fx(() -> {
      var user = (UserScope) Proxy.newProxyInstance(UserScope.class.getClassLoader(), new Class<?>[]{UserScope.class},
          (self, method, args) -> method.getName().equals("getServices") ? List.of() : null);
      new KlabIDEController() { @Override public UserScope user() { return user; } };
      var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
      var checkout = mirrors.checkout("service", "project", "app", KActorsBehavior.Type.APP, "web app app");
      var behavior = new KActorsBehaviorImpl(); behavior.setUrn("app"); behavior.setBehaviorType(KActorsBehavior.Type.APP);
      var editor = new BehaviorEditor(checkout.file(), behavior, null, null, null, null, null, mirrors, checkout.origin());
      try {
        var create = BehaviorEditor.class.getDeclaredMethod("createEditorToolbar"); create.setAccessible(true);
        var toolbar = (HBox) create.invoke(editor);
        var menu = toolbar.getChildren().stream().filter(MenuButton.class::isInstance).map(MenuButton.class::cast).findFirst().orElseThrow();
        int index = toolbar.getChildren().indexOf(menu);
        assertTrue(menu.isVisible()); assertNotNull(menu.getGraphic());
        var debug = BehaviorEditor.class.getDeclaredField("debug"); debug.setAccessible(true);
        var run = BehaviorEditor.class.getDeclaredField("run"); run.setAccessible(true);
        assertSame(debug.get(editor), toolbar.getChildren().get(index + 1));
        assertSame(run.get(editor), toolbar.getChildren().get(index + 2));
      } finally {
        editor.close();
        var field = KlabIDEController.class.getDeclaredField("_this"); field.setAccessible(true); field.set(null, previous);
      }
      return null;
    });
  }

  @Test void ideCannotLaunchThroughARemoteRuntimeEvenIfMarkedLocal() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> ApplicationWebView.requireLocalRuntime(runtime(false, "http://127.0.0.1:8094/runtime")));
    assertThrows(IllegalArgumentException.class, () -> ApplicationWebView.requireLocalRuntime(runtime(true, "https://runtime.example.invalid/runtime")));
    assertDoesNotThrow(() -> ApplicationWebView.requireLocalRuntime(runtime(true, "http://127.0.0.1:8094/runtime")));
  }

  private static RuntimeService runtime(boolean local, String url) throws Exception {
    var location = URI.create(url).toURL();
    return (RuntimeService) Proxy.newProxyInstance(RuntimeService.class.getClassLoader(), new Class<?>[]{RuntimeService.class},
        (self, method, args) -> switch (method.getName()) { case "isLocal" -> local; case "getUrl" -> location; default -> null; });
  }
  private static <T> T fx(Callable<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
    return result.get(15, TimeUnit.SECONDS);
  }
}

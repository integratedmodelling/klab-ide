package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceDashboardRecoveryTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void unavailableDashboardRecoversOnceWhenCachedStatusBecomesOperational() throws Exception {
    assertDashboardRecovery(false);
    assertDashboardRecovery(true);
  }

  private void assertDashboardRecovery(boolean initialFailure) throws Exception {
    var calls = new AtomicInteger();
    var status = new ServiceStatusImpl();
    var service = (KlabService) Proxy.newProxyInstance(KlabService.class.getClassLoader(),
        new Class<?>[]{KlabService.class}, (self, method, args) -> switch (method.getName()) {
          case "status" -> status;
          case "isLocal" -> false;
          case "capabilities" -> {
            calls.incrementAndGet();
            if (!status.isOperational() && initialFailure)
              throw new IllegalStateException("Service is starting");
            yield status.isOperational() ? capabilities() : null;
          }
          default -> null;
        });
    var previous = KlabIDEController.instance();
    try {
      var dashboard = fx(() -> {
        new KlabIDEController() { @Override public UserScope user() { return null; } };
        return new ServiceDashboard(service, "Test", true);
      });
      if (initialFailure) assertThrows(ExecutionException.class,
          () -> future(dashboard).get(5, TimeUnit.SECONDS));
      else future(dashboard).get(5, TimeUnit.SECONDS);
      fx(() -> null);
      assertEquals(1, calls.get());
      fx(() -> { status.setOperational(true); sample(dashboard, status); return null; });
      assertNotNull(future(dashboard).get(5, TimeUnit.SECONDS));
      fx(() -> { sample(dashboard, status); sample(dashboard, status); return null; });
      assertEquals(2, calls.get(), "Ready samples must not start repeated capabilities requests");
    } finally { restoreController(previous); }
  }

  @Test void supersededImportSuccessOrErrorCannotOverwriteCurrentResponse() throws Exception {
    var previous = KlabIDEController.instance();
    fx(() -> { new KlabIDEController() { @Override public UserScope user() { return null; } }; return null; });
    try {
    for (boolean failOld : new boolean[]{false, true}) {
      var service = (KlabService) Proxy.newProxyInstance(KlabService.class.getClassLoader(),
          new Class<?>[]{KlabService.class}, (self, method, args) ->
              method.getName().equals("capabilities") ? capabilities() : null);
      var dashboard = fx(() -> new ServiceDashboard(service, "Test", false));
      var old = new CompletableFuture<KlabService.ServiceCapabilities>();
      fx(() -> {
        var button = new ToggleButton(); button.setSelected(true);
        write(dashboard, "importButton", button);
        write(dashboard, "display", new StackPane());
        write(dashboard, "capabilitiesFuture", old);
        showImport(dashboard, button);
        var refresh = ServiceDashboard.class.getDeclaredMethod("loadCapabilities");
        refresh.setAccessible(true); refresh.invoke(dashboard);
        return null;
      });
      future(dashboard).get(5, TimeUnit.SECONDS);
      Object rendered = null;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!(rendered instanceof VBox) && System.nanoTime() < deadline) {
        rendered = fx(() -> ((StackPane) read(dashboard, "display")).getChildren().getFirst());
        if (!(rendered instanceof VBox)) Thread.sleep(10);
      }
      assertInstanceOf(VBox.class, rendered);
      var expected = rendered;
      if (failOld) old.completeExceptionally(new IllegalStateException("old failure"));
      else old.complete(null);
      fx(() -> {
        assertSame(expected, ((StackPane) read(dashboard, "display")).getChildren().getFirst());
        return null;
      });
    }
    } finally { restoreController(previous); }
  }

  private static KlabService.ServiceCapabilities capabilities() {
    var result = new AbstractServiceCapabilities() {
      @Override public KlabService.Type getType() { return KlabService.Type.RUNTIME; }
    };
    result.setImportSchemata(Map.of());
    return result;
  }

  @SuppressWarnings("unchecked") private static CompletableFuture<KlabService.ServiceCapabilities> future(ServiceDashboard dashboard) throws Exception {
    return fx(() -> (CompletableFuture<KlabService.ServiceCapabilities>) read(dashboard, "capabilitiesFuture"));
  }
  private static void sample(ServiceDashboard dashboard, ServiceStatusImpl status) throws Exception {
    var method = ServiceDashboard.class.getDeclaredMethod("updateCharts", ServiceDashboard.StatusSample.class);
    method.setAccessible(true); method.invoke(dashboard, ServiceDashboard.StatusSample.from(status));
  }
  private static void showImport(ServiceDashboard dashboard, ToggleButton button) throws Exception {
    var method = ServiceDashboard.class.getDeclaredMethod("showImportView", ToggleButton.class);
    method.setAccessible(true); method.invoke(dashboard, button);
  }
  private static Object read(Object object, String name) throws Exception {
    var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
  }
  private static void write(Object object, String name, Object value) throws Exception {
    var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
  private static void restoreController(KlabIDEController controller) throws Exception {
    fx(() -> { var field = KlabIDEController.class.getDeclaredField("_this"); field.setAccessible(true); field.set(null, controller); return null; });
  }
  private static <T> T fx(Callable<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
    return result.get(10, TimeUnit.SECONDS);
  }
}

package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.Notebook;
import org.integratedmodelling.klab.ide.components.generic.LogViewer;
import org.kordamp.ikonli.materialdesign.MaterialDesign;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceDashboardRecoveryTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void chartsRemainStyledAcrossNotebookReattachment() throws Exception {
    var previous = KlabIDEController.instance();
    var previousStylesheet = fx(Application::getUserAgentStylesheet);
    var root = fx(StackPane::new);
    var status = new ServiceStatusImpl();
    var service = (KlabService) Proxy.newProxyInstance(KlabService.class.getClassLoader(),
        new Class<?>[]{KlabService.class}, (self, method, args) -> switch (method.getName()) {
          case "status" -> status;
          case "isLocal" -> false;
          case "hashCode" -> System.identityHashCode(self);
          case "equals" -> self == args[0];
          default -> null;
        });
    try {
      fx(() -> {
        new KlabIDEController() { @Override public UserScope user() { return null; } };
        Application.setUserAgentStylesheet(Theme.LIGHT_DEFAULT.getStylesheet());
        var dashboard = new ServiceDashboard(service, "Test", true);
        var notebook = new Notebook();
        root.getChildren().add(notebook);
        new Scene(root, 1200, 800);
        root.getScene().getStylesheets().add(Theme.class.getResource("custom.css").toExternalForm());
        notebook.addCard("service", MaterialDesign.MDI_SERVER, "Service", dashboard);
        var detachments = new AtomicInteger();
        dashboard.sceneProperty().addListener((property, before, after) -> {
          if (after == null) detachments.incrementAndGet();
        });
        for (int i = 0; i < 8; i++) {
          root.applyCss(); root.layout();
          for (int j = 0; j < 90; j++) sample(dashboard, status);
          // Exercise the reported sequence: render logs, detach them, then restore charts.
          var display = (StackPane) read(dashboard, "display");
          var overview = display.getChildren().getFirst();
          var logs = new LogViewer(
              java.nio.file.Path.of("target", "css-regression-missing.log"));
          logs.shutdown();
          for (String level : new String[]{"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"}) {
            logs.getEntries().add(new LogViewer.LogEntry(
                "", level, "", "", "test", "Message\nDetails"));
          }
          var showView = ServiceDashboard.class.getDeclaredMethod("showView", javafx.scene.Node.class);
          showView.setAccessible(true);
          showView.invoke(dashboard, logs);
          assertSame(root.getScene(), overview.getScene(), "Live charts must retain their CSS context");
          assertFalse(overview.isVisible());
          assertFalse(overview.isManaged());
          root.applyCss(); root.layout();
          sample(dashboard, status);
          showView.invoke(dashboard, overview);
          assertTrue(overview.isVisible());
          assertTrue(overview.isManaged());
          assertNull(logs.getScene(), "Auxiliary views must detach so log polling can stop");
          notebook.addCard("other" + i, MaterialDesign.MDI_SERVER, "Other", new Label("Other"));
          assertEquals(0, detachments.get(), "Adding another card must retain the dashboard's scene");
          notebook.pinCard("service");
          detachments.set(0);
          notebook.addCard("temporary", MaterialDesign.MDI_SERVER, "Temporary", new Label("Temporary"));
          notebook.removeCard("temporary");
          assertEquals(0, detachments.get(), "Refreshing other cards must retain the pinned dashboard");
          notebook.unpinCard("service");
          detachments.set(0);
          notebook.removeCard("other" + i);
          assertEquals(0, detachments.get(), "Removing another card must retain the dashboard's scene");
          root.getChildren().clear();
          root.getChildren().add(notebook);
          detachments.set(0);
          root.applyCss(); root.layout();
          var chart = (LineChart<?, ?>) dashboard.lookup(".chart");
          assertNotNull(chart);
          var line = (Path) chart.lookup(".chart-series-line");
          assertNotNull(line);
          assertNotEquals(Color.BLACK, line.getStroke(), "Chart lost its theme after reattachment");
          assertNotNull(((Label) chart.lookup(".chart-title")).getSkin());
          var card = dashboard.getParent();
          while (card != null && !card.getClass().getSimpleName().equals("CardView")) {
            card = card.getParent();
          }
          assertNotNull(card);
          var border = ((javafx.scene.layout.Region) card).getBorder();
          assertNotNull(border, "Notebook card must not blend into the background after logs");
          assertFalse(border.getStrokes().isEmpty());
          assertTrue(border.getStrokes().getFirst().getWidths().getTop() > 0);
          assertNotEquals(Color.TRANSPARENT, border.getStrokes().getFirst().getTopStroke());
        }
        return null;
      });
    } finally {
      fx(() -> {
        root.getChildren().clear();
        Application.setUserAgentStylesheet(previousStylesheet);
        return null;
      });
      restoreController(previous);
    }
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
    return result.get(60, TimeUnit.SECONDS);
  }
}

package org.integratedmodelling.klab.ide.components.generic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.PickResult;
import javafx.event.EventType;
import javafx.geometry.Side;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DockableTabPaneTest {
  @BeforeAll
  static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try {
      Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
    } catch (IllegalStateException alreadyStarted) {
      Platform.runLater(() -> ready.complete(null));
    }
    ready.get(15, TimeUnit.SECONDS);
  }

  private void onFx(Runnable check) throws Exception {
    var result = new CompletableFuture<Void>();
    Platform.runLater(() -> {
      try { check.run(); result.complete(null); }
      catch (Throwable error) { result.completeExceptionally(error); }
    });
    result.get(15, TimeUnit.SECONDS);
  }

  private static void mouse(Node header, EventType<MouseEvent> type, double x, double y) {
    Event.fireEvent(header, new MouseEvent(type, 5, 5, x, y, MouseButton.PRIMARY, 1,
        false, false, false, false, type != MouseEvent.MOUSE_RELEASED, false, false,
        false, false, false, new PickResult(header, 5, 5)));
  }

  @Test
  void nestedHeaderDragStagesUntilReleaseAndReturnsToEmptyBottomStrip() throws Exception {
    onFx(() -> {
      var outer = new DockableTabPane();
      var inner = new DockableTabPane();
      inner.setSide(Side.BOTTOM);
      var tab = new Tab("inner", new TextArea("draft"));
      inner.getTabs().add(tab);
      var parent = new Tab("outer", inner);
      outer.getTabs().add(parent);
      var owner = new Stage();
      owner.setScene(new Scene(outer, 600, 400));
      owner.show();
      try {
        outer.applyCss(); outer.layout(); inner.applyCss(); inner.layout();
        var header = inner.lookup(".tab");
        assertNotNull(header);
        var bounds = header.localToScreen(header.getBoundsInLocal());
        double x = bounds.getMinX() + 10, y = bounds.getMinY() + 10;
        double outsideX = owner.getX() + owner.getWidth() + 50;
        mouse(header, MouseEvent.MOUSE_PRESSED, x, y);
        mouse(header, MouseEvent.MOUSE_DRAGGED, outsideX, y);
        assertTrue(inner.getTabs().contains(tab), "preview must not move the tab");
        mouse(header, MouseEvent.MOUSE_RELEASED, outsideX, y);
        assertFalse(inner.getTabs().contains(tab));
        assertTrue(outer.getTabs().contains(parent), "outer must ignore inner gestures");
        var floatingPane = tab.getTabPane();
        floatingPane.applyCss(); floatingPane.layout();
        var floatingHeader = floatingPane.lookup(".tab");
        var floatingBounds = floatingHeader.localToScreen(floatingHeader.getBoundsInLocal());
        var home = inner.localToScreen(inner.getBoundsInLocal());
        double homeX = home.getMinX() + 20, homeY = home.getMaxY() - 10;
        var floatingWindow = floatingPane.getScene().getWindow();
        double windowX = floatingWindow.getX(), windowY = floatingWindow.getY();
        mouse(floatingHeader, MouseEvent.MOUSE_PRESSED,
            floatingBounds.getMinX() + 10, floatingBounds.getMinY() + 10);
        mouse(floatingHeader, MouseEvent.MOUSE_DRAGGED, outsideX + 100, y);
        assertEquals(windowX, floatingWindow.getX());
        assertEquals(windowY, floatingWindow.getY());
        assertTrue(javafx.stage.Window.getWindows().stream()
            .anyMatch(w -> w instanceof javafx.stage.Popup && w.isShowing()));
        mouse(floatingHeader, MouseEvent.MOUSE_RELEASED, outsideX + 100, y);
        assertSame(floatingPane, tab.getTabPane(), "release outside home leaves the tab floating");
        assertEquals(windowX, floatingWindow.getX());
        assertEquals(windowY, floatingWindow.getY());
        mouse(floatingHeader, MouseEvent.MOUSE_PRESSED,
            floatingBounds.getMinX() + 10, floatingBounds.getMinY() + 10);
        mouse(floatingHeader, MouseEvent.MOUSE_DRAGGED, homeX, homeY);
        assertFalse(inner.getTabs().contains(tab));
        assertEquals(windowX, floatingWindow.getX());
        assertEquals(windowY, floatingWindow.getY());
        mouse(floatingHeader, MouseEvent.MOUSE_RELEASED, homeX, homeY);
        assertSame(inner, tab.getTabPane());
        assertSame(tab, inner.selectedTab());
      } finally { inner.dockAll(); outer.dockAll(); owner.hide(); }
    });
  }

  @Test
  void roundTripPreservesIdentityContentOrderAndLogicalMembership() throws Exception {
    onFx(() -> {
      var pane = new DockableTabPane();
      var owner = new Stage();
      owner.setScene(new Scene(pane, 600, 400));
      owner.show();
      try {
        var editor = new TextArea("unsaved text");
        var first = new Tab("first", editor);
        var second = new Tab("second");
        var removed = new AtomicInteger();
        var closed = new AtomicInteger();
        pane.setOnTabRemoved(t -> removed.incrementAndGet());
        first.setOnClosed(e -> closed.incrementAndGet());
        pane.getTabs().addAll(first, second);
        pane.detach(first, 100, 100);
        assertEquals(2, pane.allTabs().size());
        assertFalse(pane.getTabs().contains(first));
        assertSame(editor, first.getContent());
        first.setText("updated title");
        assertEquals("updated title", ((Stage) editor.getScene().getWindow()).getTitle());
        pane.dock(first);
        assertSame(first, pane.getTabs().getFirst());
        assertSame(first, pane.selectedTab());
        assertEquals("unsaved text", editor.getText());
        assertEquals(0, removed.get());
        assertEquals(0, closed.get());
        pane.detach(first, 100, 100);
        pane.detach(second, 200, 200);
        pane.dock(first);
        pane.dock(second);
        assertEquals(java.util.List.of(first, second), pane.getTabs());
      } finally { pane.dockAll(); owner.hide(); }
    });
  }

  @Test
  void windowCloseHonorsVetoThenRunsRemovalAndCloseOnce() throws Exception {
    onFx(() -> {
      var pane = new DockableTabPane();
      var owner = new Stage();
      owner.setScene(new Scene(pane, 600, 400));
      owner.show();
      try {
        var tab = new Tab("editor", new TextArea());
        var removed = new AtomicInteger();
        var closed = new AtomicInteger();
        pane.setOnTabRemoved(t -> removed.incrementAndGet());
        tab.setOnClosed(e -> closed.incrementAndGet());
        pane.getTabs().add(tab);
        pane.detach(tab, 100, 100);
        var stage = (Stage) tab.getContent().getScene().getWindow();
        tab.setOnCloseRequest(Event::consume);
        Event.fireEvent(stage, new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
        assertTrue(stage.isShowing());
        assertEquals(1, pane.allTabs().size());
        tab.setOnCloseRequest(null);
        Event.fireEvent(stage, new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
        assertFalse(stage.isShowing());
        assertTrue(pane.allTabs().isEmpty());
        assertEquals(1, removed.get());
        assertEquals(1, closed.get());
      } finally { pane.dockAll(); owner.hide(); }
    });
  }

  @Test
  void programmaticRemovalClosesFloatingWindowWithoutInventingClosedEvent() throws Exception {
    onFx(() -> {
      var pane = new DockableTabPane();
      var owner = new Stage();
      owner.setScene(new Scene(pane, 600, 400));
      owner.show();
      try {
        var tab = new Tab("editor", new TextArea());
        var removed = new AtomicInteger();
        pane.setOnTabRemoved(t -> removed.incrementAndGet());
        pane.getTabs().add(tab);
        pane.detach(tab, 100, 100);
        var stage = tab.getContent().getScene().getWindow();
        pane.removeTab(tab);
        assertFalse(stage.isShowing());
        assertTrue(pane.allTabs().isEmpty());
        assertEquals(1, removed.get());
      } finally { pane.dockAll(); owner.hide(); }
    });
  }
}

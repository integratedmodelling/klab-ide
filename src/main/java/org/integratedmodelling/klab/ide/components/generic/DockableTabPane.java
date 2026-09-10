package org.integratedmodelling.klab.ide.components.generic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.stage.Stage;

/**
 * Tabs retain their identity and content when moved to a floating window. FX thread only.
 * Drag a header outside the host window to detach on release; drag the floating tab header
 * onto the original strip to dock. Native OS title-bar movement does not initiate docking.
 * Use allTabs(), select() and removeTab() for operations that include floating tabs.
 */
public class DockableTabPane extends TabPane {
  private record Floating(Stage stage, TabPane pane) {}
  private final Map<Tab, Floating> floating = new LinkedHashMap<>();
  private final ObservableList<Tab> logicalTabs = FXCollections.observableArrayList();
  private boolean transferring;
  private Consumer<Tab> closed = tab -> {};
  private Consumer<Tab> selected = tab -> {};

  public DockableTabPane() {
    installGesture(this, null);
    getTabs().addListener((ListChangeListener<Tab>) change -> {
      while (change.next()) {
        if (!transferring) {
          logicalTabs.addAll(change.getAddedSubList());
          logicalTabs.removeAll(change.getRemoved());
          change.getRemoved().forEach(closed);
        }
      }
    });
  }

  public void setOnTabRemoved(Consumer<Tab> handler) { closed = handler; }
  public void setOnFloatingSelected(Consumer<Tab> handler) { selected = handler; }

  public ObservableList<Tab> allTabs() {
    return FXCollections.unmodifiableObservableList(logicalTabs);
  }

  public Tab selectedTab() {
    for (var entry : floating.entrySet()) {
      if (entry.getValue().stage().isFocused()) return entry.getKey();
    }
    return getSelectionModel().getSelectedItem();
  }

  public void select(Tab tab) {
    var window = floating.get(tab);
    if (window == null) getSelectionModel().select(tab);
    else {
      window.stage().setIconified(false);
      window.stage().show();
      window.stage().toFront();
      window.stage().requestFocus();
      selected.accept(tab);
    }
  }

  public boolean isSelected(Tab tab) {
    var window = floating.get(tab);
    return window == null ? getSelectionModel().getSelectedItem() == tab : window.stage().isFocused();
  }

  /** Programmatic removal has the same semantics as removing a docked tab from getTabs(). */
  public void removeTab(Tab tab) {
    var window = floating.remove(tab);
    if (window == null) getTabs().remove(tab);
    else {
      window.pane().getTabs().remove(tab);
      window.stage().titleProperty().unbind();
      window.stage().hide();
      logicalTabs.remove(tab);
      closed.accept(tab);
    }
  }

  /** Restore children before their owning page disposes its editor resources. */
  public void dockAll() {
    for (var tab : List.copyOf(floating.keySet())) dock(tab);
  }

  void detach(Tab tab, double x, double y) {
    if (!getTabs().contains(tab) || tab.isDisable()) return;
    var scene = getScene();
    if (scene == null || scene.getWindow() == null) return;
    var pane = new TabPane();
    pane.getStyleClass().setAll(getStyleClass());
    pane.setSide(Side.TOP);
    var stage = new Stage();
    stage.initOwner(scene.getWindow());
    stage.titleProperty().bind(tab.textProperty());
    var floatingScene = new Scene(pane, Math.max(480, getWidth()), Math.max(320, getHeight()));
    floatingScene.getStylesheets().setAll(scene.getStylesheets());
    stage.setScene(floatingScene);
    floating.put(tab, new Floating(stage, pane));
    transferring = true;
    try {
      getTabs().remove(tab);
      pane.getTabs().add(tab);
    } finally { transferring = false; }
    installGesture(pane, tab);
    pane.getTabs().addListener((ListChangeListener<Tab>) c -> {
      if (!pane.getTabs().contains(tab) && floating.containsKey(tab)) removeTab(tab);
    });
    stage.setOnCloseRequest(event -> {
      event.consume();
      if (!tab.isClosable()) { dock(tab); return; }
      var request = new Event(tab, tab, Tab.TAB_CLOSE_REQUEST_EVENT);
      Event.fireEvent(tab, request);
      if (!request.isConsumed()) {
        removeTab(tab);
        Event.fireEvent(tab, new Event(Tab.CLOSED_EVENT));
      }
    });
    stage.focusedProperty().addListener((obs, old, focus) -> {
      if (focus) selected.accept(tab);
    });
    stage.setOnHidden(event -> {
      if (floating.containsKey(tab)) dock(tab);
    });
    stage.setX(x - 80);
    stage.setY(y - 16);
    stage.show();
  }

  void dock(Tab tab) {
    var window = floating.remove(tab);
    if (window == null) return;
    transferring = true;
    try {
      window.pane().getTabs().remove(tab);
      int index = 0;
      for (Tab sibling : logicalTabs) {
        if (sibling == tab) break;
        if (getTabs().contains(sibling)) index++;
      }
      getTabs().add(index, tab);
    } finally { transferring = false; }
    window.stage().titleProperty().unbind();
    window.stage().hide();
    select(tab);
  }

  private boolean overHome(double x, double y) {
    if (getScene() == null || !getScene().getWindow().isShowing()) return false;
    for (Node n = this; n != null; n = n.getParent()) if (!n.isVisible()) return false;
    var bounds = localToScreen(getBoundsInLocal());
    if (bounds == null || !bounds.contains(x, y)) return false;
    Node header = lookup(".tab-header-area");
    if (!getTabs().isEmpty() && header != null && header.isVisible()) {
      var headerBounds = header.localToScreen(header.getBoundsInLocal());
      return headerBounds != null && headerBounds.contains(x, y);
    }
    // Keep a drop strip available even after the last tab has been detached.
    return switch (getSide()) {
      case TOP -> y <= bounds.getMinY() + 40;
      case BOTTOM -> y >= bounds.getMaxY() - 40;
      case LEFT -> x <= bounds.getMinX() + 40;
      case RIGHT -> x >= bounds.getMaxX() - 40;
    };
  }

  private boolean outsideWindow(double x, double y) {
    var w = getScene().getWindow();
    return x < w.getX() || x > w.getX() + w.getWidth()
        || y < w.getY() || y > w.getY() + w.getHeight();
  }

  private void installGesture(TabPane pane, Tab detachedTab) {
    final Tab[] dragged = {null};
    final double[] press = new double[2];
    final boolean[] moving = {false};
    var preview = new Popup();
    preview.setAutoFix(false);
    var label = new Label();
    label.setStyle("-fx-background-color: #285a85; -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 4;");
    label.setMouseTransparent(true);
    preview.getContent().add(label);
    pane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (event.getButton() != MouseButton.PRIMARY) return;
      Node node = event.getTarget() instanceof Node n ? n : null;
      while (node != null && node != pane) {
        if (node instanceof TabPane || node.getStyleClass().contains("tab-close-button")) return;
        if (node.getStyleClass().contains("tab")) break;
        node = node.getParent();
      }
      if (node == null || node == pane) return;
      Node host = node.getParent();
      while (host != null && !(host instanceof TabPane)) host = host.getParent();
      if (host != pane) return; // Nested EditorPage headers belong to their own pane.
      // Skin header nodes are ordered like the tabs, including overflowed headers.
      var headers = node.getParent().getChildrenUnmodifiable();
      int index = headers.indexOf(node);
      if (index < 0 || index >= pane.getTabs().size()) return;
      var tab = pane.getTabs().get(index);
      if (tab.isDisable()) return;
      dragged[0] = tab;
      moving[0] = false;
      press[0] = event.getScreenX(); press[1] = event.getScreenY();
    });
    pane.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
      if (dragged[0] == null) return;
      double x = event.getScreenX(), y = event.getScreenY();
      if (Math.hypot(x - press[0], y - press[1]) < 8 && !moving[0]) return;
      moving[0] = true;
      // Tab gestures move only the preview; the native title bar still moves the window.
      if (detachedTab != null && !floating.containsKey(detachedTab)) return;
      boolean eligible = detachedTab == null ? outsideWindow(x, y) : overHome(x, y);
      if (eligible || detachedTab != null) {
        label.setText(detachedTab == null ? "Release to detach: " + dragged[0].getText()
            : eligible ? "Release to dock" : dragged[0].getText());
        if (!preview.isShowing()) preview.show(pane.getScene().getWindow(), x + 18, y + 18);
        preview.setX(x + 18); preview.setY(y + 18);
      } else preview.hide();
      event.consume();
    });
    pane.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
      var tab = dragged[0];
      dragged[0] = null;
      preview.hide();
      if (tab == null || !moving[0]) return;
      if (detachedTab == null && outsideWindow(event.getScreenX(), event.getScreenY()))
        detach(tab, event.getScreenX(), event.getScreenY());
      else if (detachedTab != null && overHome(event.getScreenX(), event.getScreenY())) dock(tab);
      event.consume();
    });
  }
}

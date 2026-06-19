package org.integratedmodelling.klab.ide.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

public class InspectorView extends BorderPane {

  private static final double INSPECTOR_HEIGHT = 300;
  private static final int CHIP_LABEL_LENGTH = 34;

  public Object currentObject = null;

  private final List<InspectorItem> stack = new ArrayList<>();
  private final StackPane contentArea = new StackPane();
  private final HBox breadcrumbStrip = new HBox(3);
  private ScrollPane breadcrumbScroll;
  private final Button backButton = iconButton(CarbonIcons.PREVIOUS_OUTLINE, "Back");
  private final Button forwardButton = iconButton(CarbonIcons.NEXT_OUTLINE, "Forward");
  private final Button closeAllButton = iconButton(Material2AL.CLOSE, "Close all inspector components");
  private final Button dockButton = iconButton(Material2MZ.OPEN_IN_NEW, "Undock inspector");
  private int currentIndex = -1;
  private boolean docked = true;
  private Runnable undockAction = () -> {};
  private Runnable dockAction = () -> {};

  public InspectorView() {
    super();
    getStyleClass().add("inspector-view");
    HBox.setHgrow(this, Priority.ALWAYS);
    setDocked(true);

    backButton.setOnAction(event -> navigate(-1));
    forwardButton.setOnAction(event -> navigate(1));
    closeAllButton.setOnAction(event -> clear());
    dockButton.setOnAction(
        event -> {
          if (docked) {
            undockAction.run();
          } else {
            dockAction.run();
          }
        });

    contentArea.getStyleClass().add("inspector-content");
    var clip = new Rectangle();
    clip.widthProperty().bind(contentArea.widthProperty());
    clip.heightProperty().bind(contentArea.heightProperty());
    contentArea.setClip(clip);

    setTop(createToolbar());
    setCenter(contentArea);
    showEmptyState();
    updateToolbar();
  }

  public Object getCurrentObject() {
    return currentObject;
  }

  public void setDockingActions(Runnable undockAction, Runnable dockAction) {
    this.undockAction = undockAction == null ? () -> {} : undockAction;
    this.dockAction = dockAction == null ? () -> {} : dockAction;
  }

  public void setDocked(boolean docked) {
    this.docked = docked;
    if (docked) {
      setMinSize(0, INSPECTOR_HEIGHT);
      setPrefHeight(INSPECTOR_HEIGHT);
      setMaxSize(Double.MAX_VALUE, INSPECTOR_HEIGHT);
    } else {
      setMinSize(320, 220);
      setPrefSize(620, 360);
      setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }
    updateDockButton();
  }

  public boolean isDocked() {
    return docked;
  }

  public void inspect(Object value) {
    inspect(value, false);
  }

  public void inspect(Object value, boolean resetStack) {
    if (value == null) {
      clear();
      return;
    }
    push(InspectorItem.of(value), resetStack);
  }

  public void inspect(String label, Object value) {
    inspect(label, value, false);
  }

  public void inspect(String label, Object value, boolean resetStack) {
    if (value == null) {
      clear();
      return;
    }
    push(InspectorItem.of(label, value), resetStack);
  }

  public void inspectLazy(String label, Supplier<?> loader) {
    inspectLazy(label, loader, false);
  }

  public void inspectLazy(String label, Supplier<?> loader, boolean resetStack) {
    push(InspectorItem.lazy(label, loader), resetStack);
  }

  public void resetAndInspect(Object value) {
    inspect(value, true);
  }

  public void resetAndInspect(String label, Object value) {
    inspect(label, value, true);
  }

  public void resetAndInspectLazy(String label, Supplier<?> loader) {
    inspectLazy(label, loader, true);
  }

  public void setStack(List<InspectorItem> items, int selectedIndex) {
    runOnFx(
        () -> {
          stack.clear();
          if (items != null) {
            stack.addAll(items.stream().filter(Objects::nonNull).toList());
          }
          currentIndex =
              stack.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, stack.size() - 1));
          renderCurrent();
        });
  }

  public void clear() {
    runOnFx(
        () -> {
          stack.clear();
          currentIndex = -1;
          currentObject = null;
          showEmptyState();
          updateToolbar();
        });
  }

  private void push(InspectorItem item) {
    push(item, false);
  }

  private void push(InspectorItem item, boolean resetStack) {
    runOnFx(
        () -> {
          if (resetStack) {
            stack.clear();
            currentIndex = -1;
          }
          if (currentIndex >= 0 && stack.get(currentIndex).matches(item)) {
            renderCurrent();
            return;
          }
          while (stack.size() > currentIndex + 1) {
            stack.remove(stack.size() - 1);
          }
          stack.add(item);
          currentIndex = stack.size() - 1;
          renderCurrent();
        });
  }

  private Node createToolbar() {
    breadcrumbStrip.getStyleClass().add("inspector-breadcrumb-strip");
    breadcrumbStrip.setAlignment(Pos.CENTER_LEFT);

    breadcrumbScroll = new ScrollPane(breadcrumbStrip);
    breadcrumbScroll.getStyleClass().add("inspector-breadcrumbs");
    breadcrumbScroll.setFitToHeight(true);
    breadcrumbScroll.setPannable(true);
    breadcrumbScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    breadcrumbScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    breadcrumbScroll.setMinHeight(22);
    breadcrumbScroll.setPrefHeight(22);
    breadcrumbScroll.setMaxHeight(22);
    breadcrumbScroll
        .viewportBoundsProperty()
        .addListener((observable, oldValue, newValue) -> scrollCurrentBreadcrumbIntoView());
    breadcrumbStrip
        .widthProperty()
        .addListener((observable, oldValue, newValue) -> scrollCurrentBreadcrumbIntoView());
    HBox.setHgrow(breadcrumbScroll, Priority.ALWAYS);

    HBox toolbar =
        new HBox(4, backButton, forwardButton, breadcrumbScroll, closeAllButton, dockButton);
    toolbar.getStyleClass().add("inspector-toolbar");
    toolbar.setAlignment(Pos.CENTER_LEFT);
    return toolbar;
  }

  private static Button iconButton(Ikon icon, String tooltip) {
    Button button =
        new Button(null, new IconLabel(icon, 12, Theme.CURRENT_THEME.getDefaultTextColor()));
    button.getStyleClass().add("inspector-tool-button");
    button.setTooltip(tooltip(tooltip));
    button.setFocusTraversable(false);
    button.setCursor(Cursor.HAND);
    button.setMinSize(18, 18);
    button.setPrefSize(18, 18);
    button.setMaxSize(18, 18);
    return button;
  }

  private static Tooltip tooltip(String text) {
    Tooltip tooltip = new Tooltip(text);
    tooltip.setShowDelay(Duration.millis(200));
    return tooltip;
  }

  private void navigate(int delta) {
    if (stack.isEmpty()) {
      return;
    }
    int next = Math.max(0, Math.min(currentIndex + delta, stack.size() - 1));
    if (next != currentIndex) {
      currentIndex = next;
      renderCurrent();
    }
  }

  private void navigateTo(int index) {
    if (index >= 0 && index < stack.size() && index != currentIndex) {
      currentIndex = index;
      renderCurrent();
    }
  }

  private void removeAt(int index) {
    if (index < 0 || index >= stack.size()) {
      return;
    }

    boolean removingCurrent = index == currentIndex;
    stack.remove(index);
    if (stack.isEmpty()) {
      currentIndex = -1;
      currentObject = null;
      showEmptyState();
      updateToolbar();
      return;
    }

    if (index < currentIndex) {
      currentIndex--;
    } else if (removingCurrent) {
      currentIndex = Math.min(index, stack.size() - 1);
    } else if (currentIndex >= stack.size()) {
      currentIndex = stack.size() - 1;
    }

    if (removingCurrent) {
      renderCurrent();
    } else {
      updateToolbar();
    }
  }

  private void renderCurrent() {
    if (currentIndex < 0 || currentIndex >= stack.size()) {
      currentObject = null;
      showEmptyState();
      updateToolbar();
      return;
    }

    InspectorItem item = stack.get(currentIndex);
    Node node = item.resolveNode();
    currentObject = item.value();
    if (node == null) {
      contentArea.getChildren().setAll(createEmptyState("No inspector card available"));
    } else {
      configureInspectableNode(node);
      contentArea.getChildren().setAll(node);
    }
    updateToolbar();
  }

  private void showEmptyState() {
    contentArea.getChildren().setAll(createEmptyState("No inspection target"));
  }

  private Node createEmptyState(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("inspector-empty");
    StackPane.setAlignment(label, Pos.CENTER);
    return label;
  }

  private void updateToolbar() {
    backButton.setDisable(currentIndex <= 0);
    forwardButton.setDisable(currentIndex < 0 || currentIndex >= stack.size() - 1);
    closeAllButton.setDisable(stack.isEmpty());
    updateDockButton();
    updateBreadcrumbs();
  }

  private void updateDockButton() {
    if (dockButton == null) {
      return;
    }
    Ikon icon = docked ? Material2MZ.OPEN_IN_NEW : Material2MZ.NAVIGATE_BEFORE;
    String text = docked ? "Undock inspector" : "Dock inspector";
    if (dockButton.getGraphic() instanceof IconLabel iconLabel) {
      iconLabel.set(icon, 11, Theme.CURRENT_THEME.getDefaultTextColor());
    }
    dockButton.setTooltip(tooltip(text));
  }

  private void updateBreadcrumbs() {
    breadcrumbStrip.getChildren().clear();
    if (stack.isEmpty()) {
      Label empty = new Label("Inspector");
      empty.getStyleClass().add("inspector-breadcrumb-empty");
      breadcrumbStrip.getChildren().add(empty);
      if (breadcrumbScroll != null) {
        breadcrumbScroll.setHvalue(0);
      }
      return;
    }

    for (int i = 0; i < stack.size(); i++) {
      InspectorItem item = stack.get(i);
      String label = item.label();
      HBox chip = new HBox(3);
      chip.getStyleClass().add("inspector-breadcrumb-chip");
      if (i == currentIndex) {
        chip.getStyleClass().add("inspector-breadcrumb-chip-current");
      }
      chip.setAlignment(Pos.CENTER_LEFT);
      Tooltip.install(chip, tooltip(label));
      chip.setCursor(Cursor.HAND);

      Label chipLabel = new Label(abbreviate(label, CHIP_LABEL_LENGTH));
      chipLabel.getStyleClass().add("inspector-breadcrumb-chip-label");
      if (i == currentIndex) {
        chipLabel.getStyleClass().add("inspector-breadcrumb-chip-label-current");
      }
      chipLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
      chipLabel.setMinWidth(0);
      HBox.setHgrow(chipLabel, Priority.ALWAYS);

      Button close =
          new Button(
              null, new IconLabel(Material2AL.CLOSE, 8, Theme.CURRENT_THEME.getDefaultTextColor()));
      close.getStyleClass().add("inspector-breadcrumb-close");
      close.setTooltip(tooltip("Remove from inspector stack"));
      close.setFocusTraversable(false);
      close.setCursor(Cursor.HAND);
      int index = i;
      chip.setOnMouseClicked(
          event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
              navigateTo(index);
            }
          });
      close.setOnMouseClicked(event -> event.consume());
      close.setOnAction(
          event -> {
            removeAt(index);
            event.consume();
          });

      chip.getChildren().addAll(chipLabel, close);
      breadcrumbStrip.getChildren().add(chip);
    }
    scrollCurrentBreadcrumbIntoView();
  }

  private void scrollCurrentBreadcrumbIntoView() {
    if (breadcrumbScroll == null || currentIndex < 0) {
      return;
    }

    Platform.runLater(
        () -> {
          if (breadcrumbScroll == null
              || currentIndex < 0
              || currentIndex >= breadcrumbStrip.getChildren().size()) {
            return;
          }

          double viewportWidth = breadcrumbScroll.getViewportBounds().getWidth();
          double contentWidth = breadcrumbStrip.getBoundsInLocal().getWidth();
          if (viewportWidth <= 0 || contentWidth <= viewportWidth) {
            breadcrumbScroll.setHvalue(0);
            return;
          }

          Node currentChip = breadcrumbStrip.getChildren().get(currentIndex);
          Bounds chipBounds = currentChip.getBoundsInParent();
          double scrollableWidth = contentWidth - viewportWidth;
          double currentLeft = breadcrumbScroll.getHvalue() * scrollableWidth;
          double currentRight = currentLeft + viewportWidth;
          double padding = 14;
          double targetLeft = currentLeft;

          if (chipBounds.getMinX() < currentLeft + padding) {
            targetLeft = Math.max(0, chipBounds.getMinX() - padding);
          } else if (chipBounds.getMaxX() > currentRight - padding) {
            targetLeft =
                Math.min(scrollableWidth, chipBounds.getMaxX() - viewportWidth + padding);
          }

          breadcrumbScroll.setHvalue(targetLeft / scrollableWidth);
        });
  }

  private void configureInspectableNode(Node node) {
    HBox.setHgrow(node, Priority.ALWAYS);
    VBox.setVgrow(node, Priority.ALWAYS);

    if (node instanceof Region region) {
      region.setMinSize(0, 0);
      region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }
  }

  private static String labelFor(Object value) {
    if (value == null) {
      return "Unknown";
    }
    try {
      return Theme.getLabel(value);
    } catch (RuntimeException e) {
      return value.toString();
    }
  }

  private static String abbreviate(String text, int maxLength) {
    if (text == null || text.isBlank()) {
      return "Untitled";
    }
    return Utils.Strings.abbreviate(text, maxLength);
  }

  private static void runOnFx(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }

  public static final class InspectorItem {
    private final String explicitLabel;
    private final Supplier<?> loader;
    private Object value;
    private Node node;
    private boolean resolved;

    private InspectorItem(String label, Object value, Supplier<?> loader) {
      this.explicitLabel = label;
      this.value = value;
      this.loader = loader;
      this.resolved = loader == null;
    }

    public static InspectorItem of(Object value) {
      return new InspectorItem(null, value, null);
    }

    public static InspectorItem of(String label, Object value) {
      return new InspectorItem(label, value, null);
    }

    public static InspectorItem lazy(String label, Supplier<?> loader) {
      return new InspectorItem(label, null, loader);
    }

    private String label() {
      if (explicitLabel != null && !explicitLabel.isBlank()) {
        return explicitLabel;
      }
      return labelFor(value());
    }

    private Object value() {
      resolve();
      return value;
    }

    private boolean matches(InspectorItem other) {
      if (other == null || loader != null || other.loader != null) {
        return false;
      }
      return Objects.equals(value, other.value);
    }

    private Node resolveNode() {
      Object resolvedValue = value();
      if (node != null) {
        return node;
      }
      if (resolvedValue == null) {
        return null;
      }
      if (resolvedValue instanceof Node resolvedNode) {
        node = resolvedNode;
        return node;
      }
      Object component;
      try {
        component = Theme.getDisplayObject(resolvedValue, Theme.Detail.CARD);
      } catch (RuntimeException e) {
        component = resolvedValue.toString();
      }
      if (component instanceof Node resolvedNode) {
        node = resolvedNode;
        return node;
      }
      if (component != null) {
        Label label = new Label(component.toString());
        label.getStyleClass().add("inspector-empty");
        node = new StackPane(label);
        return node;
      }
      return null;
    }

    private void resolve() {
      if (!resolved && loader != null) {
        value = loader.get();
        resolved = true;
      }
    }
  }
}

package org.integratedmodelling.klab.ide.components.generic;

import javafx.animation.TranslateTransition;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A horizontal or vertical carousel container.
 *
 * <p>When all child nodes fit within the container bounds they are arranged as a simple
 * left-aligned (horizontal) or top-aligned (vertical) sequence. When the children overflow the
 * container, thin navigation strips appear on the relevant sides; clicking a strip slides the
 * adjacent item into view with a smooth animation. Clicking any item selects it (shown as a thin
 * accent border via the {@code carousel-selected} CSS class); selection changes are reported
 * through a {@link Consumer} listener.
 *
 * <p>All items are expected to share the same cross-axis size (height for horizontal orientation,
 * width for vertical). The container's cross-axis dimension is considered fixed by the parent.
 *
 * <p>Internal structure:
 * <pre>
 *   CarouselBox (Region)
 *   ├── clipView  (Pane, sized to content area, owns the clip rectangle)
 *   │   └── contentPane (Pane, full content width/height, receives translateX/Y)
 *   │       └── [items...]
 *   ├── prevStrip (StackPane)
 *   └── nextStrip (StackPane)
 * </pre>
 * The clip is on {@code clipView} so that items' positions in {@code clipView}'s local coordinate
 * space correctly fall in or out of the clip as {@code contentPane} is translated.
 */
public class CarouselBox extends Region {

  // ── Constants ─────────────────────────────────────────────────────────────────

  /** Pixel width (horizontal) or height (vertical) reserved for each navigation strip. */
  private static final double NAV_STRIP_SIZE = 20.0;

  /** Duration of the slide animation in milliseconds. */
  private static final double ANIMATION_MS = 220.0;

  /** CSS style-class applied to the currently selected item. */
  private static final String SELECTED_STYLE_CLASS = "carousel-selected";

  // ── State ─────────────────────────────────────────────────────────────────────

  private final Orientation orientation;
  private final List<Node> items = new ArrayList<>();

  /**
   * Outer clip container, sized to the visible content area. The clip rectangle is applied here
   * so that items in {@code contentPane} are masked as they scroll in/out of view.
   */
  private final Pane clipView = new Pane();

  /**
   * Inner pane holding all items at their natural sequential positions. Scrolling is achieved by
   * translating this pane; the clip on {@code clipView} does the masking.
   */
  private final Pane contentPane = new Pane();

  private final StackPane prevStrip;
  private final StackPane nextStrip;

  /** Index of the first item that should be aligned to the leading edge of the content area. */
  private int firstVisibleIndex = 0;

  private Node selectedItem = null;
  private Consumer<Node> selectionListener = null;
  private boolean overflows = false;
  private boolean animating = false;

  // ── Construction ─────────────────────────────────────────────────────────────

  /**
   * Creates a {@code CarouselBox} with the given orientation.
   *
   * @param orientation {@link Orientation#HORIZONTAL} or {@link Orientation#VERTICAL}
   */
  public CarouselBox(Orientation orientation) {
    this.orientation = orientation;

    // clipView owns the clip; contentPane is an unclipped child that scrolls inside it
    clipView.setClip(new Rectangle());
    clipView.getStyleClass().add("carousel-clip-view");
    clipView.getChildren().add(contentPane);

    contentPane.getStyleClass().add("carousel-content");

    prevStrip = buildNavStrip(true);
    nextStrip = buildNavStrip(false);

    getChildren().addAll(clipView, prevStrip, nextStrip);

    prevStrip.setVisible(false);
    nextStrip.setVisible(false);

    getStyleClass().add("carousel-box");
  }

  // ── Public API ────────────────────────────────────────────────────────────────

  /**
   * Replaces all currently displayed items.
   *
   * @param newItems items to display (may be empty)
   */
  public void setItems(List<? extends Node> newItems) {
    clearAllItems();
    newItems.forEach(this::addItemInternal);
    requestLayout();
  }

  /**
   * Appends a single item to the end of the carousel.
   *
   * @param item node to add
   */
  public void addItem(Node item) {
    addItemInternal(item);
    requestLayout();
  }

  /**
   * Removes a single item from the carousel. If the item was selected the listener receives
   * {@code null}.
   *
   * @param item node to remove
   */
  public void removeItem(Node item) {
    int idx = items.indexOf(item);
    if (idx < 0) return;
    detachItem(item);
    items.remove(idx);
    contentPane.getChildren().remove(item);
    if (firstVisibleIndex >= items.size()) {
      firstVisibleIndex = Math.max(0, items.size() - 1);
    }
    requestLayout();
  }

  /** Removes all items from the carousel. */
  public void clear() {
    clearAllItems();
    requestLayout();
  }

  /**
   * Registers a listener that receives the newly selected node whenever the selection changes.
   * Receives {@code null} when the selection is cleared.
   *
   * @param listener consumer to notify; may be {@code null} to unregister
   */
  public void setSelectionListener(Consumer<Node> listener) {
    this.selectionListener = listener;
  }

  /**
   * Returns the currently selected item, or {@code null} if nothing is selected.
   *
   * @return selected node or null
   */
  public Node getSelectedItem() {
    return selectedItem;
  }

  /**
   * Programmatically selects an item that is already present in the carousel, firing the listener.
   *
   * @param item node to select
   */
  public void selectItem(Node item) {
    if (items.contains(item)) applySelection(item);
  }

  /**
   * Clears the current selection silently (listener is not notified).
   */
  public void clearSelection() {
    if (selectedItem != null) {
      selectedItem.getStyleClass().remove(SELECTED_STYLE_CLASS);
      selectedItem = null;
    }
  }

  // ── Internal helpers ──────────────────────────────────────────────────────────

  private StackPane buildNavStrip(boolean isPrev) {
    FontIcon icon;
    if (orientation == Orientation.HORIZONTAL) {
      icon = new FontIcon(isPrev ? Material2AL.CHEVRON_LEFT : Material2AL.CHEVRON_RIGHT);
    } else {
      icon = new FontIcon(isPrev ? Material2AL.EXPAND_LESS : Material2AL.EXPAND_MORE);
    }
    icon.setIconSize(12);
    icon.getStyleClass().add("carousel-nav-icon");

    StackPane strip = new StackPane(icon);
    strip.getStyleClass().add("carousel-nav-strip");
    strip.setAlignment(Pos.CENTER);
    strip.setCursor(Cursor.HAND);
    strip.setOpacity(0.50);

    strip.setOnMouseEntered(e -> strip.setOpacity(1.0));
    strip.setOnMouseExited(e -> strip.setOpacity(0.50));
    strip.setOnMouseClicked(e -> {
      if (!animating) {
        if (isPrev) navigatePrev();
        else navigateNext();
      }
    });

    return strip;
  }

  private void addItemInternal(Node item) {
    items.add(item);
    contentPane.getChildren().add(item);
    item.setCursor(Cursor.HAND);
    item.setOnMouseClicked(e -> {
      applySelection(item);
      e.consume();
    });
  }

  private void detachItem(Node item) {
    item.setOnMouseClicked(null);
    item.setCursor(Cursor.DEFAULT);
    item.getStyleClass().remove(SELECTED_STYLE_CLASS);
    if (item == selectedItem) {
      selectedItem = null;
      if (selectionListener != null) selectionListener.accept(null);
    }
  }

  private void clearAllItems() {
    items.forEach(item -> {
      item.setOnMouseClicked(null);
      item.setCursor(Cursor.DEFAULT);
      item.getStyleClass().remove(SELECTED_STYLE_CLASS);
    });
    items.clear();
    contentPane.getChildren().clear();
    selectedItem = null;
    firstVisibleIndex = 0;
    contentPane.setTranslateX(0);
    contentPane.setTranslateY(0);
  }

  private void applySelection(Node item) {
    if (item == selectedItem) return;
    if (selectedItem != null) selectedItem.getStyleClass().remove(SELECTED_STYLE_CLASS);
    selectedItem = item;
    if (!item.getStyleClass().contains(SELECTED_STYLE_CLASS)) {
      item.getStyleClass().add(SELECTED_STYLE_CLASS);
    }
    if (selectionListener != null) selectionListener.accept(item);
  }

  private void navigatePrev() {
    if (firstVisibleIndex <= 0) return;
    firstVisibleIndex--;
    animateToOffset(leadingEdge(firstVisibleIndex));
  }

  private void navigateNext() {
    // Guard: do not advance past the last item, and only navigate when there
    // are items genuinely hidden beyond the current view.
    if (firstVisibleIndex >= items.size() - 1 || !hasHiddenTrailingItems()) return;
    firstVisibleIndex++;
    animateToOffset(leadingEdge(firstVisibleIndex));
  }

  /**
   * Returns the layout-space leading coordinate ({@code layoutX} or {@code layoutY}) of the item
   * at {@code index} within {@code contentPane}. Assumes {@link #layoutChildren()} has already
   * run and positioned items.
   */
  private double leadingEdge(int index) {
    if (index <= 0 || items.isEmpty()) return 0.0;
    Node item = items.get(Math.min(index, items.size() - 1));
    return orientation == Orientation.HORIZONTAL ? item.getLayoutX() : item.getLayoutY();
  }

  private void animateToOffset(double targetOffset) {
    TranslateTransition tt =
        new TranslateTransition(Duration.millis(ANIMATION_MS), contentPane);
    if (orientation == Orientation.HORIZONTAL) {
      tt.setToX(-targetOffset);
    } else {
      tt.setToY(-targetOffset);
    }
    animating = true;
    tt.setOnFinished(e -> {
      animating = false;
      refreshNavStrips();
    });
    refreshNavStrips();
    tt.play();
  }

  /** Updates navigation strip visibility based on the current scroll position. */
  private void refreshNavStrips() {
    if (!overflows) {
      prevStrip.setVisible(false);
      nextStrip.setVisible(false);
      return;
    }
    prevStrip.setVisible(firstVisibleIndex > 0);
    nextStrip.setVisible(hasHiddenTrailingItems());
  }

  /**
   * Returns {@code true} when at least one item extends beyond the trailing edge of the content
   * area for the current {@code firstVisibleIndex}.
   *
   * <p>The content area size is always {@code containerSize - 2 * NAV_STRIP_SIZE} when overflowing
   * (both sides are always reserved so the layout stays stable during animation).
   */
  private boolean hasHiddenTrailingItems() {
    if (items.isEmpty()) return false;
    double contentAreaSize =
        (orientation == Orientation.HORIZONTAL ? getWidth() : getHeight())
            - 2 * NAV_STRIP_SIZE;
    double visibleStart = leadingEdge(firstVisibleIndex);
    double totalTrailing = totalContentSize() - visibleStart;
    return totalTrailing > contentAreaSize + 0.5;
  }

  private double totalContentSize() {
    if (items.isEmpty()) return 0.0;
    Node last = items.get(items.size() - 1);
    if (orientation == Orientation.HORIZONTAL) {
      return last.getLayoutX() + last.getBoundsInLocal().getWidth();
    } else {
      return last.getLayoutY() + last.getBoundsInLocal().getHeight();
    }
  }

  // ── Layout ────────────────────────────────────────────────────────────────────

  @Override
  protected void layoutChildren() {
    double w = getWidth();
    double h = getHeight();

    if (w <= 0 || h <= 0 || items.isEmpty()) {
      prevStrip.setVisible(false);
      nextStrip.setVisible(false);
      return;
    }

    // ── 1. Position items sequentially inside contentPane ─────────────────────
    // Items are placed at their natural positions (0 → totalContent) in contentPane's
    // local coordinate space. The translate on contentPane then moves them relative to
    // clipView, and clipView's clip does the masking.
    double pos = 0.0;
    for (Node item : items) {
      if (orientation == Orientation.HORIZONTAL) {
        double itemW =
            item instanceof Region r ? r.prefWidth(h) : item.getBoundsInLocal().getWidth();
        item.resize(itemW, h);
        item.setLayoutX(pos);
        item.setLayoutY(0);
        pos += itemW;
      } else {
        double itemH =
            item instanceof Region r ? r.prefHeight(w) : item.getBoundsInLocal().getHeight();
        item.resize(w, itemH);
        item.setLayoutX(0);
        item.setLayoutY(pos);
        pos += itemH;
      }
    }

    double containerSize = orientation == Orientation.HORIZONTAL ? w : h;
    overflows = pos > containerSize + 0.5;

    // ── 2. Size and position clipView and nav strips ───────────────────────────
    // When overflowing, always reserve NAV_STRIP_SIZE on each side so the layout
    // stays stable regardless of which strips are currently visible.
    double contentStart = overflows ? NAV_STRIP_SIZE : 0.0;
    double contentSize = overflows ? containerSize - 2 * NAV_STRIP_SIZE : containerSize;

    if (orientation == Orientation.HORIZONTAL) {
      clipView.resizeRelocate(contentStart, 0, contentSize, h);
      prevStrip.resizeRelocate(0, 0, NAV_STRIP_SIZE, h);
      nextStrip.resizeRelocate(w - NAV_STRIP_SIZE, 0, NAV_STRIP_SIZE, h);
    } else {
      clipView.resizeRelocate(0, contentStart, w, contentSize);
      prevStrip.resizeRelocate(0, 0, w, NAV_STRIP_SIZE);
      nextStrip.resizeRelocate(0, h - NAV_STRIP_SIZE, w, NAV_STRIP_SIZE);
    }

    // ── 3. Update clip to match clipView's visible bounds ─────────────────────
    // The clip is in clipView's local space. As contentPane is translated, items'
    // positions in that same space shift in/out of [0, contentSize].
    Rectangle clip = (Rectangle) clipView.getClip();
    clip.setWidth(clipView.getWidth());
    clip.setHeight(clipView.getHeight());

    // ── 4. Sync translate (skipped during animation to avoid fighting the transition)
    if (!animating) {
      if (!overflows) {
        firstVisibleIndex = 0;
        contentPane.setTranslateX(0);
        contentPane.setTranslateY(0);
      } else {
        double offset = leadingEdge(firstVisibleIndex);
        if (orientation == Orientation.HORIZONTAL) {
          contentPane.setTranslateX(-offset);
        } else {
          contentPane.setTranslateY(-offset);
        }
      }
    }

    // ── 5. Show/hide nav strips ───────────────────────────────────────────────
    refreshNavStrips();
  }
}

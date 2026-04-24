package org.integratedmodelling.klab.ide.components.generic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

/**
 * A JavaFX composite component that combines a search {@link TextField} with a search icon, wired
 * to a {@link TreeView}. The component starts in an inactive (visually disabled) state and becomes
 * editable when the user clicks either the field or the icon. Typing filters the tree in place:
 * only items for which {@code itemMatcher} returns {@code true}, and their ancestor items, are
 * shown. When the field is cleared the full tree is restored. Pressing Escape clears the field,
 * restores the tree, and returns the component to its inactive state.
 *
 * @param <T> the type of items held by the connected {@link TreeView}
 */
public class TreeSearchField<T> extends HBox {

  private static final String STYLE_INACTIVE = "-fx-text-fill: -color-fg-muted;";
  private static final String STYLE_ACTIVE = "-fx-text-fill: -color-accent-fg;";

  private TreeTableView<T> treeTableView;
  private TreeView<T> treeView;
  private BiFunction<String, T, Boolean> itemMatcher;
  private TextField searchField;
  private IconLabel searchIcon;

  /**
   * Snapshot of each item's original children list and expanded state, taken the first time a
   * non-empty filter is applied. Null when no filter is active.
   */
  private Map<TreeItem<T>, List<TreeItem<T>>> originalChildren;

  private Map<TreeItem<T>, Boolean> originalExpanded;

  public TreeSearchField(TreeView<T> treeView, BiFunction<String, T, Boolean> itemMatcher) {
    this.treeView = treeView;
    initialize(itemMatcher);
  }

  public TreeSearchField(TreeTableView<T> treeView, BiFunction<String, T, Boolean> itemMatcher) {
    this.treeTableView = treeView;
    initialize(itemMatcher);
  }

  private void initialize(BiFunction<String, T, Boolean> itemMatcher) {

    this.itemMatcher = itemMatcher;
    searchField = new TextField();
    searchField.setPromptText("Search…");
    searchField.setEditable(false);
    searchField.setOpacity(0.45);
    searchField.setPadding(new Insets(4, 26, 4, 7));

    searchIcon = new IconLabel(FontAwesomeSolid.SEARCH, 13, STYLE_INACTIVE);
    searchIcon.setCursor(Cursor.HAND);
    searchIcon.setPadding(new Insets(0, 6, 0, 0));
    searchIcon.setMouseTransparent(false);

    StackPane fieldStack = new StackPane(searchField, searchIcon);
    StackPane.setAlignment(searchIcon, Pos.CENTER_RIGHT);
    HBox.setHgrow(fieldStack, Priority.ALWAYS);

    setAlignment(Pos.CENTER_LEFT);
    getChildren().add(fieldStack);

    searchIcon.setOnMouseClicked(
        e -> {
          if (searchField.isEditable()) {
            deactivate();
          } else {
            activate();
          }
        });
    searchField.setOnMouseClicked(e -> activate());

    searchField
        .textProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              if (newVal == null || newVal.isEmpty()) {
                restoreFullTree();
              } else {
                if (originalChildren == null) {
                  snapshotTree();
                }
                applyFilter(newVal);
              }
            });

    searchField.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            deactivate();
            e.consume();
          }
        });
  }

  private void activate() {
    searchField.setEditable(true);
    searchField.setOpacity(1.0);
    searchIcon.setStyle(STYLE_ACTIVE);
    searchField.requestFocus();
  }

  private void deactivate() {
    // clear() triggers the text listener which calls restoreFullTree()
    searchField.clear();
    searchField.setEditable(false);
    searchField.setOpacity(0.45);
    searchIcon.setStyle(STYLE_INACTIVE);
    requestTreeFocus();
  }

  private void requestTreeFocus() {
    if (treeTableView != null) {
      treeTableView.requestFocus();
    } else {
      treeView.requestFocus();
    }
  }

  // --- tree snapshot / filter / restore ----------------------------------------

  private void snapshotTree() {
    originalChildren = new IdentityHashMap<>();
    originalExpanded = new IdentityHashMap<>();
    if (getRoot() != null) {
      snapshotItem(getRoot());
    }
  }

  private void snapshotItem(TreeItem<T> item) {
    originalChildren.put(item, new ArrayList<>(item.getChildren()));
    originalExpanded.put(item, item.isExpanded());
    for (TreeItem<T> child : item.getChildren()) {
      snapshotItem(child);
    }
  }

  private void applyFilter(String text) {
    if (getRoot() != null) {
      filterItem(getRoot(), text);
      getRoot().setExpanded(true);
    }
  }

  private TreeItem<T> getRoot() {
    return treeTableView != null ? treeTableView.getRoot() : treeView.getRoot();
  }

  /**
   * Recursively filters {@code item}'s children to those that match or have matching descendants,
   * and expands branches that contain matches.
   *
   * @return {@code true} if this item or any of its (filtered) descendants matches
   */
  private boolean filterItem(TreeItem<T> item, String text) {
    List<TreeItem<T>> origChildren = originalChildren.getOrDefault(item, List.of());

    boolean selfMatches = item.getValue() != null && itemMatcher.apply(text, item.getValue());

    List<TreeItem<T>> keepChildren = new ArrayList<>();
    for (TreeItem<T> child : origChildren) {
      if (filterItem(child, text)) {
        keepChildren.add(child);
      }
    }

    item.getChildren().setAll(keepChildren);
    if (!keepChildren.isEmpty()) {
      item.setExpanded(true);
    }

    return selfMatches || !keepChildren.isEmpty();
  }

  private void restoreFullTree() {
    if (originalChildren == null) {
      return;
    }
    if (getRoot() != null) {
      restoreItem(getRoot());
    }
    originalChildren = null;
    originalExpanded = null;
  }

  private void restoreItem(TreeItem<T> item) {
    List<TreeItem<T>> origChildren = originalChildren.getOrDefault(item, List.of());
    item.getChildren().setAll(origChildren);
    item.setExpanded(Boolean.TRUE.equals(originalExpanded.get(item)));
    for (TreeItem<T> child : origChildren) {
      restoreItem(child);
    }
  }
}

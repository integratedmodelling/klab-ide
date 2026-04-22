package org.integratedmodelling.klab.ide.components.generic;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

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
    private static final String STYLE_ACTIVE   = "-fx-text-fill: -color-accent-fg;";

    private final TreeView<T> treeView;
    private final BiFunction<String, T, Boolean> itemMatcher;
    private final TextField searchField;
    private final IconLabel searchIcon;

    /**
     * Snapshot of each item's original children list, taken the first time a non-empty filter is
     * applied. Null when no filter is active.
     */
    private Map<TreeItem<T>, List<TreeItem<T>>> originalChildren;

    public TreeSearchField(TreeView<T> treeView, BiFunction<String, T, Boolean> itemMatcher) {
        this.treeView = treeView;
        this.itemMatcher = itemMatcher;

        searchField = new TextField();
        searchField.setPromptText("Search…");
        searchField.setEditable(false);
        searchField.setOpacity(0.45);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchIcon = new IconLabel(FontAwesomeSolid.SEARCH, 13, STYLE_INACTIVE);
        searchIcon.setCursor(Cursor.HAND);
        searchIcon.setPadding(new Insets(0, 5, 0, 5));

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(2);
        getChildren().addAll(searchField, searchIcon);

        searchIcon.setOnMouseClicked(e -> {
            if (searchField.isEditable()) {
                deactivate();
            } else {
                activate();
            }
        });
        searchField.setOnMouseClicked(e -> activate());

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                restoreFullTree();
            } else {
                if (originalChildren == null) {
                    snapshotTree();
                }
                applyFilter(newVal);
            }
        });

        searchField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
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
        treeView.requestFocus();
    }

    // --- tree snapshot / filter / restore ----------------------------------------

    private void snapshotTree() {
        originalChildren = new IdentityHashMap<>();
        if (treeView.getRoot() != null) {
            snapshotItem(treeView.getRoot());
        }
    }

    private void snapshotItem(TreeItem<T> item) {
        originalChildren.put(item, new ArrayList<>(item.getChildren()));
        for (TreeItem<T> child : item.getChildren()) {
            snapshotItem(child);
        }
    }

    private void applyFilter(String text) {
        if (treeView.getRoot() != null) {
            filterItem(treeView.getRoot(), text);
            treeView.getRoot().setExpanded(true);
        }
    }

    /**
     * Recursively filters {@code item}'s children to those that match or have matching descendants,
     * and expands branches that contain matches.
     *
     * @return {@code true} if this item or any of its (filtered) descendants matches
     */
    private boolean filterItem(TreeItem<T> item, String text) {
        List<TreeItem<T>> origChildren =
                originalChildren.getOrDefault(item, List.of());

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
        if (treeView.getRoot() != null) {
            restoreItem(treeView.getRoot());
        }
        originalChildren = null;
    }

    private void restoreItem(TreeItem<T> item) {
        List<TreeItem<T>> origChildren = originalChildren.getOrDefault(item, List.of());
        item.getChildren().setAll(origChildren);
        for (TreeItem<T> child : origChildren) {
            restoreItem(child);
        }
    }
}

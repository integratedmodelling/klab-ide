package org.integratedmodelling.klab.ide.components.treeviews;

import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public final class TreeViewClickBehavior {

  private static final String DISABLE_BRANCH_DOUBLE_CLICK_TOGGLE_KEY =
      TreeViewClickBehavior.class.getName() + ".disableBranchDoubleClickToggle";
  private static final String DISCLOSURE_NODE_STYLE_CLASS = "tree-disclosure-node";

  private TreeViewClickBehavior() {}

  public static void disableBranchToggleOnDoubleClick(TreeView<?> treeView) {
    install(treeView, TreeViewClickBehavior::treeItemFromTreeViewEvent);
  }

  public static void disableBranchToggleOnDoubleClick(TreeTableView<?> treeTableView) {
    install(treeTableView, TreeViewClickBehavior::treeItemFromTreeTableViewEvent);
  }

  private static void install(
      Control treeControl, Function<MouseEvent, TreeItem<?>> treeItemResolver) {
    if (treeControl.getProperties().containsKey(DISABLE_BRANCH_DOUBLE_CLICK_TOGGLE_KEY)) {
      return;
    }
    treeControl.getProperties().put(DISABLE_BRANCH_DOUBLE_CLICK_TOGGLE_KEY, Boolean.TRUE);
    // JavaFX toggles branch expansion from the second mouse press, before clicked handlers run.
    treeControl.addEventFilter(
        MouseEvent.MOUSE_PRESSED,
        event -> consumeBranchBodyDoubleClick(event, treeItemResolver));
  }

  private static void consumeBranchBodyDoubleClick(
      MouseEvent event, Function<MouseEvent, TreeItem<?>> treeItemResolver) {
    if (event.isConsumed()
        || event.getButton() != MouseButton.PRIMARY
        || event.getClickCount() < 2) {
      return;
    }

    var target = targetNode(event);
    if (target == null || isDisclosureNode(target)) {
      return;
    }

    var treeItem = treeItemResolver.apply(event);
    if (treeItem != null && !treeItem.isLeaf()) {
      event.consume();
    }
  }

  private static TreeItem<?> treeItemFromTreeViewEvent(MouseEvent event) {
    var cell = ancestor(targetNode(event), TreeCell.class);
    return cell == null ? null : cell.getTreeItem();
  }

  private static TreeItem<?> treeItemFromTreeTableViewEvent(MouseEvent event) {
    var target = targetNode(event);
    var row = ancestor(target, TreeTableRow.class);
    if (row != null) {
      return row.getTreeItem();
    }
    var cell = ancestor(target, TreeTableCell.class);
    return cell == null || cell.getTreeTableRow() == null
        ? null
        : cell.getTreeTableRow().getTreeItem();
  }

  private static Node targetNode(MouseEvent event) {
    return event.getTarget() instanceof Node node
        ? node
        : event.getPickResult().getIntersectedNode();
  }

  private static boolean isDisclosureNode(Node node) {
    for (Node current = node; current != null; current = current.getParent()) {
      if (current.getStyleClass().contains(DISCLOSURE_NODE_STYLE_CLASS)) {
        return true;
      }
      if (current instanceof TreeCell<?> || current instanceof TreeTableCell<?, ?>) {
        return false;
      }
    }
    return false;
  }

  private static <T> T ancestor(Node node, Class<T> type) {
    for (Node current = node; current != null; current = current.getParent()) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
    }
    return null;
  }
}

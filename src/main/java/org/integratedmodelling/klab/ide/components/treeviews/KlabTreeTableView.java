package org.integratedmodelling.klab.ide.components.treeviews;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;

public class KlabTreeTableView<T> extends TreeTableView<T> {

  public KlabTreeTableView() {
    TreeViewClickBehavior.disableBranchToggleOnDoubleClick(this);
  }

  public KlabTreeTableView(TreeItem<T> root) {
    super(root);
    TreeViewClickBehavior.disableBranchToggleOnDoubleClick(this);
  }
}

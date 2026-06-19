package org.integratedmodelling.klab.ide.components.treeviews;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

public class KlabTreeView<T> extends TreeView<T> {

  public KlabTreeView() {
    TreeViewClickBehavior.disableBranchToggleOnDoubleClick(this);
  }

  public KlabTreeView(TreeItem<T> root) {
    super(root);
    TreeViewClickBehavior.disableBranchToggleOnDoubleClick(this);
  }
}

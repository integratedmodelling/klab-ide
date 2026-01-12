package org.integratedmodelling.klab.ide.components.treeviews;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;

import java.util.HashSet;
import java.util.Set;

public class ObservationTree extends TreeTableView<RuntimeAsset> {

  private ClientKnowledgeGraph clientKnowledgeGraph;

  public ObservationTree() {

    setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    setShowRoot(false);

    TreeTableColumn<RuntimeAsset, HBox> descriptionColumn = new TreeTableColumn<>("Description");
    descriptionColumn.setCellValueFactory(
        param -> new SimpleObjectProperty<>(observationDescription(param.getValue().getValue())));

    //    TreeTableColumn<RuntimeAsset, Node> statusColumn = new TreeTableColumn<>("Status");
    //    statusColumn.setMinWidth(40);
    //    statusColumn.setMaxWidth(40);
    //    statusColumn.setCellValueFactory(
    //        param -> {
    //          var activity = param.getValue() == null ? null : param.getValue().getValue();
    //          var icon = Theme.getGraphics(activity);
    //          return new SimpleObjectProperty<>(icon);
    //        });

    descriptionColumn.prefWidthProperty().bind(widthProperty().subtract(10));

    getColumns().setAll(descriptionColumn);
    setRoot(new TreeItem<>());
  }

  private HBox observationDescription(RuntimeAsset observation) {

    var icon = Theme.getGraphics(observation);
    icon.setMaxWidth(24);
    icon.setMinWidth(24);

    var description =
        Utils.Strings.abbreviate(Utils.Strings.replaceWhitespace(observation.toString(), " "), 42);

    var label = new Label(description);
    HBox.setHgrow(label, Priority.ALWAYS);
    var ret = new HBox(icon, label);
    ret.setSpacing(2);
    ret.setAlignment(Pos.CENTER_LEFT);
    icon.setOnMouseClicked(mouseEvent -> attemptSettingContext(observation, icon));

    return ret;
  }

  private void attemptSettingContext(RuntimeAsset observation, IconLabel icon) {
    System.out.println("PUTAZZO IL GESÚ");
  }

  public void update(IDEContextScope scope, Observation observation) {
    //  redraw tree and select the passed observation
    this.clientKnowledgeGraph = scope.getDigitalTwin().getKnowledgeGraph();
    setRoot(new AssetTreeItem(RuntimeAsset.CONTEXT_ASSET));
    //  select observation
    var treeItem = findTreeItem(observation);
    if (treeItem != null) {
      if (treeItem.getParent() != null) {
        treeItem.getParent().setExpanded(true);
      }
      getSelectionModel().select(treeItem);
      scrollTo(getSelectionModel().getSelectedIndex());
    }
  }

  private TreeItem<RuntimeAsset> findTreeItem(RuntimeAsset asset) {
    if (asset == null || getRoot() == null) {
      return null;
    }
    return findTreeItemRecursive(getRoot(), asset);
  }

  private TreeItem<RuntimeAsset> findTreeItemRecursive(
      TreeItem<RuntimeAsset> current, RuntimeAsset asset) {
    if (current == null) {
      return null;
    }

    RuntimeAsset currentAsset = current.getValue();
    if (currentAsset != null && currentAsset.getId() == asset.getId()) {
      return current;
    }

    // Expand the node to ensure children are loaded
    current.setExpanded(true);

    for (TreeItem<RuntimeAsset> child : current.getChildren()) {
      TreeItem<RuntimeAsset> result = findTreeItemRecursive(child, asset);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private class AssetTreeItem extends TreeItem<RuntimeAsset> {

    public AssetTreeItem(RuntimeAsset asset) {
      super(asset);
    }

    @Override
    public boolean isLeaf() {
      var asset = getValue();
      return asset == null
          || (asset instanceof Observation && asset.getChildrenCount() == 0)
          || (!(asset
                  instanceof Observation) // TODO eventually this should be correct for all assets
              && clientKnowledgeGraph.outgoing(asset, GraphModel.Relationship.HAS_CHILD).isEmpty());
    }

    @Override
    public ObservableList<TreeItem<RuntimeAsset>> getChildren() {

      var children = super.getChildren();
      RuntimeAsset asset = getValue();
      if (asset != null && (!(asset instanceof Observation) || asset.getChildrenCount() > 0)) {
        Set<Long> selectedIds =
            new HashSet<>(
                children.stream().map(TreeItem::getValue).map(RuntimeAsset::getId).toList());
        var ch = clientKnowledgeGraph.getChildAssets(asset);
        for (var child : ch) {
          if (selectedIds.contains(child.getId())) {
            continue;
          }
          children.add(new AssetTreeItem(child));
        }
        return children;
      }
      return children;
    }
  }
}

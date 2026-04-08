package org.integratedmodelling.klab.ide.components.treeviews;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
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
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;

public class ObservationTree extends TreeTableView<RuntimeAsset> {

  private ClientKnowledgeGraph clientKnowledgeGraph;

  public ObservationTree() {

    setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    setShowRoot(false);
    setPlaceholder(new Label("No observations available"));
    TreeTableColumn<RuntimeAsset, HBox> descriptionColumn = new TreeTableColumn<>("Description");
    descriptionColumn.setCellValueFactory(
        param -> new SimpleObjectProperty<>(observationDescription(param.getValue().getValue())));
    descriptionColumn.prefWidthProperty().bind(widthProperty().subtract(10));
    getColumns().setAll(descriptionColumn);
    setRoot(new TreeItem<>());
  }

  private HBox observationDescription(RuntimeAsset observation) {

    var icon = Theme.getGraphics(observation);
    icon.setMaxWidth(24);
    icon.setMinWidth(24);

    var description = Utils.Strings.abbreviate(Theme.getLabel(observation), 42);

    var label = new Label(description);
    HBox.setHgrow(label, Priority.ALWAYS);

    int level = 0;
    var info = new Label("");
    if (observation instanceof Observation obs) {
      for (var notification : obs.getNotifications()) {
        switch (notification.getLevel()) {
          case Info:
            level = 1;
            break;
          case Warning:
            level = 2;
            break;
          case Error, SystemError:
            level = 3;
            break;
        }
      }

      // TODO improve this, use right icons, provide hover support
      if (level > 0) {
        label.setGraphic(new IconLabel(Theme.OBSERVATION_ICON, 12, Color.DARKGOLDENROD));
      }
    }

    var ret = new HBox(icon, label);
    ret.setSpacing(2);
    ret.setAlignment(Pos.CENTER_LEFT);
    icon.setOnMouseClicked(mouseEvent -> attemptSettingContext(observation, icon));

    return ret;
  }

  private void attemptSettingContext(RuntimeAsset observation, IconLabel icon) {
    System.out.println("PUTAZZO IL GESÚ");
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

  public void update(RuntimeAsset rootAsset, RuntimeAsset focalAsset, IDEContextScope scope) {
    var root = TreeModel.createTree(rootAsset, focalAsset, scope);
    setRoot(root.getFirst());
    if (root.getSecond() != null) {
      getSelectionModel().select(root.getSecond());
    }
  }
}

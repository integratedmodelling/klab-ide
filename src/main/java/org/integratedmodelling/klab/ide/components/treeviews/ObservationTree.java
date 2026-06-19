package org.integratedmodelling.klab.ide.components.treeviews;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.utils.DoubleClickHandler;
import org.kordamp.ikonli.material2.Material2AL;

public class ObservationTree extends KlabTreeTableView<RuntimeAsset> {

  private ClientKnowledgeGraph clientKnowledgeGraph;
  TreeTableColumn<RuntimeAsset, HBox> descriptionColumn;

  public ObservationTree() {

    setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    setShowRoot(false);
    setPlaceholder(new Label("No observations available"));
    descriptionColumn = new TreeTableColumn<>("Description");
    descriptionColumn.setCellValueFactory(
        param -> new SimpleObjectProperty<>(observationDescription(param.getValue().getValue())));
    descriptionColumn.prefWidthProperty().bind(widthProperty().subtract(10));
    getColumns().setAll(descriptionColumn);
    setRoot(new TreeItem<>());
  }

  public boolean matches(String string, RuntimeAsset asset) {
    return Theme.getLabel(asset).toLowerCase().contains(string.toLowerCase());
  }

  private HBox observationDescription(RuntimeAsset observation) {

    var scope = KlabIDEController.instance().getFocalScope();
    RuntimeAsset current = null;
    if (scope != null) {
      current = scope.getContextObservation();
    }

    var color =
        observation instanceof Observation obs
            ? Theme.getColorForType(
                SemanticType.fundamentalType(obs.getObservable().getSemantics().getType()))
            : Color.BLACK;

    var icon =
        current != null && current.getId() == observation.getId()
            ? new IconLabel(Material2AL.HOME, 16, color)
            : Theme.getGraphics(observation);

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
        //        label.setGraphic(new IconLabel(Theme.OBSERVATION_ICON, 12, Color.DARKGOLDENROD));
      }
    }

    var ret = new HBox(icon, label);
    ret.setSpacing(2);
    ret.setAlignment(Pos.CENTER_LEFT);
    icon.setOnMouseClicked(
        mouseEvent -> {
          if (attemptSettingContext(observation, icon)) {
            refresh();
          }
        });

    ret.addEventHandler(
        MouseEvent.MOUSE_CLICKED,
        new DoubleClickHandler<>(
                observation,
                a -> {
                  if (KlabIDEController.instance().isInspectorShown()) {
                    KlabIDEController.instance().getInspector().inspect(a);
                  }
                },
                a -> {
                  if (KlabIDEController.instance().getInspector().getCurrentObject() != a) {
                    if (!KlabIDEController.instance().isInspectorShown()) {
                      KlabIDEController.instance().showInspector();
                    }
                    KlabIDEController.instance().getInspector().inspect(a);
                  } else if (KlabIDEController.instance().isInspectorShown()) {
                    // reset to enable repetition
                    KlabIDEController.instance().getInspector().inspect(null);
                    KlabIDEController.instance().hideInspector();
                  }
                })
            .getHandler());

    return ret;
  }

  private boolean attemptSettingContext(RuntimeAsset observation, IconLabel icon) {
    if (observation instanceof Observation obs
        && obs.getObservable().is(SemanticType.SUBJECT)
        && !obs.getObservable().getSemantics().isCollective()) {
      var scope = KlabIDEController.instance().getFocalScope();
      if (scope != null) {
        var current = scope.getContextObservation();
        if (current != null) {
          if (current.getId() == obs.getId()) {
            scope.within(null);
            return true;
          }
        }
        scope.within(obs);
        return true;
      }
    }
    return false;
  }

  public void update(RuntimeAsset rootAsset, RuntimeAsset focalAsset, IDEContextScope scope) {
    var root = TreeModel.createTree(rootAsset, focalAsset, scope);
    setRoot(root.getFirst());
    if (root.getSecond() != null) {
      getSelectionModel().select(root.getSecond());
    }
  }

  public void reset() {
    setRoot(new TreeItem<>());
  }
}

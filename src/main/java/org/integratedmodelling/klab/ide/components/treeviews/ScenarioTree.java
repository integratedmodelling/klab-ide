package org.integratedmodelling.klab.ide.components.treeviews;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;

public class ScenarioTree extends TreeTableView<KimNamespace> {

  public ScenarioTree() {

    setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    setShowRoot(false);
    setPlaceholder(new Label("No scenarios available"));

    TreeTableColumn<KimNamespace, HBox> descriptionColumn = new TreeTableColumn<>("Description");
    descriptionColumn.setCellValueFactory(
        param -> new SimpleObjectProperty<>(scenarioDescriptor(param.getValue().getValue())));

    TreeTableColumn<KimNamespace, IconLabel> statusColumn = new TreeTableColumn<>("Status");
    statusColumn.setMinWidth(40);
    statusColumn.setMaxWidth(40);
    statusColumn.setCellValueFactory(
        param -> {
          var activity = param.getValue() == null ? null : param.getValue().getValue();
          var ikon = Theme.OBSERVATION_ICON;
          var color = Color.GOLDENROD;
          //          if (activity != null && activity.getOutcome() != null) {
          //            ikon =
          //                activity.getOutcome() == Activity.Outcome.SUCCESS
          //                    ? Material2AL.CHECK_CIRCLE
          //                    : Material2AL.ERROR;
          //            color = activity.getOutcome() == Activity.Outcome.SUCCESS ? Color.GREEN :
          // Color.RED;
          //          }
          var icon = new IconLabel(ikon, 14, color);
          return new SimpleObjectProperty<>(icon);
        });

    descriptionColumn.prefWidthProperty().bind(widthProperty().subtract(40));

    getColumns().setAll(descriptionColumn, statusColumn);
    setRoot(new TreeItem<>());
  }

  private HBox scenarioDescriptor(KimNamespace value) {
    var ret = new HBox();
    return ret;
  }
}

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
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;

public class ObservationTree extends TreeTableView<Observation> {

  public ObservationTree() {

    setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    setShowRoot(false);

    TreeTableColumn<Observation, HBox> descriptionColumn = new TreeTableColumn<>("Description");
    descriptionColumn.setCellValueFactory(
        param -> new SimpleObjectProperty<>(observationDescription(param.getValue().getValue())));

    TreeTableColumn<Observation, IconLabel> statusColumn = new TreeTableColumn<>("Status");
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
//            color = activity.getOutcome() == Activity.Outcome.SUCCESS ? Color.GREEN : Color.RED;
//          }
          var icon = new IconLabel(ikon, 14, color);
          return new SimpleObjectProperty<>(icon);
        });

    descriptionColumn.prefWidthProperty().bind(widthProperty().subtract(40));

    getColumns().setAll(descriptionColumn, statusColumn);
    setRoot(new TreeItem<>());
  }

    private HBox observationDescription(Observation observation) {
        var icon = new IconLabel(Theme.OBSERVATION_ICON,
//                new IconLabel(
//                        switch (activity.getType()) {
//                            case CONTEXT_INITIALIZATION, SUBMISSION -> Evaicons.DOWNLOAD;
//                            case INITIALIZATION -> BootstrapIcons.PLAY_BTN;
//                            case RESOLUTION -> CarbonIcons.TREE_VIEW_ALT;
//                            case CONTEXTUALIZATION -> MaterialDesign.MDI_RUN;
//                        },
                        16,
                        Theme.FOREGROUND_COLOR);
        icon.setMaxWidth(24);
        icon.setMinWidth(24);

        var description =
                Utils.Strings.abbreviate(
                        Utils.Strings.replaceWhitespace(observation.toString(), " "), 42);

        var label = new Label(description);
        HBox.setHgrow(label, Priority.ALWAYS);
        var ret = new HBox(icon, label);
        ret.setSpacing(2);
        ret.setAlignment(Pos.CENTER_LEFT);
        icon.setOnMouseClicked(mouseEvent -> System.out.println(observation));
        return ret;
    }

    public void update(IDEContextScope scope, Observation observation) {
    // TODO redraw tree and select the passed observation
  }
}

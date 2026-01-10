package org.integratedmodelling.klab.ide.components.treeviews;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

import java.util.*;

public class ActivityTree extends TreeTableView<Activity> {

  public ActivityTree() {

    setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    setShowRoot(false);

    TreeTableColumn<Activity, HBox> descriptionColumn = new TreeTableColumn<>("Description");
    descriptionColumn.setCellValueFactory(
        param -> new SimpleObjectProperty<>(activityDescription(param.getValue().getValue())));

    TreeTableColumn<Activity, IconLabel> statusColumn = new TreeTableColumn<>("Status");
    statusColumn.setMinWidth(40);
    statusColumn.setMaxWidth(40);
    statusColumn.setCellValueFactory(
        param -> {
          var activity = param.getValue() == null ? null : param.getValue().getValue();
          var ikon = Material2AL.ACCESS_ALARM;
          var color = Color.GOLDENROD;
          if (activity != null && activity.getOutcome() != null) {
            ikon =
                activity.getOutcome() == Activity.Outcome.SUCCESS
                    ? Material2AL.CHECK_CIRCLE
                    : Material2AL.ERROR;
            color = activity.getOutcome() == Activity.Outcome.SUCCESS ? Color.GREEN : Color.RED;
          }
          var icon = new IconLabel(ikon, 14, color);
          return new SimpleObjectProperty<>(icon);
        });

    descriptionColumn.prefWidthProperty().bind(widthProperty().subtract(40));

    getColumns().setAll(descriptionColumn, statusColumn);
    setRoot(new TreeItem<>());
  }

  private HBox activityDescription(Activity activity) {

    var icon =
        new IconLabel(
            switch (activity.getType()) {
              case CONTEXT_INITIALIZATION, SUBMISSION -> Evaicons.DOWNLOAD;
              case INITIALIZATION -> BootstrapIcons.PLAY_BTN;
              case RESOLUTION -> CarbonIcons.TREE_VIEW_ALT;
              case CONTEXTUALIZATION -> MaterialDesign.MDI_RUN;
            },
            16,
            Theme.FOREGROUND_COLOR);
    icon.setMaxWidth(24);
    icon.setMinWidth(24);

    var description =
        Utils.Strings.abbreviate(
            Utils.Strings.replaceWhitespace(activity.getDescription(), " "), 42);
    var label = new Label(description);
    HBox.setHgrow(label, Priority.ALWAYS);
    var ret = new HBox(icon, label);
    ret.setSpacing(2);
    ret.setAlignment(Pos.CENTER_LEFT);
    ret.setOnMouseClicked(mouseEvent -> System.out.println(activity.getDescription()));
    return ret;
  }

  public void update(IDEContextScope scope) {

    // Create defensive copies of the data to avoid ConcurrentModificationException
    var vertices = new ArrayList<>(scope.getActivityGraph().vertexSet());
    var rootActivities =
        vertices.stream()
            .filter(activity -> scope.getActivityGraph().incomingEdgesOf(activity).isEmpty())
            .sorted(Comparator.comparingLong(Activity::getStart))
            .toList();

    // Create a snapshot of the graph structure for each activity
    var activityChildren = new HashMap<Activity, List<Activity>>();
    for (Activity activity : vertices) {
      var children =
          scope.getActivityGraph().outgoingEdgesOf(activity).stream()
              .map(scope.getActivityGraph()::getEdgeTarget)
              .toList();
      activityChildren.put(activity, children);
    }

    Platform.runLater(
        () -> {
          getRoot().getChildren().clear();
          for (Activity activity : rootActivities) {
            getRoot().getChildren().add(makeItem(activity, activityChildren));
          }
          // Refresh all columns to ensure proper cell rendering - otherwise the columns that were
          // previously visible won't change.
          getColumns()
              .forEach(
                  column -> {
                    column.setVisible(false);
                    column.setVisible(true);
                  });
        });
  }

  private TreeItem<Activity> makeItem(
      Activity activity, Map<Activity, List<Activity>> activityChildren) {
    TreeItem<Activity> ret = new TreeItem<>(activity);
    List<Activity> children = activityChildren.get(activity);
    if (children != null) {
      for (Activity child : children) {
        ret.getChildren().add(makeItem(child, activityChildren));
      }
    }
    return ret;
  }
}

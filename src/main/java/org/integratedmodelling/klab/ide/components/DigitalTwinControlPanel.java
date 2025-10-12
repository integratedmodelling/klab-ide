package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.model.DigitalTwinPeer;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlled by the DT peer installed in the main IDE controller.
 *
 * <p>IDEA: top menu has DT choice (MenuButton) + DT switch to main view button (->) + Observer
 * label + Observer choice (MenuButton)
 *
 * <p>Center area shows context in some way and has spinner for current observation (ideally on top
 * of context). If no context show the "drop here" arrow, same size spinner if computing. Errors and
 * info message should be just small buttons getting colored; click should show the list as an
 * overlay. Full logs in full view
 *
 * <p>Bottom menu has Context choice (MenuButton) + Context label + Scenario count + Scenario choose
 * button (dialog or switch)/reset all scenarios
 */
public class DigitalTwinControlPanel extends BorderPane implements DigitalTwinViewer {

  private final ProgressIndicator progressIndicator;
  private final Label statusLabel;
  private ContextScope scope;

  public enum Status {
    IDLE,
    COMPUTING,
    ERROR,
    RECEIVING,
    INFO
  }

  private Pane dropZone;
  private Status status = Status.IDLE;
  private TreeTableView<Activity> treeTableView;
  /* controller is bound after the first observation is made */
  private DigitalTwinPeer controller;

  // otherwise?

  public DigitalTwinControlPanel(int size, EditorPage<?, ?> editorPage) {
    super();
    setMinHeight(size);
    setMinWidth(size);

    // Create top control bar
    HBox controlBar = new HBox(0);
    controlBar.setPrefHeight(24);
    controlBar.setAlignment(Pos.CENTER_LEFT);
    controlBar.setPadding(new Insets(1));
    controlBar.setStyle("-fx-background-color: #E0E0E0;");

    //    // Target selection menu
    //    MenuButton targetMenu = new MenuButton();
    //    targetMenu.setGraphic(new FontIcon(Material2AL.CENTER_FOCUS_STRONG));
    //    //    targetMenu.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
    Button swapButton = new Button();
    swapButton.setGraphic(new FontIcon(Material2MZ.SWAP_HORIZONTAL_CIRCLE));
    swapButton.setOnAction(
        e -> {
          if (getScope() != null) {
            KlabIDEController.instance()
                .getView(KlabIDEController.View.DIGITAL_TWINS, DigitalTwinView.class)
                .showDigitalTwin(getScope());
            KlabIDEController.instance().selectView(KlabIDEController.View.DIGITAL_TWINS);
          }
        });
    swapButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);

    Button resetButton = new Button();
    resetButton.setGraphic(new FontIcon(Material2AL.DELETE_FOREVER));
    resetButton.setOnAction(e -> editorPage.deleteScope(scope));
    resetButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);

    // Status label
    this.statusLabel = new Label("No target selected");
    HBox.setHgrow(statusLabel, Priority.ALWAYS);
    statusLabel.setMaxWidth(Double.MAX_VALUE);

    Button observerMenu = new Button();
    observerMenu.setGraphic(new FontIcon(Material2MZ.REMOVE_RED_EYE));
    observerMenu.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);

    // Progress indicator
    this.progressIndicator = new ProgressIndicator(0d);
    progressIndicator.setPrefSize(24, 24);
    progressIndicator.setMaxSize(24, 24);
    progressIndicator.setMinSize(24, 24);
    //    progressIndicator.setProgress(1);

    controlBar
        .getChildren()
        .addAll(swapButton, resetButton, statusLabel, observerMenu, progressIndicator);
    setTop(controlBar);

    treeTableView = new TreeTableView<>();
    treeTableView.setMinSize(220, 220);
    treeTableView.setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    treeTableView.getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    treeTableView.setShowRoot(false);

    //    TreeTableColumn<Activity, IconLabel> typeColumn = new TreeTableColumn<>("Type");
    //    typeColumn.setPrefWidth(52);
    //    typeColumn.setCellValueFactory(
    //        param -> {
    //          var icon = new IconLabel(FontAwesomeSolid.CIRCLE, 16, Color.GREEN);
    //          return new SimpleObjectProperty<>(icon);
    //        });

    TreeTableColumn<Activity, String> descriptionColumn = new TreeTableColumn<>("Description");
    descriptionColumn.prefWidthProperty().bind(treeTableView.widthProperty().subtract(32));
    descriptionColumn.setCellValueFactory(
        param -> new SimpleObjectProperty<>(activityDescription(param.getValue().getValue())));

    TreeTableColumn<Activity, IconLabel> statusColumn = new TreeTableColumn<>("Status");
    //    statusColumn.setPrefWidth(32);
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
          var icon = new IconLabel(ikon, 16, color);
          return new SimpleObjectProperty<>(icon);
        });

    treeTableView.getColumns().setAll(/*typeColumn,*/ descriptionColumn, statusColumn);
    treeTableView.setRoot(new TreeItem<>());
    treeTableView.setShowRoot(false);

    dropZone = new Pane();
    dropZone.setMinSize(220, 220);
    dropZone.setMaxSize(220, 220);
    dropZone.setStyle(
        "-fx-background-color: #F5F5F5; -fx-border-color: grey; -fx-border-width: 5; -fx-border-style: dashed; -fx-border-radius: 10;");
    //    Label dropLabel = new Label("Drop an observable here");
    //    dropLabel.setTextFill(Color.GREY);
    //    dropZone.getChildren().add(dropLabel);
    //    dropLabel.setLayoutX((220 - dropLabel.prefWidth(-1)) / 2);
    //    dropLabel.setLayoutY((220 - dropLabel.prefHeight(-1)) / 2);
    setCenter(treeTableView);

    // Create bottom control bar for scenarios
    HBox scenarioBar = new HBox(10);
    scenarioBar.setPadding(new Insets(5));
    scenarioBar.setStyle("-fx-background-color: #E0E0E0;");

    //    // Scenario selection menu
    //    MenuButton scenarioMenu = new MenuButton();
    //    scenarioMenu.setGraphic(new FontIcon(Material2AL.LIBRARY_BOOKS));

    // Scenario selection combobox
    ComboBox<String> scenarioComboBox = new ComboBox<>();
    scenarioComboBox.setPromptText("Select scenario");
    HBox.setHgrow(scenarioComboBox, Priority.ALWAYS);
    //
    //    // Current scenario label
    //    Label currentScenarioLabel = new Label("No scenario selected");
    HBox.setHgrow(scenarioComboBox, Priority.ALWAYS);
    scenarioComboBox.setMaxWidth(Double.MAX_VALUE);

    // Function buttons
    Button collapseButton = new Button();
    collapseButton.setGraphic(new FontIcon(Material2AL.ARROW_DOWNWARD));
    collapseButton.setOnAction(e -> editorPage.hideDigitalTwinControlPanel());
    collapseButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);

    scenarioBar.getChildren().addAll(scenarioComboBox, collapseButton);
    setBottom(scenarioBar);
  }

  private String activityDescription(Activity value) {
    // TODO
    return Utils.Strings.abbreviate(
        Utils.Strings.replaceWhitespace(value.getDescription(), " "), 64);
  }

  public void setStatus(Status status) {
    this.status = status;
    Platform.runLater(
        () -> {
          switch (status) {
            case IDLE -> {
              progressIndicator.setProgress(0d);
              setCenter(treeTableView);
            }
            case RECEIVING -> {
              setCenter(dropZone);
            }
            case COMPUTING -> {
              setCenter(treeTableView);
              progressIndicator.setProgress(-1d);
            }
          }
        });
  }

  public void setScope(ContextScope scope) {

    if (this.scope != null) {
      throw new KlabInternalErrorException("SCOPE CAN ONLY BE BOUND ONCE IN A DT WIDGET");
    }
    Platform.runLater(
        () -> {
          //          ComboBox<String> scenarioBox =
          //              (ComboBox<String>) ((HBox) getBottom()).getChildren().get(1);
          //          scenarioBox.getItems().clear();
        });
    this.scope = scope;
    this.controller = KlabIDEController.instance().requireDigitalTwinPeer(scope);
    this.controller.register(this);
    // TODO define the full interface and bind the controller
  }

  public ContextScope getScope() {
    return this.scope;
  }

  @Override
  public void submissionStarted(Observation observation) {}

  @Override
  public void submissionAborted(Observation observation) {}

  @Override
  public void submissionFinished(Observation observation) {}

  @Override
  public void setContext(Observation observation) {
    Theme.setLabel(this.statusLabel, observation);
  }

  @Override
  public void setObserver(Observation observation) {}

  //  @Override
  //  public void activityFinished(Activity activity) {
  //    Platform.runLater(
  //        () -> {
  //          TreeItem<Activity> existing = findTreeItemByTransientId(activity.getTransientId());
  //          if (existing != null) {
  //            existing.setValue(activity);
  //          }
  //        });
  //  }
  //
  //  @Override
  //  public void activityStarted(Activity activity) {
  //    Platform.runLater(
  //        () -> {
  //          TreeItem<Activity> item = new TreeItem<>(activity);
  //          treeTableView.getRoot().getChildren().add(item);
  //        });
  //  }

  private TreeItem<Activity> findTreeItemByTransientId(long transientId) {
    if (treeTableView.getRoot() == null) return null;
    for (TreeItem<Activity> item : treeTableView.getRoot().getChildren()) {
      if (item.getValue().getTransientId() == transientId) {
        return item;
      }
    }
    return null;
  }

  @Override
  public void knowledgeGraphModified() {}

  @Override
  public void activitiesModified(Graph<Activity, DefaultEdge> activityGraph) {
    // Create defensive copies of the data to avoid ConcurrentModificationException
    var vertices = new ArrayList<>(activityGraph.vertexSet());
    var rootActivities =
        vertices.stream()
            .filter(activity -> activityGraph.incomingEdgesOf(activity).isEmpty())
            .sorted(Comparator.comparingLong(Activity::getStart))
            .toList();

    // Create a snapshot of the graph structure for each activity
    var activityChildren = new HashMap<Activity, List<Activity>>();
    for (Activity activity : vertices) {
      var children =
          activityGraph.outgoingEdgesOf(activity).stream()
              .map(activityGraph::getEdgeTarget)
              .toList();
      activityChildren.put(activity, children);
    }

    Platform.runLater(
        () -> {
          treeTableView.getRoot().getChildren().clear();
          for (Activity activity : rootActivities) {
            treeTableView.getRoot().getChildren().add(makeItem(activity, activityChildren));
          }
          // Refresh all columns to ensure proper cell rendering - otherwise the columns that were
          // previously visible won't change.
          treeTableView
              .getColumns()
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

  @Override
  public void scheduleModified(Schedule schedule) {}

  @Override
  public void cleanup() {}
}

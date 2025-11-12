package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Breadcrumbs;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * Controlled by the DT peer installed in the main IDE controller. Differently from other DT views,
 * this one exists independently of any DT and is installed in all main views; the DT can be
 * swapped. All other views are dedicated to an individual DT, so the handling must be customized.
 *
 * <p>IDEA: top menu has DT choice (MenuButton) + DT switch to main view button (->) + Observer
 * label + Observer choice (MenuButton)
 *
 * <p>TODO add view types and toggles in menu bar. Use DT icon for the full view. Link
 * info/warn/error from local service to activities.
 *
 * <p>Upper bar: switch between activities, knowledge graph, schedule, observers + Current context
 * or "Root"; on right, log toggle (for activities or other) and stats (time in existence etc. in
 * tooltip)
 *
 * <p>Below bar: DT icon to switch to full view + DT name and current observer; on right, connected
 * scopes and delete button
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
  //  private final Label statusLabel;
  private final HBox topBar;
  private final HBox bottomBar;
  private final MenuButton digitalTwinSwitcher;
  private final Button resetButton;
  private final Button activitiesButton;
  private final Button scenarioButton;
  private final Button observationButton;
  private final Button observerButton;
  private final Breadcrumbs<Observation> contextPath;
  private IDEContextScope scope;

  public enum Status {
    IDLE,
    COMPUTING,
    ERROR,
    RECEIVING,
    INFO
  }

  public enum View {
    ACTIVITIES,
    OBSERVATIONS,
    SCHEDULE,
    KNOWLEDGE_GRAPH,
    LOGS,
    IDLE;
  }

  private Pane dropZone;
  private Status status = Status.IDLE;
  private TreeTableView<Activity> activityTree;
  private View currentView = View.ACTIVITIES;

  public DigitalTwinControlPanel(int size, EditorPage<?, ?> editorPage) {

    super();

    setMinHeight(size);
    setMinWidth(size);

    // Create top control bar
    this.topBar = new HBox(0);
    topBar.setPrefHeight(20);
    topBar.setAlignment(Pos.CENTER_LEFT);
    topBar.setStyle("-fx-background-color: #E0E0E0;");

    this.activitiesButton = new Button("", new IconLabel(Theme.ACTIVITY_ICON, 14, Color.DARKGRAY));
    this.observationButton =
        new Button("", new IconLabel(Theme.OBSERVATION_ICON, 14, Color.DARKGRAY));
    this.observerButton = new Button("", new IconLabel(Theme.OBSERVER_ICON, 14, Color.DARKGRAY));
    this.scenarioButton = new Button("", new IconLabel(Theme.SCENARIO_ICON, 14, Color.DARKGRAY));
    this.resetButton =
        new Button("", new IconLabel(Material2AL.DELETE_FOREVER, 18, Color.DARKGRAY));

    resetButton.setOnAction(e -> editorPage.deleteScope(scope));

    activitiesButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
    observationButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
    observerButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
    scenarioButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
    resetButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);

    var contextSelector = new HBox(0);
    contextSelector.setAlignment(Pos.CENTER_LEFT);
    var homeButton = new Button("", new IconLabel(Material2AL.HOME, 14, Color.BLACK));
    homeButton.setOnAction(e -> scope.within(null));
    homeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);

    this.contextPath = new Breadcrumbs<>();
    contextPath.setDividerFactory(
        item -> {
          return item != null && !item.isLast() && !item.getChildren().isEmpty()
              ? new IconLabel(Material2AL.CHEVRON_RIGHT, 12, Color.DARKGRAY)
              : null;
        });
    contextPath.setCrumbFactory(
        observation ->
            new Hyperlink(observation == null ? "DT Home" : observation.getValue().getName()));

    HBox.setMargin(contextSelector, new Insets(5, 5, 5, 5));
    contextSelector.setStyle(
        "-fx-border-color: #CCCCCC; -fx-border-width: 2px; -fx-border-radius: 3px;");

    contextSelector.getChildren().addAll(homeButton, contextPath);

    HBox.setHgrow(contextSelector, Priority.ALWAYS);
    contextSelector.setMaxWidth(Double.MAX_VALUE);

    this.progressIndicator = new ProgressIndicator(0);
    progressIndicator.setPrefSize(14, 14);
    progressIndicator.setMaxSize(14, 14);
    progressIndicator.setMinSize(14, 14);

    topBar
        .getChildren()
        .addAll(
            activitiesButton,
            observationButton,
            scenarioButton,
            observerButton,
            contextSelector,
            progressIndicator,
            resetButton);

    setTop(topBar);

    activityTree = new TreeTableView<>();
    activityTree.setMinSize(220, 220);
    activityTree.setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    activityTree.getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    activityTree.setShowRoot(false);

    TreeTableColumn<Activity, String> descriptionColumn = new TreeTableColumn<>("Description");
    descriptionColumn.prefWidthProperty().bind(activityTree.widthProperty().subtract(32));
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

    activityTree.getColumns().setAll(descriptionColumn, statusColumn);
    activityTree.setRoot(new TreeItem<>());
    activityTree.setShowRoot(false);

    dropZone = new Pane();
    dropZone.setMinSize(220, 220);
    dropZone.setMaxSize(220, 220);
    dropZone.setStyle(
        "-fx-background-color: #F5F5F5; -fx-border-color: grey; -fx-border-width: 5; -fx-border-style: dashed; -fx-border-radius: 10;");
    setCenter(activityTree);

    // Create bottom control bar for scenarios
    this.bottomBar = new HBox(10);
    bottomBar.setPrefHeight(20);
    bottomBar.setStyle("-fx-background-color: #E0E0E0;");

    Button swapButton = new Button();
    swapButton.setGraphic(new FontIcon(Theme.DIGITAL_TWINS_ICON));
    swapButton.setOnAction(
        e -> {
          if (getScope() != null) {
            KlabIDEController.instance()
                .getView(KlabIDEController.View.DIGITAL_TWINS, DigitalTwinView.class)
                .showDigitalTwin(getScope());
            KlabIDEController.instance().selectView(KlabIDEController.View.DIGITAL_TWINS);
          }
        });
    swapButton.getStyleClass().addAll(Styles.FLAT /*, Styles.BUTTON_CIRCLE*/);

    this.digitalTwinSwitcher = new MenuButton();
    this.digitalTwinSwitcher.getStyleClass().addAll(Styles.FLAT);
    HBox.setHgrow(digitalTwinSwitcher, Priority.ALWAYS);
    digitalTwinSwitcher.setMaxWidth(Double.MAX_VALUE);

    // Function buttons
    Button collapseButton = new Button();
    collapseButton.setGraphic(new FontIcon(Material2AL.ARROW_DOWNWARD));
    collapseButton.setOnAction(e -> editorPage.hideDigitalTwinControlPanel());
    collapseButton.getStyleClass().addAll(Styles.FLAT /*, Styles.BUTTON_CIRCLE*/);
    if (editorPage instanceof DigitalTwinEditor) {
      bottomBar.getChildren().addAll(digitalTwinSwitcher, collapseButton);
    } else {
      bottomBar.getChildren().addAll(swapButton, digitalTwinSwitcher, collapseButton);
    }
    setBottom(bottomBar);
  }

  HBox setControlBar() {
    return (HBox) getTop();
  }

  private void loadScope(IDEContextScope scope) {
    // TODO obviously this isn't right
    this.digitalTwinSwitcher.setText(scope == null ? "" : scope.getName());
    //    this.digitalTwinSwitcher.getItems().add(new MenuItem(scope.getName()));
    this.scope = scope;
    if (scope != null) {
      scope.addViewer(this);
      if (!scope.getActivityGraph().vertexSet().isEmpty()) {
        activitiesModified();
      }
    }
    Platform.runLater(this::setMainView);
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
              progressIndicator.setProgress(0);
              setMainView();
            }
            case RECEIVING -> {
              setCenter(dropZone);
            }
            case COMPUTING -> {
              setMainView();
              progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            }
          }
        });
  }

  private void setMainView() {

    this.activitiesButton.setGraphic(
        new IconLabel(Theme.ACTIVITY_ICON, 14, scope == null ? Color.DARKGRAY : Color.BLACK));
    this.observationButton.setGraphic(
        new IconLabel(Theme.OBSERVATION_ICON, 14, scope == null ? Color.DARKGRAY : Color.BLACK));
    this.observerButton.setGraphic(
        new IconLabel(Theme.OBSERVER_ICON, 14, scope == null ? Color.DARKGRAY : Color.BLACK));
    this.scenarioButton.setGraphic(
        new IconLabel(Theme.SCENARIO_ICON, 14, scope == null ? Color.DARKGRAY : Color.BLACK));
    this.resetButton.setGraphic(
        new IconLabel(
            Material2AL.DELETE_FOREVER, 18, scope == null ? Color.DARKGRAY : Color.DARKRED));

    if (scope != null) {
      switch (currentView) {
        case ACTIVITIES -> {
          this.activitiesButton.setGraphic(new IconLabel(Theme.ACTIVITY_ICON, 14, Color.DARKGREEN));
          setCenter(activityTree);
        }
        case OBSERVATIONS -> {
          this.observationButton.setGraphic(
              new IconLabel(Theme.OBSERVATION_ICON, 14, Color.DARKGRAY));
        }
        case SCHEDULE -> {}
        case KNOWLEDGE_GRAPH -> {}
        case LOGS -> {}
        case IDLE -> {}
      }
    } else {
      setCenter(null);
      this.contextPath.setSelectedCrumb(null);
    }
  }

  @Override
  public void setDigitalTwin(IDEContextScope scope, boolean focus) {
    close();
    loadScope(scope);
  }

  @Override
  public void close() {
    if (this.scope != null) {
      scope.removeViewer(this);
    }
  }

  @Override
  public void closeDigitalTwin(IDEContextScope ideContextScope) {
    this.scope = null;
    this.digitalTwinSwitcher.setText("");
    this.status = Status.IDLE;
    this.setMainView();
  }

  public IDEContextScope getScope() {
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
    Platform.runLater(
        () -> {
          var path = scope.getContextPath();
          if (path.isEmpty()) {
            this.contextPath.setSelectedCrumb(null);
            return;
          }
          var root = new Breadcrumbs.BreadCrumbItem<>(path.getFirst());
          for (int i = 1; i < path.size(); i++) {
            var child = new Breadcrumbs.BreadCrumbItem<>(path.get(i));
            root.getChildren().add(child);
            root = child;
          }
          this.contextPath.setSelectedCrumb(root);
        });
  }

  @Override
  public void setObserver(Observation observation) {}

  @Override
  public void knowledgeGraphModified() {}

  @Override
  public void activitiesModified() {

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
          activityTree.getRoot().getChildren().clear();
          for (Activity activity : rootActivities) {
            activityTree.getRoot().getChildren().add(makeItem(activity, activityChildren));
          }
          // Refresh all columns to ensure proper cell rendering - otherwise the columns that were
          // previously visible won't change.
          activityTree
              .getColumns()
              .forEach(
                  column -> {
                    column.setVisible(false);
                    column.setVisible(true);
                  });
        });
  }

  @Override
  public void focusObservations(List<RuntimeAsset> ids) {}

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
  public boolean isAffectedBy(IDEContextScope scope) {
    return this.scope.getId().equals(scope.getId());
  }

  @Override
  public void scheduleModified(Schedule schedule) {}

  @Override
  public void cleanup() {}
}

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
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.treeviews.ActivityTree;
import org.integratedmodelling.klab.ide.components.treeviews.ObservationTree;
import org.integratedmodelling.klab.ide.components.treeviews.ObserverTree;
import org.integratedmodelling.klab.ide.components.treeviews.ScenarioTree;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * Controlled by the current DT peer installed in the main IDE controller. Differently from other DT
 * views, this one is installed in all {@link EditorPage}s, although it may not be in view. Its
 * visibility is controlled for the current editor page using the UI status bar. The current DT can
 * be swapped, which causes all instances of the DT control panel to be refocused.
 *
 * <p>Center area shows content of the DT according to the selected viee in IDLE or COMPUTING state,
 * and displays the "drop here" arrow when in RECEIVING mode (normally during a drag of a suitable
 * resolvable from the associated editor page).
 */
public class DigitalTwinControlPanel extends BorderPane implements DigitalTwinViewer {

  private final ProgressIndicator progressIndicator;
  private final HBox topBar;
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
    OBSERVERS,
    SCENARIOS,
    IDLE;
  }

  private Pane dropZone;
  private Status status = Status.IDLE;
  private ActivityTree activityTree;
  private ObservationTree observationTree;
  private ScenarioTree scenarioTree;
  private ObserverTree observerTree;

  private View currentView = View.ACTIVITIES;

  public DigitalTwinControlPanel(int size, EditorPage<?, ?> editorPage) {

    super();

    setMinHeight(size);
    setMinWidth(size);

    // Create top control bar
    this.topBar = new HBox(0);
    topBar.setPrefHeight(20);
    topBar.setAlignment(Pos.CENTER_LEFT);
    topBar.setStyle("-fx-background-color: -color-neutral-muted;");

    this.activitiesButton = new Button("", new IconLabel(Theme.ACTIVITY_ICON, 14, Color.DARKGRAY));
    this.observationButton =
        new Button("", new IconLabel(Theme.KNOWLEDGE_GRAPH_ICON, 14, Color.DARKGRAY));
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

    activitiesButton.setOnAction(click -> setView(View.ACTIVITIES));
    observationButton.setOnAction(click -> setView(View.OBSERVATIONS));
    observerButton.setOnAction(click -> setView(View.OBSERVERS));
    scenarioButton.setOnAction(click -> setView(View.SCENARIOS));

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

    activityTree = new ActivityTree();
    observationTree = new ObservationTree();
    scenarioTree = new ScenarioTree();
    observerTree = new ObserverTree();

    activityTree.setMinSize(220, 220);

    dropZone = new Pane();
    dropZone.setMinSize(220, 220);
    dropZone.setMaxSize(220, 220);
    dropZone.setStyle(
        "-fx-background-color: #F5F5F5; -fx-border-color: grey; -fx-border-width: 5; -fx-border-style: dashed; -fx-border-radius: 10;");
    setCenter(null); // TODO use some idle view
  }

  private void setView(View view) {
    if (this.currentView == view) return;
    this.currentView = view;
    this.setMainView();
  }

  private void loadScope(IDEContextScope scope) {
    this.scope = scope;
    if (scope != null) {
      scope.addViewer(this);
      if (!scope.getActivityGraph().vertexSet().isEmpty()) {
        activityTree.update(scope);
      }
    }
    Platform.runLater(this::setMainView);
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

    if (scope == null) {
      setCenter(null); // TODO use some idle view
      this.contextPath.setSelectedCrumb(null);
    } else {
      this.setCenter(
          switch (currentView) {
            case ACTIVITIES -> activityTree;
            case OBSERVATIONS -> observationTree;
            case OBSERVERS -> observerTree;
            case SCENARIOS -> scenarioTree;
            case IDLE -> null;
          });
    }

    this.activitiesButton.setGraphic(
        new IconLabel(
            Theme.ACTIVITY_ICON,
            14,
            scope == null
                ? Color.DARKGRAY
                : (currentView == View.ACTIVITIES ? Color.DARKGREEN : Color.BLACK)));
    this.observationButton.setGraphic(
        new IconLabel(
            Theme.KNOWLEDGE_GRAPH_ICON,
            14,
            scope == null
                ? Color.DARKGRAY
                : (currentView == View.OBSERVATIONS ? Color.DARKGREEN : Color.BLACK)));
    this.observerButton.setGraphic(
        new IconLabel(
            Theme.OBSERVER_ICON,
            14,
            scope == null
                ? Color.DARKGRAY
                : (currentView == View.OBSERVERS ? Color.DARKGREEN : Color.BLACK)));
    this.scenarioButton.setGraphic(
        new IconLabel(
            Theme.SCENARIO_ICON,
            14,
            scope == null
                ? Color.DARKGRAY
                : (currentView == View.SCENARIOS ? Color.DARKGREEN : Color.BLACK)));
    this.resetButton.setGraphic(
        new IconLabel(
            Material2AL.DELETE_FOREVER, 18, scope == null ? Color.DARKGRAY : Color.DARKRED));

    outlineButton(this.activitiesButton, currentView == View.ACTIVITIES);
    outlineButton(this.observationButton, currentView == View.OBSERVATIONS);
    outlineButton(this.observerButton, currentView == View.OBSERVERS);
    outlineButton(this.scenarioButton, currentView == View.SCENARIOS);
  }

  private void outlineButton(Button button, boolean b) {
    if (scope == null) {
      button.getStyleClass().removeAll(Styles.BUTTON_OUTLINED);
      button.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
      button.setDisable(true);
    } else {
      button.setDisable(false);
      if (b) {
        button.getStyleClass().removeAll(Styles.FLAT);
        button.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.BUTTON_OUTLINED);
      } else {
        button.getStyleClass().removeAll(Styles.BUTTON_OUTLINED);
        button.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
      }
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
    if (this.scope != null && this.scope.getId().equals(ideContextScope.getId())) {
      this.scope = null;
      this.status = Status.IDLE;
      this.setMainView();
    }
  }

  @Override
  public void unsetDigitalTwin(IDEContextScope focalScope) {
    if (this.scope != null && this.scope.getId().equals(focalScope.getId())) {
      this.scope = null;
      this.status = Status.IDLE;
      this.setMainView();
    }
  }

  public IDEContextScope getScope() {
    return this.scope;
  }

  @Override
  public void submissionStarted(Observation observation) {
    Platform.runLater(
        () -> {
          setView(View.ACTIVITIES);
        });
  }

  @Override
  public void submissionAborted(Observation observation) {
    // TODO show some error message temporarily
  }

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
    activityTree.update(scope);
  }

  @Override
  public void focusObservations(List<RuntimeAsset> ids) {

    Platform.runLater(
        () -> {
          observationTree.update(scope, (Observation) ids.getFirst());
          setView(View.OBSERVATIONS);
        });
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

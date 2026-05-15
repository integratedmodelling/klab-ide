package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.Switcher;
import org.integratedmodelling.klab.ide.components.generic.TreeSearchField;
import org.integratedmodelling.klab.ide.components.treeviews.ActivityTree;
import org.integratedmodelling.klab.ide.components.treeviews.ObservationTree;
import org.integratedmodelling.klab.ide.components.treeviews.ObserverTree;
import org.integratedmodelling.klab.ide.components.treeviews.ScenarioTree;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
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
  private final Button homeButton;
  private final Switcher searchArea;
  private final TreeSearchField<Activity> activitySearch;
  private final TreeSearchField<RuntimeAsset> observationSearch;
  private final TreeSearchField<Observation> observerSearch;
  private final TreeSearchField<KimNamespace> scenarioSearch;

  @Deprecated
  // FIXME don't copy, carry over from the modeler
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
    topBar.setPrefHeight(38);
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

    var aTooltip = new Tooltip("Activities");
    var oTooltip = new Tooltip("Observations");
    var sTooltip = new Tooltip("Scenarios");
    var bTooltip = new Tooltip("Observers");

    aTooltip.setShowDelay(Duration.millis(200));
    oTooltip.setShowDelay(Duration.millis(200));
    sTooltip.setShowDelay(Duration.millis(200));
    bTooltip.setShowDelay(Duration.millis(200));

    activitiesButton.setTooltip(aTooltip);
    observationButton.setTooltip(oTooltip);
    observerButton.setTooltip(bTooltip);
    scenarioButton.setTooltip(sTooltip);

    activityTree = new ActivityTree();
    observationTree = new ObservationTree();
    scenarioTree = new ScenarioTree();
    observerTree = new ObserverTree();

    this.activitySearch = new TreeSearchField<>(activityTree, activityTree::matches);
    this.observationSearch = new TreeSearchField<>(observationTree, observationTree::matches);
    this.scenarioSearch = new TreeSearchField<>(scenarioTree, scenarioTree::matches);
    this.observerSearch = new TreeSearchField<>(observerTree, observerTree::matches);

    this.searchArea =
        new Switcher(
            Map.of(
                "activities",
                activitySearch,
                "observations",
                observationSearch,
                "scenarios",
                scenarioSearch,
                "observers",
                observerSearch));

    searchArea.show("activities");

    this.homeButton = new Button("", new IconLabel(Material2AL.HOME, 14, Color.BLACK));
    homeButton.setOnAction(e -> contextScopeAction());
    homeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);

    homeButton.setOnContextMenuRequested(e -> showContextScopeMenu(e.getScreenX(), e.getScreenY()));

    var hbTooltip =
        new Tooltip("Click to bring the whole graph into view. Right-click to recontextualize.");
    hbTooltip.setShowDelay(Duration.millis(200));
    homeButton.setTooltip(hbTooltip);

    var conceptButton = new Button("", new IconLabel(Theme.WORLDVIEW_ICON, 14, Color.BLACK));
    conceptButton.setOnAction(
        e -> {
          var button = new Button("Dio è un MAIALE. Click to agree");
          button.setOnAction(ex -> KlabIDEController.instance().removeModalOverlay());
          KlabIDEController.instance().showInModalOverlay(button);
        });
    conceptButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
    HBox.setHgrow(searchArea, Priority.ALWAYS);
    HBox.setMargin(searchArea, new Insets(5, 0, 0, 0));

    this.progressIndicator = new ProgressIndicator(0);
    progressIndicator.setPrefSize(12, 12);
    progressIndicator.setMaxSize(12, 12);
    progressIndicator.setMinSize(12, 12);
    progressIndicator.setManaged(false);
    topBar
        .getChildren()
        .addAll(
            activitiesButton,
            observationButton,
            scenarioButton,
            observerButton,
            searchArea,
            progressIndicator,
            homeButton,
            conceptButton,
            resetButton);

    setTop(topBar);

    activityTree.setMinSize(220, 220);

    dropZone = new Pane();
    dropZone.setMinSize(220, 220);
    dropZone.setMaxSize(220, 220);
    dropZone.setStyle(
        "-fx-background-color: #F5F5F5; -fx-border-color: grey; -fx-border-width: 5; -fx-border-style: dashed; -fx-border-radius: 10;");
    setCenter(null); // TODO use some idle view
  }

  /** Action for the home button, which resets the graph to the root of the KG */
  private void contextScopeAction() {
    observationTree.update(RuntimeAsset.CONTEXT_ASSET, null, scope);
  }

  private void showContextScopeMenu(double screenX, double screenY) {
    var homeContextMenu = new ContextMenu();
    homeContextMenu.setStyle("-fx-font-size: 11px;");
    if (scope != null && scope.getContextObservation() != null) {
      var homeMenuItem1 = new MenuItem("Reset the context observation");
      homeMenuItem1.setOnAction(
          e -> {
            scope.within(null);
            observationTree.refresh();
          });
      homeContextMenu.getItems().add(homeMenuItem1);
    }

    if (scope != null) {
      for (var commit : scope.getCommits()) {
        var homeMenuItem2 = new MenuItem(Theme.getLabel(commit.getFirst()));
        homeMenuItem2.setOnAction(
            e -> {
              observationTree.update(commit.getFirst(), null, scope);
            });
        homeContextMenu.getItems().addAll(homeMenuItem2);
      }
    }

    if (!homeContextMenu.getItems().isEmpty()) {
      homeContextMenu.show(homeButton, screenX, screenY);
    }
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
              homeButton.setManaged(true);
              homeButton.setVisible(true);
              progressIndicator.setManaged(false);
              progressIndicator.setVisible(false);
              progressIndicator.setProgress(0);
              setMainView();
            }
            case RECEIVING -> {
              setCenter(dropZone);
            }
            case COMPUTING -> {
              setMainView();
              setView(View.ACTIVITIES);
              homeButton.setManaged(false);
              homeButton.setVisible(false);
              progressIndicator.setManaged(true);
              progressIndicator.setVisible(true);
              progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            }
          }
        });
  }

  private void setMainView() {

    if (scope == null) {
      setCenter(null); // TODO use some idle view
      //      this.contextPath.setSelectedCrumb(null);
    } else {
      this.setCenter(
          switch (currentView) {
            case ACTIVITIES -> activityTree;
            case OBSERVATIONS -> observationTree;
            case OBSERVERS -> observerTree;
            case SCENARIOS -> scenarioTree;
            case IDLE -> null;
          });
      this.searchArea.show(
          switch (currentView) {
            case ACTIVITIES -> "activities";
            case OBSERVATIONS -> "observations";
            case OBSERVERS -> "observers";
            case SCENARIOS -> "scenarios";
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
    this.homeButton.setGraphic(
        new IconLabel(
            (scope == null || scope.getContextObservation() == null)
                ? Material2AL.HOME
                : FontAwesomeSolid.HOME,
            (scope == null || scope.getContextObservation() == null) ? 16 : 14,
            (scope == null
                ? Color.DARKGRAY
                : (scope.getContextObservation() == null
                    ? Color.BLACK
                    : Theme.getColorForType(
                        SemanticType.fundamentalType(
                            scope
                                .getContextObservation()
                                .getObservable()
                                .getSemantics()
                                .getType()))))));

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
    reset();
    if (this.scope != null) {
      scope.removeViewer(this);
    }
  }

  @Override
  public void closeDigitalTwin(IDEContextScope ideContextScope) {
    if (this.scope != null && this.scope.getId().equals(ideContextScope.getId())) {
      reset();
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
  public void submissionFinished(Observation observation) {
    var root =
        observation.getMetadata().containsKey(Metadata.IM_COMMIT)
            ? observation.getMetadata().get(Metadata.IM_COMMIT, KnowledgeGraph.Commit.class)
            : RuntimeAsset.CONTEXT_ASSET;
    Platform.runLater(
        () -> {
          observationTree.update(root, observation, scope);
          setView(View.OBSERVATIONS);
        });
  }

  public void reset() {
    Platform.runLater(
        () -> {
          if (scope == null) {
            observationTree.reset();
            activityTree.reset();
            scenarioTree.reset();
            observerTree.reset();
          } else {
            observationTree.update(
                RuntimeAsset.CONTEXT_ASSET, scope.getContextObservation(), scope);
            activityTree.reset();
            // TODO fill up scenarios and observers
          }
        });
  }

  @Override
  public void setContext(Observation observation) {
    Platform.runLater(
        () -> {
          homeButton.setGraphic(
              observation == null
                  ? new IconLabel(Material2AL.HOME, 16, Color.BLACK)
                  : new IconLabel(
                      FontAwesomeSolid.HOME,
                      14,
                      Theme.getColorForType(
                          SemanticType.fundamentalType(
                              observation.getObservable().getSemantics().getType()))));
          var tooltip =
              new Tooltip(
                  observation == null
                      ? "No context observation set"
                      : "Context observation set to " + Theme.getLabel(observation));
          tooltip.setShowDelay(Duration.millis(200));
          homeButton.setTooltip(tooltip);
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
  public boolean isAffectedBy(IDEContextScope scope) {
    return this.scope.getId().equals(scope.getId());
  }

  @Override
  public void scheduleModified(Schedule schedule) {}

  @Override
  public void cleanup() {}
}

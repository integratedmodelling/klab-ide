package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.IntegerStringConverter;
import com.brunomnsilva.smartgraph.graph.DigraphEdgeList;
import com.brunomnsilva.smartgraph.graphview.ForceDirectedSpringGravityLayoutStrategy;
import com.brunomnsilva.smartgraph.graphview.SmartCircularSortedPlacementStrategy;
import com.brunomnsilva.smartgraph.graphview.SmartGraphPanel;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.integratedmodelling.cli.Test;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.components.generic.Timeline;
import org.integratedmodelling.klab.ide.components.treeviews.TreeModel;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

public class KnowledgeGraphView extends BorderPane implements DigitalTwinViewer {

  private final ClientKnowledgeGraph knowledgeGraph;
  private final IDEContextScope scope;
  private final DigitalTwinEditor editor;
  private final MenuButton homeButton;
  private SmartGraphPanel<RuntimeAsset, ClientKnowledgeGraph.Relationship> graphView;
  private Set<GraphModel.Relationship> visibleRelationships =
      EnumSet.of(GraphModel.Relationship.HAS_CHILD, GraphModel.Relationship.HAS_MEMBER);
  private Set<RuntimeAsset.Type> visibleTypes =
      EnumSet.of(RuntimeAsset.Type.OBSERVATION, RuntimeAsset.Type.COHORT);

  // Queue to store pending updates until the graph is ready
  private List<RuntimeAsset> pendingFocalAssets = null;

  private volatile boolean initialized = false;
  private volatile boolean graphViewReady = false;
  private Timeline timeline;
  // Optional callback invoked when this view is brought back into view/focus
  private Runnable onBroughtIntoView;
  private AtomicBoolean changePending = new AtomicBoolean(false);

  public KnowledgeGraphView(
      ContextScope contextScope, ClientKnowledgeGraph knowledgeGraph, DigitalTwinEditor editor) {

    this.scope = KlabIDEController.instance().requireDigitalTwinPeer(contextScope, this);
    this.knowledgeGraph = knowledgeGraph;
    this.editor = editor;

    HBox controls = new HBox(2);
    controls.getStyleClass().add(Styles.SMALL);
    controls.setStyle("-fx-padding: 5px;");

    HBox switchesBox = new HBox(2);
    switchesBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
    switchesBox.getStyleClass().add(Styles.SMALL);

    // TODO use toggle buttons with icons
    ToggleSwitch affectedSwitch = new ToggleSwitch("Affected");
    ToggleSwitch dataSwitch = new ToggleSwitch("Data");
    ToggleSwitch activitiesSwitch = new ToggleSwitch("Activities");
    ToggleSwitch actuatorsSwitch = new ToggleSwitch("Actuators");
    ToggleSwitch cohortsSwitch = new ToggleSwitch("Cohorts");

    affectedSwitch.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    dataSwitch.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    activitiesSwitch.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    actuatorsSwitch.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    cohortsSwitch.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    cohortsSwitch.setSelected(true);

    this.homeButton = new MenuButton();
    homeButton.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
    homeButton.setText("Knowledge graph");
    homeButton.setGraphic(new FontIcon(Material2AL.HOME));

    MenuItem homeMenuItem = new MenuItem("Knowledge graph", new FontIcon(Material2AL.HOME));
    homeButton.getItems().add(homeMenuItem);
    homeMenuItem.setOnAction(
        event -> {
          homeButton.setText("Knowledge graph");
          homeButton.setGraphic(new FontIcon(Material2AL.HOME));
          scope.setFocalAssets(RuntimeAsset.CONTEXT_ASSET, null);
          editor.getKnowledgeTree().update(RuntimeAsset.CONTEXT_ASSET, null);
          graphView.setAutomaticLayout(true);
          updateGraphSafely();
        });

    Spinner<Integer> spinner = new Spinner<>(1, 5, 2);
    spinner.setPrefWidth(100);
    IntegerStringConverter.createFor(spinner);
    spinner.setEditable(false);
    spinner
        .getStyleClass()
        .addAll(
            Styles.TEXT_SMALL,
            Styles.SMALL,
            Styles.FLAT,
            Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);

    Button redrawButton = new Button();
    redrawButton.setGraphic(new FontIcon(Material2AL.AUTORENEW));
    redrawButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT, Styles.SMALL);

    spinner
        .valueProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              scope.setGraphDepth(newValue);
              graphView.setAutomaticLayout(true);
              updateGraphSafely();
            });
    redrawButton.setOnAction(
        event -> {
          redrawGraph();
        });
    affectedSwitch
        .selectedProperty()
        .addListener(
            (obs, old, val) -> {
              if (obs.getValue()) {
                visibleRelationships.add(GraphModel.Relationship.AFFECTS);
              } else {
                visibleRelationships.remove(GraphModel.Relationship.AFFECTS);
              }
              graphView.setAutomaticLayout(true);
              updateGraphSafely();
            });
    dataSwitch
        .selectedProperty()
        .addListener(
            (obs, old, val) -> {
              if (obs.getValue()) {
                visibleTypes.add(RuntimeAsset.Type.DATA);
                visibleRelationships.add(GraphModel.Relationship.HAS_DATA);
              } else {
                visibleTypes.add(RuntimeAsset.Type.DATA);
                visibleRelationships.remove(GraphModel.Relationship.HAS_DATA);
              }
              graphView.setAutomaticLayout(true);
              updateGraphSafely();
            });
    activitiesSwitch
        .selectedProperty()
        .addListener(
            (obs, old, val) -> {
              if (obs.getValue()) {
                visibleTypes.add(RuntimeAsset.Type.ACTIVITY);
                visibleTypes.add(RuntimeAsset.Type.PROVENANCE);
                visibleRelationships.add(GraphModel.Relationship.HAS_ACTIVITY);
                visibleRelationships.add(GraphModel.Relationship.TRIGGERED);
                visibleRelationships.add(GraphModel.Relationship.HAS_PROVENANCE);
              } else {
                visibleTypes.remove(RuntimeAsset.Type.ACTIVITY);
                visibleTypes.remove(RuntimeAsset.Type.PROVENANCE);
                visibleRelationships.remove(GraphModel.Relationship.HAS_ACTIVITY);
                visibleRelationships.remove(GraphModel.Relationship.TRIGGERED);
                visibleRelationships.remove(GraphModel.Relationship.HAS_PROVENANCE);
              }
              graphView.setAutomaticLayout(true);
              updateGraphSafely();
            });
    actuatorsSwitch
        .selectedProperty()
        .addListener(
            (obs, old, val) -> {
              if (obs.getValue()) {
                visibleTypes.add(RuntimeAsset.Type.DATAFLOW);
                visibleTypes.add(RuntimeAsset.Type.ACTUATOR);
                visibleRelationships.add(GraphModel.Relationship.HAS_PLAN);
                visibleRelationships.add(GraphModel.Relationship.HAS_DATAFLOW);
              } else {
                visibleTypes.remove(RuntimeAsset.Type.DATAFLOW);
                visibleTypes.remove(RuntimeAsset.Type.ACTUATOR);
                visibleRelationships.remove(GraphModel.Relationship.HAS_PLAN);
                visibleRelationships.remove(GraphModel.Relationship.HAS_DATAFLOW);
              }
              graphView.setAutomaticLayout(true);
              updateGraphSafely();
            });
    cohortsSwitch
        .selectedProperty()
        .addListener(
            (obs, old, val) -> {
              if (obs.getValue()) {
                visibleRelationships.add(GraphModel.Relationship.CREATED);
                visibleTypes.add(RuntimeAsset.Type.COHORT);
              } else {
                visibleRelationships.remove(GraphModel.Relationship.CREATED);
                visibleTypes.remove(RuntimeAsset.Type.COHORT);
              }
              graphView.setAutomaticLayout(true);
              updateGraphSafely();
            });

    switchesBox
        .getChildren()
        .addAll(affectedSwitch, dataSwitch, activitiesSwitch, actuatorsSwitch, cohortsSwitch);
    HBox spinnerBox = new HBox(homeButton, spinner, redrawButton);
    HBox.setHgrow(spinnerBox, javafx.scene.layout.Priority.ALWAYS);
    controls.getChildren().addAll(spinnerBox, switchesBox);
    this.setTop(controls);

    // Fire callback when this Node becomes visible again
    this.visibleProperty()
        .addListener(
            (obs, wasVisible, isNowVisible) -> {
              if (isNowVisible) {
                invokeBroughtIntoViewCallback();
              }
            });

    onBroughtIntoView =
        () -> {
          if (this.changePending.get()) {
            changePending.set(false);
            redrawGraph();
          }
        };
  }

  private void redrawGraph() {
    if (isGraphViewReady()) {
      for (int i = 0; i < 1; i++) {
        //        autoLayout = !autoLayout;
        updateGraph();
        graphView.setAutomaticLayout(true);
      }
    } else {
      // TODO enqueue event for when graph comes into view
    }
  }

  @Override
  public boolean isAffectedBy(IDEContextScope scope) {
    return this.scope.getId().equals(scope.getId());
  }

  private void initializeGraphView() {
    // Check if the component has valid dimensions before initializing
    if (getWidth() > 0 && getHeight() > 0 && !initialized) {
      Logging.INSTANCE.info("Initializing Knowledge Graph View");
      var initialPlacement = new SmartCircularSortedPlacementStrategy();
      var graph = new DigraphEdgeList<RuntimeAsset, ClientKnowledgeGraph.Relationship>();
      this.graphView = new SmartGraphPanel<>(graph, initialPlacement);
      this.setCenter(this.graphView);
      this.graphView.setAutomaticLayoutStrategy(new ForceDirectedSpringGravityLayoutStrategy<>());
      this.graphView.setAutomaticLayout(true);

      graphView.setVertexDoubleClickAction(
          graphVertex -> {
            var asset = graphVertex.getUnderlyingVertex().element();
            if (asset instanceof Asset wrapper) {
              asset = wrapper.getDelegate();
            }
            //            this.editor.selectAsset(asset);
            this.scope.setFocalAssets(this.scope.getFocalRoot(), asset);
          });

      graphView.setEdgeDoubleClickAction(
          graphEdge -> {
            Logging.INSTANCE.info(
                "Edge contains element: " + graphEdge.getUnderlyingEdge().element());
            // dynamically change the style, can also be done for a vertex
            graphEdge.setStyleInline("-fx-stroke: black; -fx-stroke-width: 2;");
          });

      // Create default start and end times (current time and 1 hour later)
      long currentTimeMs = System.currentTimeMillis();
      long oneHourLaterMs = currentTimeMs + (3600000 * 2); // 2 hours in milliseconds
      // Create the timeline component
      timeline = new Timeline(currentTimeMs, oneHourLaterMs, TimeUnit.MINUTES, 1);
      this.setBottom(timeline);
      timeline.setVisible(true);

      // Initialize the graph view after it's been added to the scene
      Platform.runLater(
          () -> {
            try {
              if (graphView.getParent() != null && graphView.getScene() != null) {
                graphView.init();
                this.initialized = true;
                this.graphViewReady = true;

                // Process any pending focal asset update
                if (pendingFocalAssets != null) {
                  pendingFocalAssets = null;
                } else if (scope.getFocalAsset() != null) {
                  updateGraph();
                }
              } else {
                // Still not ready, try again
                Platform.runLater(() -> initializeGraphView());
              }
            } catch (IllegalStateException e) {
              Logging.INSTANCE.warn(
                  "Graph view initialization failed, retrying: " + e.getMessage());
              // If still not ready, try again after another layout pass
              try {
                Thread.sleep(300);
              } catch (InterruptedException ex) {
                // fock
              }
              Platform.runLater(
                  () -> {
                    if (graphView.getWidth() > 0
                        && graphView.getHeight() > 0
                        && graphView.getParent() != null
                        && graphView.getScene() != null) {
                      try {
                        graphView.init();
                        this.initialized = true;
                        this.setGraphViewReady(true);

                        // Process any pending focal asset update
                        if (pendingFocalAssets != null) {
                          updateGraph();
                          pendingFocalAssets = null;
                        } else if (scope.getFocalAsset() != null) {
                          updateGraph();
                        }
                      } catch (IllegalStateException ex) {
                        Logging.INSTANCE.error("Failed to initialize graph view after retry", ex);
                      }
                    }
                  });
            }
          });
    } else if (!initialized) {
      // If dimensions are still not available, try again on the next pulse
      Platform.runLater(() -> initializeGraphView());
    }
  }

  public void clear() {
    if (graphView != null && graphView.getModel() != null) {
      for (var vertex : graphView.getModel().vertices()) {
        graphView.getModel().removeVertex(vertex);
      }
    }
  }

  public void updateGraph() {

    if (!initialized || !graphViewReady || graphView == null) {
      Logging.INSTANCE.warn("Attempted to update graph before initialization");
      return;
    }

    clear();
    var graph =
        TreeModel.createGraph(
            scope.getFocalRoot(),
            scope.getGraphDepth(),
            scope,
            visibleTypes,
            visibleRelationships,
            scope.getFocalAsset());

    var cache = new HashMap<Long, Asset>();
    for (var vertex : graph.vertexSet()) {
      var asset = new Asset(vertex);
      cache.put(vertex.getId(), asset);
      graphView.getModel().insertVertex(asset);
    }
    for (var edge : graph.edgeSet()) {
      graphView.getModel().insertEdge(cache.get(edge.sourceId), cache.get(edge.targetId), edge);
    }
    try {
      this.graphView.update();
      Platform.runLater(
          () -> {
            timeline.drawTimeline();
            for (var graphAsset : cache.values()) {
              graphAsset.setStyle(this.graphView);
            }
          });
    } catch (IllegalStateException e) {
      // TODO enqueue event for when graph comes into view
      Logging.INSTANCE.warn("Failed to update graph view: " + e.getMessage());
    }
  }

  @Override
  public void submissionStarted(Observation observation) {}

  @Override
  public void submissionAborted(Observation observation) {}

  @Override
  public void submissionFinished(Observation observation) {
    var root =
        observation.getMetadata().containsKey(Metadata.IM_COMMIT)
            ? observation.getMetadata().get(Metadata.IM_COMMIT, KnowledgeGraph.Commit.class)
            : RuntimeAsset.CONTEXT_ASSET;
    MenuItem homeMenuItem = new MenuItem(Theme.getLabel(root), new FontIcon(Evaicons.DOWNLOAD));
    homeButton.getItems().add(homeMenuItem);
    homeMenuItem.setOnAction(
        event -> {
          homeButton.setText(Theme.getLabel(root));
          homeButton.setGraphic(new FontIcon(Evaicons.DOWNLOAD));
          scope.setFocalAssets(root, observation);
          editor.getKnowledgeTree().update(root, observation);
          if (graphView == null) {
            this.changePending.set(true);
          } else {
            graphView.setAutomaticLayout(true);
            updateGraphSafely();
          }
        });
    homeMenuItem.fire();
  }

  @Override
  public void setContext(Observation observation) {}

  @Override
  public void setObserver(Observation observation) {}

  @Override
  public void knowledgeGraphModified() {}

  @Override
  public void activitiesModified() {}

  @Override
  public void scheduleModified(Schedule schedule) {
    Platform.runLater(
        () -> {
          if (!timeline.isVisible()) {
            timeline.setVisible(true);
          }
          timeline.updateEndTime(schedule.getEnd());
        });
  }

  @Override
  public void cleanup() {}

  public boolean isGraphViewReady() {
    return graphViewReady
        && initialized
        && graphView != null
        && graphView.getParent() != null
        && graphView.getScene() != null;
  }

  public void setGraphViewReady(boolean graphViewReady) {
    this.graphViewReady = graphViewReady;
  }

  // Safely update the graph on the JavaFX application thread
  private void updateGraphSafely() {
    if (Platform.isFxApplicationThread()) {
      updateGraph(/*scope.getFocalAssets()*/ );
    } else {
      Platform.runLater(() -> updateGraph(/*scope.getFocalAssets()*/ ));
    }
  }

  /**
   * Set a callback to be invoked when the graph view is brought back into view/focus. The callback
   * is executed on the JavaFX Application Thread.
   */
  public void setOnBroughtIntoView(Runnable callback) {
    this.onBroughtIntoView = callback;
  }

  private void invokeBroughtIntoViewCallback() {
    if (onBroughtIntoView != null) {
      Logging.INSTANCE.info("Graph brought into view");

      if (Platform.isFxApplicationThread()) {
        onBroughtIntoView.run();
      } else {
        Platform.runLater(onBroughtIntoView);
      }
    }
  }

  @Override
  public void setDigitalTwin(IDEContextScope scope, boolean inFocus) {
    scope.addViewer(this);
    if (this.sceneProperty().get() != null && this.sceneProperty().get().getWindow() != null) {
      initializeGraphView();
    } else {
      this.sceneProperty()
          .addListener(
              (observable, oldScene, newScene) -> {
                Logging.INSTANCE.info("Graph view scene changed: " + newScene);
                if (!initialized && newScene != null) {
                  // Delay initialization until the next pulse to ensure proper layout.
                  Platform.runLater(
                      () -> {
                        Logging.INSTANCE.info("Graph view scene visible: " + newScene);
                        initializeGraphView();
                      });
                }
              });
    }

    // If this view is being brought into focus, invoke the callback
    if (inFocus) {
      invokeBroughtIntoViewCallback();
    }
  }

  @Override
  public void close() {
    scope.removeViewer(this);
  }

  @Override
  public void closeDigitalTwin(IDEContextScope ideContextScope) {}

  @Override
  public void unsetDigitalTwin(IDEContextScope focalScope) {}
}

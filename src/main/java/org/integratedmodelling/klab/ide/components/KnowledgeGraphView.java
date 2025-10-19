package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import com.brunomnsilva.smartgraph.graph.DigraphEdgeList;
import com.brunomnsilva.smartgraph.graph.Graph;
import com.brunomnsilva.smartgraph.graphview.SmartGraphPanel;
import com.brunomnsilva.smartgraph.graphview.SmartRandomPlacementStrategy;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.jgrapht.graph.DefaultEdge;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

public class KnowledgeGraphView extends BorderPane implements DigitalTwinViewer {

  private final ClientKnowledgeGraph knowledgeGraph;
  private final ContextScope scope;
  private final DigitalTwinEditor editor;
  private boolean autoLayout = false;
  private SmartGraphPanel<RuntimeAsset, ClientKnowledgeGraph.Relationship> graphView;
  private int depth = 2;
  private Set<RuntimeAsset.Type> visibleTypes =
      EnumSet.of(RuntimeAsset.Type.OBSERVATION, RuntimeAsset.Type.CONTEXT);
  private Set<GraphModel.Relationship> visibleRelationships =
      EnumSet.of(GraphModel.Relationship.HAS_CHILD);

  private List<RuntimeAsset> focalAssets = new ArrayList<>();
  // Queue to store pending updates until the graph is ready
  private List<RuntimeAsset> pendingFocalAssets = null;

  private volatile boolean initialized = false;
  private volatile boolean graphViewReady = false;
  private Timeline timeline;

  public KnowledgeGraphView(
      ContextScope scope, ClientKnowledgeGraph knowledgeGraph, DigitalTwinEditor editor) {

    this.scope = scope;
    this.knowledgeGraph = knowledgeGraph;
    this.editor = editor;
    //    this.focalAssets.add(RuntimeAsset.CONTEXT_ASSET);

    HBox controls = new HBox(2);
    controls.getStyleClass().add(Styles.SMALL);
    controls.setStyle("-fx-padding: 5px;");

    HBox switchesBox = new HBox(2);
    switchesBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
    switchesBox.getStyleClass().add(Styles.SMALL);

    ToggleSwitch switch1 = new ToggleSwitch("Children");
    ToggleSwitch switch2 = new ToggleSwitch("Affected");
    ToggleSwitch switch3 = new ToggleSwitch("Data");
    ToggleSwitch switch4 = new ToggleSwitch("Activities");
    ToggleSwitch switch5 = new ToggleSwitch("Actuators");

    switch1.setSelected(true);
    switch1.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    switch2.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    switch3.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    switch4.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    switch5.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);

    Button homeButton = new Button();
    homeButton.setGraphic(new FontIcon(Material2AL.HOME));
    homeButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT, Styles.SMALL);
    Button minusButton = new Button();
    minusButton.setGraphic(new FontIcon(Material2MZ.REMOVE_CIRCLE_OUTLINE));
    minusButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT, Styles.SMALL);
    Button plusButton = new Button();
    plusButton.setGraphic(new FontIcon(Material2AL.ADD_CIRCLE_OUTLINE));
    plusButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT, Styles.SMALL);
    Button redrawButton = new Button();
    redrawButton.setGraphic(new FontIcon(Material2AL.AUTORENEW));
    redrawButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT, Styles.SMALL);

    homeButton.setOnAction(
        event -> {
          focalAssets.clear();
          focalAssets.add(RuntimeAsset.CONTEXT_ASSET);
          if (isGraphViewReady()) {
            updateGraphSafely(focalAssets);
          }
        });
    minusButton.setOnAction(
        event -> {
          if (depth > 1) {
            depth--;
            if (isGraphViewReady() && !focalAssets.isEmpty()) {
              updateGraphSafely(focalAssets);
            }
          }
        });
    plusButton.setOnAction(
        event -> {
          if (depth < 5) {
            depth++;
            if (isGraphViewReady() && !focalAssets.isEmpty()) {
              updateGraphSafely(focalAssets);
            }
          }
        });
    redrawButton.setOnAction(
        event -> {
          if (depth < 5) {
            depth++;
            if (isGraphViewReady() && !focalAssets.isEmpty()) {
              autoLayout = !autoLayout;
              graphView.setAutomaticLayout(autoLayout);
            }
          }
        });
    switch1.selectedProperty().addListener((obs, old, val) -> {});
    switch2.selectedProperty().addListener((obs, old, val) -> {});
    switch3.selectedProperty().addListener((obs, old, val) -> {});
    switch4.selectedProperty().addListener((obs, old, val) -> {});
    switch5.selectedProperty().addListener((obs, old, val) -> {});

    switchesBox.getChildren().addAll(switch1, switch2, switch3, switch4, switch5);
    HBox spinnerBox = new HBox(homeButton, minusButton, plusButton, redrawButton);
    HBox.setHgrow(spinnerBox, javafx.scene.layout.Priority.ALWAYS);
    controls.getChildren().addAll(spinnerBox, switchesBox);
    this.setTop(controls);
    this.focalAssets.addAll(
        KlabIDEController.instance().requireDigitalTwinPeer(scope).register(this));

    this.sceneProperty()
        .addListener(
            (observable, oldScene, newScene) -> {
              if (!initialized && newScene != null) {
                // Delay initialization until the next pulse to ensure proper layout
                Platform.runLater(() -> initializeGraphView());
              }
            });
  }

  private void initializeGraphView() {
    // Check if the component has valid dimensions before initializing
    if (getWidth() > 0 && getHeight() > 0 && !initialized) {
      Logging.INSTANCE.info("Initializing Knowledge Graph View");
      var initialPlacement = new SmartRandomPlacementStrategy();
      var graph = new DigraphEdgeList<RuntimeAsset, ClientKnowledgeGraph.Relationship>();
      this.graphView = new SmartGraphPanel<>(graph, initialPlacement);
      this.setCenter(this.graphView);
      this.graphView.setAutomaticLayout(true);

      graphView.setVertexDoubleClickAction(
          graphVertex -> {
            var asset = graphVertex.getUnderlyingVertex().element();
            if (asset instanceof Asset wrapper) {
              asset = wrapper.getDelegate();
            }
            this.editor.selectAsset(asset);
            KlabIDEController.instance().requireDigitalTwinPeer(this.scope).focus(asset);
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
                  updateGraph(pendingFocalAssets);
                  focalAssets = pendingFocalAssets;
                  pendingFocalAssets = null;
                } else if (!focalAssets.isEmpty()) {
                  updateGraph(focalAssets);
                }
              } else {
                // Still not ready, try again
                Platform.runLater(() -> initializeGraphView());
              }
            } catch (IllegalStateException e) {
              Logging.INSTANCE.warn(
                  "Graph view initialization failed, retrying: " + e.getMessage());
              // If still not ready, try again after another layout pass
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
                          updateGraph(pendingFocalAssets);
                          focalAssets = pendingFocalAssets;
                          pendingFocalAssets = null;
                        } else if (focalAssets != null) {
                          updateGraph(focalAssets);
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

  //  public void setFocalAsset(List<RuntimeAsset> assets) {
  //    // Always run on JavaFX Application Thread for thread safety
  //    if (Platform.isFxApplicationThread()) {
  //      setFocalAssetInternal(assets);
  //    } else {
  //      Platform.runLater(() -> setFocalAssetInternal(assets));
  //    }
  //  }

  //  private void setFocalAssetInternal(List<RuntimeAsset> asset) {
  //    if (initialized && graphViewReady && graphView != null) {
  //      focalAssets.clear();
  //      focalAssets.addAll(asset);
  //      try {
  //        updateGraph(asset);
  //        graphView.update();
  //      } catch (IllegalStateException e) {
  //        Logging.INSTANCE.warn(
  //            "Graph update failed, graph view may not be ready: " + e.getMessage());
  //        // Store the asset for later processing
  //        pendingFocalAssets = asset;
  //      }
  //    } else {
  //      // Store the asset for later processing when the graph is ready
  //      pendingFocalAssets = asset;
  //    }
  //  }

  public void clear() {
    if (graphView != null && graphView.getModel() != null) {
      for (var vertex : graphView.getModel().vertices()) {
        graphView.getModel().removeVertex(vertex);
      }
    }
  }

  //  private void fillGraph(Asset asset, int depth, Set<Asset> cache) {
  //
  //    for (GraphModel.Relationship relationship : visibleRelationships) {
  //      for (var targetEdge :
  //          knowledgeGraph.getLinks(
  //              asset,
  //              GraphModel.Relationship.Direction.OUTGOING,
  //              scope,
  //              visibleRelationships.toArray(GraphModel.Relationship[]::new))) {
  //        var targetAsset = new Asset(targetEdge.target());
  //        if (!cache.contains(targetAsset)) {
  //          graphView.getModel().insertVertex(targetAsset);
  //          cache.add(targetAsset);
  //        }
  //        graphView
  //            .getModel()
  //            .insertEdge(asset, targetAsset, new ClientKnowledgeGraph.Relationship(targetEdge));
  //        if (depth > 1) {
  //          fillGraph(targetAsset, depth - 1, cache);
  //        }
  //      }
  //    }
  //  }

  public void updateGraph(List<RuntimeAsset> focalAssets) {

    if (!initialized || !graphViewReady || graphView == null) {
      Logging.INSTANCE.warn("Attempted to update graph before initialization");
      return;
    }

    clear();

    if (focalAssets.isEmpty()) {
      focalAssets.add(RuntimeAsset.CONTEXT_ASSET);
    }

    var graph = knowledgeGraph.getSubgraph(focalAssets, depth, visibleTypes, visibleRelationships);

    var cache = new HashMap<Long, Asset>();
    if (graph != null) {
      for (var vertex : graph.vertexSet()) {
        var asset = new Asset(vertex);
        cache.put(vertex.getId(), asset);
        graphView.getModel().insertVertex(asset);
      }
      for (var edge : graph.edgeSet()) {
        graphView.getModel().insertEdge(cache.get(edge.sourceId), cache.get(edge.targetId), edge);
      }
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
      Logging.INSTANCE.warn("Failed to update graph view: " + e.getMessage());
    }
  }

  @Override
  public void submissionStarted(Observation observation) {}

  @Override
  public void submissionAborted(Observation observation) {}

  @Override
  public void submissionFinished(Observation observation) {
    //    setFocalAsset(observation);
  }

  @Override
  public void setContext(Observation observation) {}

  @Override
  public void setObserver(Observation observation) {}

  @Override
  public void knowledgeGraphModified() {}

  @Override
  public void activitiesModified(org.jgrapht.Graph<Activity, DefaultEdge> activityGraph) {}

  @Override
  public void focusObservations(List<RuntimeAsset> assets) {
    updateGraphSafely(assets);
  }

  //  private Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> adaptGraph(
  //     ) {}

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
  private void updateGraphSafely(List<RuntimeAsset> asset) {
    if (Platform.isFxApplicationThread()) {
      updateGraph(asset);
    } else {
      Platform.runLater(() -> updateGraph(asset));
    }
  }
}

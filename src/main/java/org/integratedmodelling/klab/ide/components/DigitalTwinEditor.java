package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.util.*;
import java.util.function.Consumer;

import com.google.common.net.MediaType;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.model.DigitalTwinPeer;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

public class DigitalTwinEditor extends EditorPage<ContextScope, RuntimeAsset>
    implements DigitalTwinViewer {

  private final DigitalTwinPeer controller;
  private final RuntimeService runtimeService;
  private final DigitalTwinView view;
  private ClientKnowledgeGraph knowledgeGraph;
  private HBox menuArea;
  private KnowledgeGraphTree treeView;
  private RuntimeAsset context;
  private KnowledgeGraphView knowledgeGraphView;
  private final ContextScope contextScope;

  public DigitalTwinEditor(
      ContextScope contextScope, RuntimeService runtimeService, DigitalTwinView digitalTwinView) {
    super(contextScope);
    this.controller = KlabIDEController.instance().requireDigitalTwinPeer(contextScope);
    this.controller.register(this);
    this.contextScope = contextScope;
    this.runtimeService = runtimeService;
    if (contextScope.getDigitalTwin() instanceof ClientDigitalTwin clientDigitalTwin) {
      this.knowledgeGraph = clientDigitalTwin.getKnowledgeGraph();
    }
    this.context = RuntimeAsset.CONTEXT_ASSET;
    this.view = digitalTwinView;
  }

  @Override
  public void knowledgeGraphModified() {
    updateTree(this.context);
  }

  @Override
  public void scheduleModified(Schedule schedule) {}

  @Override
  public void cleanup() {
    Platform.runLater(() -> this.view.removeDigitalTwin(contextScope));
  }

  @Override
  protected void onSingleClickItemSelection(RuntimeAsset value) {}

  @Override
  protected void onDoubleClickItemSelection(RuntimeAsset value) {}

  private ContextMenu activeContextMenu;

  @Override
  protected TreeView<RuntimeAsset> createContentTree() {

    treeView = new KnowledgeGraphTree(this.context, contextScope);
    controller.register(treeView);
    treeView.setCellFactory(p -> new AssetTreeCell());
    treeView.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    treeView.setShowRoot(false);
    treeView.setPrefWidth(340);
    // FIXME the context menu remains on the scene until clicked or escaped, and moves around within
    // the tree
    treeView.setOnContextMenuRequested(
        event -> {
          TreeItem<RuntimeAsset> item = treeView.getSelectionModel().getSelectedItem();
          if (item != null && item.getValue() != null && isContextMenuShown(item.getValue())) {
            if (activeContextMenu != null) {
              activeContextMenu.hide();
            }
            activeContextMenu = new ContextMenu();
            List<Pair<String, Consumer<RuntimeAsset>>> entries =
                getContextMenuEntries(item.getValue());
            for (Pair<String, Consumer<RuntimeAsset>> entry : entries) {
              MenuItem menuItem = new MenuItem(entry.getFirst());
              menuItem.setOnAction(e -> entry.getSecond().accept(item.getValue()));
              activeContextMenu.getItems().add(menuItem);
            }
            activeContextMenu.show(treeView, event.getScreenX(), event.getScreenY());
            event.consume();
          }
        });
    return treeView;
  }

  protected boolean isContextMenuShown(RuntimeAsset asset) {
    return asset instanceof Observation observation;
  }

  protected List<Pair<String, Consumer<RuntimeAsset>>> getContextMenuEntries(RuntimeAsset asset) {

    var ret = new ArrayList<Pair<String, Consumer<RuntimeAsset>>>();
    ret.add(Pair.of("Open detailed view", this::showDetails));

    if (asset instanceof Observation observation) {
      if (observation.getObservable().is(SemanticType.QUALITY)) {
        ret.add(Pair.of("Export to filesystem", this::exportToFilesystem));
      } else if (observation.getObservable().is(SemanticType.SUBJECT)) {
        ret.add(Pair.of("Set as context", this::setAsContext));
      }
    }
    return ret;
  }

  void showDetails(RuntimeAsset asset) {
    if (asset instanceof KlabAsset klabAsset) {
      try (var stream =
          KlabIDEController.modeler()
              .visualize(klabAsset, null, "text/html", contextScope, Map.of())) {
        if (stream != null) {
          Logging.INSTANCE.info("Visualizing asset: " + asset);
        }

      } catch (Exception e) {
        Logging.INSTANCE.error("Failed to visualize asset", e);
      }
    }
  }

  void exportToFilesystem(RuntimeAsset observation) {
    Logging.INSTANCE.info("Exporting observation to filesystem: " + observation);
  }

  void setAsContext(RuntimeAsset observation) {}

  private void initializeContextMenu() {
    setOnMouseClicked(
        event -> {
          if (event.isSecondaryButtonDown()) {}
        });
  }

  @Override
  public void activitiesModified(Graph<Activity, DefaultEdge> activityGraph) {}

  @Override
  public void focusObservations(List<RuntimeAsset> ids) {}

  private List<RuntimeAsset> children(RuntimeAsset asset) {
    if (controller.scope().getDigitalTwin().getKnowledgeGraph()
        instanceof ClientKnowledgeGraph clientKnowledgeGraph) {
      return clientKnowledgeGraph.outgoing(asset, GraphModel.Relationship.HAS_CHILD);
    }
    return List.of();
  }

  @Override
  protected void configureDigitalTwinWidget(DigitalTwinControlPanel digitalTwinMinified) {
    super.configureDigitalTwinWidget(digitalTwinMinified);
    KlabIDEController.instance().requireDigitalTwinPeer(contextScope).register(digitalTwinMinified);
  }

  @Override
  protected Node createEditor(RuntimeAsset asset) {
    if (asset == context) {
      var ret =
          this.knowledgeGraphView =
              new KnowledgeGraphView(this.controller.scope(), this.knowledgeGraph, this);
      KlabIDEController.instance().requireDigitalTwinPeer(contextScope).register(ret);
      return ret;
    }
    return null;
  }

  private void updateTree(RuntimeAsset changed) {
    Platform.runLater(
        () -> {
          if (treeView == null || treeView.getSelectionModel() == null) {
            return;
          }

          // Store selection to restore it later
          TreeItem<RuntimeAsset> selectedItem = treeView.getSelectionModel().getSelectedItem();

          // Temporarily disable cell updates to prevent flickering
          treeView.setDisable(true);

          try {
            // Restore selection if possible
            if (selectedItem != null) {
              var newSelectedItem = treeView.findItemById(selectedItem.getValue().getId());
              if (newSelectedItem != null) {
                treeView.getSelectionModel().select(newSelectedItem);
              }
            }
          } finally {
            treeView.setDisable(false);
          }
        });
  }

  public RuntimeAsset getRootAsset() {
    return this.context;
  }

  @Override
  public void submissionStarted(Observation observation) {}

  @Override
  public void submissionAborted(Observation observation) {}

  @Override
  public void submissionFinished(Observation observation) {}

  @Override
  public void setContext(Observation observation) {}

  @Override
  public void setObserver(Observation observation) {}

  public void selectAsset(RuntimeAsset asset) {
    // TODO we can link the action to the selection and stop here.
    Logging.INSTANCE.info("Selecting asset: " + asset);
    var item = treeView.findItemById(asset.getId());
    if (item != null) {
      Platform.runLater(
          () -> {
            treeView.getSelectionModel().select(item);
          });
    }
  }

  private static final class AssetTreeCell extends TreeCell<RuntimeAsset> {
    @Override
    protected void updateItem(RuntimeAsset asset, boolean empty) {
      super.updateItem(asset, empty);

      if (empty || asset == null) {
        // Cell is empty or asset is null - clear everything
        setText(null);
        setGraphic(null);
        setStyle(null);
      } else {
        // Cell has valid content
        setText(Theme.getLabel(asset));
        setGraphic(Theme.getGraphics(asset));
        switch (asset) {
          default -> {
            setStyle(null);
          }
        }
      }
    }
  }
}

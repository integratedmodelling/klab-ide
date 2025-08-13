package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
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
  private TreeItem<RuntimeAsset> root;
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
    this.root = defineTree(this.context);
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

  //  @Override
  //  public void activityFinished(Activity payload) {
  //    activities.computeIfAbsent(payload.getTransientId(), id -> payload);
  //  }
  //
  //  @Override
  //  public void activityStarted(Activity payload) {
  //    activities.computeIfAbsent(payload.getTransientId(), id -> payload);
  //  }

  @Override
  protected void onSingleClickItemSelection(RuntimeAsset value) {}

  @Override
  protected void onDoubleClickItemSelection(RuntimeAsset value) {}

  @Override
  protected TreeView<RuntimeAsset> createContentTree() {

    treeView = new KnowledgeGraphTree(this.root);
    controller.register(treeView);
    treeView.setCellFactory(p -> new AssetTreeCell());
    treeView.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    treeView.setShowRoot(false);
    treeView.setPrefWidth(340);
    treeView.setOnContextMenuRequested(
        event -> {
          TreeItem<RuntimeAsset> item = treeView.getSelectionModel().getSelectedItem();
          if (item != null) {
            var contextMenu = new javafx.scene.control.ContextMenu();
            switch (item.getValue()) {
              //                  case NavigableProject project -> {
              //                    var lockUnlock =
              //                            new javafx.scene.control.MenuItem(project.isLocked() ?
              // "Unlock" : "Lock");
              //                    lockUnlock.setOnAction(
              //                            e -> {
              //                              if (runtimeService instanceof ResourcesService.Admin
              // admin) {
              //                                if (project.isLocked()) {
              //                                  admin.unlockProject(project.getUrn(),
              // KlabIDEController.modeler().user());
              //                                  project.setLocked(false);
              //                                } else {
              //                                  admin.lockProject(project.getUrn(),
              // KlabIDEController.modeler().user());
              //                                  project.setLocked(true);
              //                                }
              //                              }
              //                            });
              //                    contextMenu.getItems().add(lockUnlock);
              //                  }
              //                  case KlabDocument<?> document -> {
              //                    var openEdit = new javafx.scene.control.MenuItem("Open");
              //                    openEdit.setOnAction(e -> edit(item.getValue()));
              //                    contextMenu.getItems().add(openEdit);
              //                  }
              default -> {}
            }
            contextMenu.show(treeView, event.getScreenX(), event.getScreenY());
          }
        });

    return treeView;
  }

  @Override
  public void activitiesModified(Graph<Activity, DefaultEdge> activityGraph) {}

  private List<RuntimeAsset> children(RuntimeAsset asset) {
    if (controller.scope().getDigitalTwin().getKnowledgeGraph()
        instanceof ClientKnowledgeGraph clientKnowledgeGraph) {
      return clientKnowledgeGraph.outgoing(asset, GraphModel.Relationship.HAS_CHILD);
    }
    return List.of();
  }

  /**
   * Tree behavior:
   *
   * <p>on explicit observation submission received: add it in ctx with clock icon on resolved tree
   * received:
   *
   * <p>on submission failed: on submission finished: check if empty;
   *
   * @param asset
   * @return
   */
  private TreeItem<RuntimeAsset> defineTree(RuntimeAsset asset) {
    var ret = new TreeItem<>(asset);
    for (var child : children(asset)) {
      ret.getChildren().add(defineTree(child));
    }
    return ret;
  }

  @Override
  protected void configureDigitalTwinWidget(DigitalTwinControlPanel digitalTwinMinified) {
    super.configureDigitalTwinWidget(digitalTwinMinified);
    KlabIDEController.instance().requireDigitalTwinPeer(contextScope).register(digitalTwinMinified);
  }

  @Override
  protected Node createEditor(RuntimeAsset asset) {
    if (asset == context) {
      return this.knowledgeGraphView =
          new KnowledgeGraphView(this.controller.scope(), this.knowledgeGraph, this);
    }
    return null;
  }

  private TreeItem<RuntimeAsset> findTreeItemById(TreeItem<RuntimeAsset> current, long id) {
    if (current.getValue().getId() == id) {
      return current;
    }
    for (TreeItem<RuntimeAsset> child : current.getChildren()) {
      TreeItem<RuntimeAsset> result = findTreeItemById(child, id);
      if (result != null) {
        return result;
      }
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
            treeView.getRoot().getChildren().clear();
            TreeItem<RuntimeAsset> newRoot = defineTree(RuntimeAsset.CONTEXT_ASSET);
            treeView.setRoot(root = newRoot);

            // Restore selection if possible
            if (selectedItem != null) {
              TreeItem<RuntimeAsset> newSelectedItem =
                  findTreeItemById(newRoot, selectedItem.getValue().getId());
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
    var item = findTreeItemById(root, asset.getId());
    Platform.runLater(
        () -> {
          treeView.getSelectionModel().select(item);
        });
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

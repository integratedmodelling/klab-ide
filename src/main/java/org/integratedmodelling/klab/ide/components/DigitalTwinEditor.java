package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.components.treeviews.KnowledgeGraphTree;
import org.integratedmodelling.klab.ide.pages.EditorPage;

public class DigitalTwinEditor extends EditorPage<IDEContextScope, RuntimeAsset>
    implements DigitalTwinViewer {

  private final RuntimeService runtimeService;
  private final DigitalTwinView view;
  private ClientKnowledgeGraph knowledgeGraph;
  private HBox menuArea;
  private KnowledgeGraphTree treeView;
  private RuntimeAsset context;
  private KnowledgeGraphView knowledgeGraphView;
  private final IDEContextScope contextScope;

  public DigitalTwinEditor(
      IDEContextScope contextScope,
      RuntimeService runtimeService,
      DigitalTwinView digitalTwinView) {
    super(contextScope);
    this.contextScope = contextScope;
    this.runtimeService = runtimeService;
    if (contextScope.getDigitalTwin() instanceof ClientDigitalTwin clientDigitalTwin) {
      this.knowledgeGraph = clientDigitalTwin.getKnowledgeGraph();
    }
    this.context = RuntimeAsset.CONTEXT_ASSET;
    this.view = digitalTwinView;
    setDigitalTwin(contextScope, true);
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
    //    controller.register(treeView);
    treeView.setCellFactory(p -> new AssetTreeCell());
    treeView.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    treeView.setShowRoot(false);
    treeView.setPrefWidth(340);
    // FIXME the context menu remains on the scene until clicked or escaped, and moves around
    // FIXME bring this within KnowledgeGraphTree
    treeView.setOnContextMenuRequested(
        event -> {
          TreeItem<RuntimeAsset> item = treeView.getSelectionModel().getSelectedItem();
          if (item != null && item.getValue() != null && isContextMenuShown(item.getValue())) {
            if (activeContextMenu != null) {
              activeContextMenu.hide();
            }
            activeContextMenu = new ContextMenu();
            activeContextMenu.setAutoHide(true);
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
    this.knowledgeGraphView.setDigitalTwin(contextScope, true);
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
        ret.add(Pair.of("Set as context", o -> setAsContext(observation)));
      }
    }
    return ret;
  }

  void showDetails(RuntimeAsset asset) {
    if (asset instanceof KlabAsset klabAsset) {
      edit(asset);
    }
  }

  void exportToFilesystem(RuntimeAsset observation) {
    Logging.INSTANCE.info("Exporting observation to filesystem: " + observation);
  }

  void setAsContext(Observation observation) {
    contextScope.within(observation);
  }

  private void initializeContextMenu() {
    setOnMouseClicked(
        event -> {
          if (event.isSecondaryButtonDown()) {}
        });
  }

  @Override
  public void activitiesModified() {}

//  @Override
//  public void focusObservations(RuntimeAsset rootAsset, List<RuntimeAsset> focalAssets) {}

  @Override
  protected void onVisualize(boolean visibleAfterCall) {
    KlabIDEController.instance().setFocalEditor(this, visibleAfterCall);
  }

  @Override
  protected void configureDigitalTwinWidget(DigitalTwinControlPanel digitalTwinMinified) {
    digitalTwinMinified.setDigitalTwin(contextScope, true);
  }

  @Override
  protected Node createEditor(RuntimeAsset asset) {
    if (asset == context) {
      var ret =
          this.knowledgeGraphView =
              new KnowledgeGraphView(this.contextScope, this.knowledgeGraph, this);
      return ret;
    } else if (asset instanceof Observation observation) {
      File imageUrl =
          Utils.Files.copyInputStreamToTempFile(
              contextScope
                  .getService(RuntimeService.class)
                  .exportAsset(
                      observation.getUrn(),
                      KlabAsset.KnowledgeClass.OBSERVATION,
                      "image/png",
                      Parameters.create("viewportX", 800, "viewportY", 800),
                      contextScope),
              "png");
      return new AssetViewer(
          observation,
          () -> {
            var url =
                KlabIDEController.instance()
                    .visualize(observation, null, "text/html", contextScope, Map.of(), URL.class);
            return url == null ? null : url.toString();
          },
          imageUrl);
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
  public void submissionFinished(Observation observation) {
    var root = observation.getMetadata().containsKey(Metadata.IM_COMMIT)
            ? observation.getMetadata().get(Metadata.IM_COMMIT, KnowledgeGraph.Commit.class)
            : RuntimeAsset.CONTEXT_ASSET;

  }

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

  @Override
  public boolean isAffectedBy(IDEContextScope scope) {
    return this.contextScope.getId().equals(scope.getId());
  }

  @Override
  public void setDigitalTwin(IDEContextScope scope, boolean focus) {

    //    this.digitalTwinControlPanel.setDigitalTwin(scope, focus);
  }

  @Override
  public void close() {

    /** TODO if the scope is ONE_OFF, should alert and remove */
    if (contextScope.getConfiguration().getPersistence() == Persistence.IDLE_TIMEOUT) {
      // TODO start timeout counter, add a notification (scope ... will be removed in xxxx if not
      // used again)
    }
    // Removed the circular call that was causing the StackOverflowError
    // The view.removeDigitalTwin() call is handled by the caller (DigitalTwinView)
    knowledgeGraphView.close();
    treeView.close();
    digitalTwinControlPanel.close();
    super.close();
  }

  @Override
  public void closeDigitalTwin(IDEContextScope ideContextScope) {
    Platform.runLater(
        () -> {
          close();
          view.removeDigitalTwin(contextScope);
        });
  }

  @Override
  public void unsetDigitalTwin(IDEContextScope focalScope) {
    if (this.contextScope.getId().equals(focalScope.getId())) {
      this.view.removeDigitalTwin(focalScope);
    }
  }
}

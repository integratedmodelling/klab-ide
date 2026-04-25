package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

import com.brunomnsilva.smartgraph.graphview.SmartGraphPanel;
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
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
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

    treeView = new KnowledgeGraphTree(this, this.context, contextScope);
    treeView.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    treeView.setShowRoot(false);
    treeView.setPrefWidth(340);
    this.knowledgeGraphView.setDigitalTwin(contextScope, true);
    return treeView;
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

  @Override
  public void activitiesModified() {}

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
    var root =
        observation.getMetadata().containsKey(Metadata.IM_COMMIT)
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

  public KnowledgeGraphTree getKnowledgeTree() {
    return treeView;
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

  public void setupAssetMenu(ContextMenu contextMenu, RuntimeAsset asset) {

    contextMenu.getItems().clear();

    var showDetails = new MenuItem("Open detailed view");
    showDetails.setOnAction(event -> showDetails(asset));

    contextMenu.getItems().add(showDetails);

    if (asset instanceof Observation observation) {
      if (observation.getObservable().is(SemanticType.QUALITY)) {
        var exportMenu = new MenuItem("Export to filesystem...");
        exportMenu.setOnAction(event -> exportToFilesystem(asset));
        contextMenu.getItems().add(exportMenu);
      } else if (observation.getObservable().is(SemanticType.SUBJECT)) {
        var setAsContext = new MenuItem("Set as context");
        setAsContext.setOnAction(event -> setAsContext(observation));
        contextMenu.getItems().add(setAsContext);
      }
    }
  }
}

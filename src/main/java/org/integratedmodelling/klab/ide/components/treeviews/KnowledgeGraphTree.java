package org.integratedmodelling.klab.ide.components.treeviews;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.text.Text;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.components.DigitalTwinEditor;
import org.integratedmodelling.klab.ide.components.WorkspaceEditor;
import org.integratedmodelling.klab.modeler.model.NavigableKimConceptStatement;
import org.integratedmodelling.klab.modeler.model.NavigableKimModel;
import org.integratedmodelling.klab.modeler.model.NavigableProject;

public class KnowledgeGraphTree extends KlabTreeView<RuntimeAsset> implements DigitalTwinViewer {

  private TreeModel.AssetTreeItem previousBoldItem;
  private ClientKnowledgeGraph clientKnowledgeGraph;
  private TreeModel.AssetTreeItem root;
  private IDEContextScope scope;
  private DigitalTwinEditor editor;

  private static final class AssetTreeCell extends TreeCell<RuntimeAsset> {

    DigitalTwinEditor editor;

    public AssetTreeCell(DigitalTwinEditor workspaceEditor) {
      this.editor = workspaceEditor;
    }

    @Override
    protected void updateItem(RuntimeAsset asset, boolean empty) {
      super.updateItem(asset, empty);
      if (asset != null && !empty) {
        setText(Theme.getLabel(asset));
        setGraphic(Theme.getGraphics(asset));
        setOnContextMenuRequested(
            event -> {
              var contextMenu = new ContextMenu();
              contextMenu.setAutoHide(true);
              editor.setupAssetMenu(contextMenu, asset);
              contextMenu.show(this, event.getScreenX(), event.getScreenY());
            });
        switch (asset) {
          // TODO these never match, left for reference - set up as needed
          case NavigableProject navigableProject -> {
            if (navigableProject.isLocked()) {
              setStyle("-fx-text-fill: -color-success-fg;");
            }
          }
          case NavigableDocument navigableProject -> {
            // leave these - there is an unclear style "leaking" phenomenon otherwise
            setStyle(null);
          }
          case NavigableKimConceptStatement navigableProject -> {
            setStyle(null);
          }
          case NavigableKimModel navigableProject -> {
            setStyle(null);
          }
          default -> {
            setStyle(null);
          }
        }

      } else {
        setText(null);
        setGraphic(null);
      }
    }
  }

  public TreeItem<RuntimeAsset> findItemById(long id) {
    return findItemById(root, id);
  }

  public TreeItem<RuntimeAsset> findItemById(TreeItem<RuntimeAsset> current, long id) {
    if (current.getValue().getId() == id) {
      return current;
    }
    for (TreeItem<RuntimeAsset> child : current.getChildren()) {
      TreeItem<RuntimeAsset> result = findItemById(child, id);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Override
  public boolean isAffectedBy(IDEContextScope scope) {
    return this.scope.getId().equals(scope.getId());
  }

  public KnowledgeGraphTree(
      DigitalTwinEditor editor, RuntimeAsset rootAsset, ContextScope contextScope) {
    super();
    this.editor = editor;
    this.scope = KlabIDEController.instance().requireDigitalTwinPeer(contextScope, this);
    this.clientKnowledgeGraph = this.scope.getDigitalTwin().getKnowledgeGraph();
    setCellFactory(p -> new AssetTreeCell(editor));

    var pair = TreeModel.createTree(rootAsset, null, scope);
    setRoot(pair.getFirst());
    if (pair.getSecond() != null) {
      getSelectionModel().select(pair.getSecond());
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
    update(root, observation);
  }

  public void update(RuntimeAsset rootAsset, RuntimeAsset focus) {

    var pair = TreeModel.createTree(rootAsset, focus, scope);
    setRoot(pair.getFirst());
    if (pair.getSecond() != null) {
      getSelectionModel().select(pair.getSecond());
    }
  }

  @Override
  public void setContext(Observation observation) {

    // DO NOT call scope.within(observation) here - it causes infinite recursion
    // The context change should be initiated externally, and this method
    // should only react to the notification

    if (observation != null) {
      var item = findTreeItemById((TreeModel.AssetTreeItem) getRoot(), observation.getId());
      Platform.runLater(
          () -> {
            if (previousBoldItem != null) {
              // Ensure the previous item has a graphic before styling
              ensureGraphicExists(previousBoldItem);
              previousBoldItem.graphicProperty().get().setStyle("-fx-font-weight: normal");
            }
            if (item != null) {
              // Ensure the current item has a graphic before styling
              ensureGraphicExists(item);
              item.graphicProperty().get().setStyle("-fx-font-weight: bold");
              previousBoldItem = item;
            }
          });
    }
  }

  /**
   * Ensures that a TreeItem has a Text graphic that can be styled. If no graphic exists, creates a
   * Text node with the item's value as text.
   */
  private void ensureGraphicExists(TreeItem<RuntimeAsset> item) {
    if (item.graphicProperty().get() == null) {
      // Create a Text node for styling purposes
      Text textNode = new Text();
      if (item.getValue() != null) {
        // Use the RuntimeAsset's string representation or a meaningful property
        textNode.setText(item.getValue().toString());
      } else {
        textNode.setText(""); // Empty text for null values
      }
      item.setGraphic(textNode);
    }
  }

  private TreeModel.AssetTreeItem findTreeItemById(TreeModel.AssetTreeItem current, long id) {
    if (current.getValue() != null && current.getValue().getId() == id) {
      return current;
    }
    for (var child : current.getChildren()) {
      var result = findTreeItemById((TreeModel.AssetTreeItem) child, id);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Override
  public void setObserver(Observation observation) {}

  @Override
  public void knowledgeGraphModified() {}

  @Override
  public void scheduleModified(Schedule schedule) {}

  @Override
  public void cleanup() {}

  @Override
  public void activitiesModified() {}

  @Override
  public void setDigitalTwin(IDEContextScope scope, boolean focus) {
    scope.addViewer(this);
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

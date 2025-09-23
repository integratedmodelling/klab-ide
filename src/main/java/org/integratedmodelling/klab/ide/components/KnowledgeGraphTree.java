package org.integratedmodelling.klab.ide.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.text.Text;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

public class KnowledgeGraphTree extends TreeView<RuntimeAsset> implements DigitalTwinViewer {

  private AssetTreeItem previousBoldItem;

  public static class AssetTreeItem extends TreeItem<RuntimeAsset> {
    // We cache whether the File is a leaf or not. A File is a leaf if
    // it is not a directory and does not have any files contained within
    // it. We cache this as isLeaf() is called often, and doing the
    // actual check on File is expensive.
    private boolean isLeaf;

    // We do the children and leaf testing only once, and then set these
    // booleans to false so that we do not check again during this
    // run. A more complete implementation may need to handle more
    // dynamic file system situations (such as where a folder has files
    // added after the TreeView is shown). Again, this is left as an
    // exercise for the reader.
    private boolean isFirstTimeChildren = true;
    private boolean isFirstTimeLeaf = true;
    private ContextScope contextScope;

    public AssetTreeItem(RuntimeAsset asset, ContextScope contextScope) {
      super(asset);
      this.contextScope = contextScope;
    }

    @Override
    public ObservableList<TreeItem<RuntimeAsset>> getChildren() {
      if (isFirstTimeChildren) {
        isFirstTimeChildren = false;
        // First getChildren() call, so we actually go off and
        // determine the children of the File contained in this TreeItem.
        super.getChildren().setAll(buildChildren(this));
      }
      return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
      if (isFirstTimeLeaf) {
        isFirstTimeLeaf = false;
        RuntimeAsset f = (RuntimeAsset) getValue();
        isLeaf = f.getChildrenCount() == 0;
      }

      return isLeaf;
    }

    private ObservableList<TreeItem<RuntimeAsset>> buildChildren(TreeItem<RuntimeAsset> treeItem) {
      RuntimeAsset asset = treeItem.getValue();
      if (asset != null && asset.getChildrenCount() > 0) {
        ObservableList<TreeItem<RuntimeAsset>> children = FXCollections.observableArrayList();
        for (var child : contextScope.getChildrenOf(asset)) {
          children.add(new AssetTreeItem(child, contextScope));
        }
        return children;
      }

      return FXCollections.emptyObservableList();
    }
  }

  public KnowledgeGraphTree() {
    super();
  }

  public KnowledgeGraphTree(AssetTreeItem runtimeAssetTreeItem) {
    super(runtimeAssetTreeItem);
  }

  @Override
  public void submissionStarted(Observation observation) {}

  @Override
  public void submissionAborted(Observation observation) {}

  @Override
  public void submissionFinished(Observation observation) {}

  @Override
  public void setContext(Observation observation) {
    var item = findTreeItemById((AssetTreeItem) getRoot(), observation.getId());
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

  private AssetTreeItem findTreeItemById(AssetTreeItem current, long id) {
    if (current.getValue() != null && current.getValue().getId() == id) {
      return current;
    }
    for (var child : current.getChildren()) {
      var result = findTreeItemById((AssetTreeItem) child, id);
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
  public void activitiesModified(Graph<Activity, DefaultEdge> activityGraph) {}
}

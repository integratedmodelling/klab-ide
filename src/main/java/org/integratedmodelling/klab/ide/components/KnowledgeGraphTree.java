package org.integratedmodelling.klab.ide.components;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.text.Text;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

public class KnowledgeGraphTree extends TreeView<RuntimeAsset> implements DigitalTwinViewer {

  private AssetTreeItem previousBoldItem;
  private ClientKnowledgeGraph clientKnowledgeGraph;
  private AssetTreeItem root;

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

  private class AssetTreeItem extends TreeItem<RuntimeAsset> {

    public AssetTreeItem(RuntimeAsset asset) {
      super(asset);
    }

    @Override
    public boolean isLeaf() {
      var asset = getValue();
      return asset == null
          || (asset instanceof Observation && asset.getChildrenCount() == 0)
          || (!(asset
                  instanceof Observation) // TODO eventually this should be correct for all assets
              && clientKnowledgeGraph.outgoing(asset, GraphModel.Relationship.HAS_CHILD).isEmpty());
    }

    private ObservableList<TreeItem<RuntimeAsset>> children;

    @Override
    public ObservableList<TreeItem<RuntimeAsset>> getChildren() {
      if (children == null) {
        children = super.getChildren();
      }

      RuntimeAsset asset = getValue();
      if (asset != null && (!(asset instanceof Observation) || asset.getChildrenCount() > 0)) {
        Set<Long> selectedIds =
            new HashSet<>(
                children.stream().map(TreeItem::getValue).map(RuntimeAsset::getId).toList());
        var ch = clientKnowledgeGraph.getChildAssets(asset);
        for (var child : ch) {
          if (selectedIds.contains(child.getId())) {
            continue;
          }
          children.add(new AssetTreeItem(child));
        }
        return children;
      }
      return children;
    }
  }

  public KnowledgeGraphTree() {
    super();
  }

  public KnowledgeGraphTree(RuntimeAsset rootAsset, ContextScope contextScope) {
    super();
    var kg = contextScope.getDigitalTwin().getKnowledgeGraph();
    if (kg instanceof ClientKnowledgeGraph clientKnowledgeGraph) {
      this.clientKnowledgeGraph = clientKnowledgeGraph;
    } else {
      throw new KlabIllegalStateException("Knowledge graph must be a client knowledge graph");
    }
    setRoot(new AssetTreeItem(rootAsset));
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

  @Override
  public void focusObservations(List<RuntimeAsset> ids) {}
}

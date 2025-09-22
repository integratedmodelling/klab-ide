package org.integratedmodelling.klab.ide.components;

import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.text.Text;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

public class KnowledgeGraphTree extends TreeView<RuntimeAsset> implements DigitalTwinViewer {

  private TreeItem<RuntimeAsset> previousBoldItem;

  public KnowledgeGraphTree() {
    super();
  }

  public KnowledgeGraphTree(TreeItem<RuntimeAsset> runtimeAssetTreeItem) {
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
    var item = findTreeItemById(getRoot(), observation.getId());
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

  private TreeItem<RuntimeAsset> findTreeItemById(TreeItem<RuntimeAsset> current, long id) {
    if (current.getValue() != null && current.getValue().getId() == id) {
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

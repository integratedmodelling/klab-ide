package org.integratedmodelling.klab.ide.components.treeviews;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;

import java.util.HashSet;
import java.util.Set;

class AssetTreeItem extends TreeItem<RuntimeAsset> {

  private final IDEContextScope scope;

  public AssetTreeItem(RuntimeAsset asset, IDEContextScope scope) {
    super(asset);
    this.scope = scope;
  }

  @Override
  public boolean isLeaf() {
    var asset = getValue();
    return asset == null
        || (asset instanceof KnowledgeGraph.Commit commit && commit.getAddedAssets().isEmpty())
        || (asset instanceof Observation && asset.getChildrenCount() == 0)
        || (!(asset instanceof Observation) // TODO eventually this should be correct for all assets
            && scope.getDigitalTwin().getKnowledgeGraph()
                .outgoing(asset, GraphModel.Relationship.HAS_CHILD)
                .isEmpty());
  }

  @Override
  public ObservableList<TreeItem<RuntimeAsset>> getChildren() {

    var children = super.getChildren();
    RuntimeAsset asset = getValue();
    if (asset instanceof KnowledgeGraph.Commit commit) {
      for (var observationId : commit.getAddedObservations()) {
        var observation =
            scope
                .getDigitalTwin()
                .getKnowledgeGraph()
                .getAsset(observationId, scope, Observation.class);
        if (observation != null) {
          children.add(new AssetTreeItem(observation, scope));
        }
      }
      for (var cohortId : commit.getAddedCohorts()) {
        var cohort =
            scope.getDigitalTwin().getKnowledgeGraph().getAsset(cohortId, scope, Cohort.class);
        if (cohort != null) {
          children.add(new AssetTreeItem(cohort, scope));
        }
      }
    } else if (asset != null && (!(asset instanceof Observation) || asset.getChildrenCount() > 0)) {
      Set<Long> selectedIds =
          new HashSet<>(
              children.stream().map(TreeItem::getValue).map(RuntimeAsset::getId).toList());
      var ch = scope.getDigitalTwin().getKnowledgeGraph().getChildAssets(asset);
      for (var child : ch) {
        if (selectedIds.contains(child.getId())) {
          continue;
        }
        children.add(new AssetTreeItem(child, scope));
      }
      return children;
    }
    return children;
  }
}

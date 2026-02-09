package org.integratedmodelling.klab.ide.components.treeviews;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.jgrapht.Graph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeModel {

  /**
   * Create a new AssetTreeItem for the given runtime asset and IDE context scope.
   *
   * @param asset the runtime asset to represent
   * @param scope the IDE context scope
   * @return a new AssetTreeItem root
   */
  public static AssetTreeItem createTree(RuntimeAsset asset, IDEContextScope scope) {
    return new AssetTreeItem(asset, scope);
  }

  /**
   * Creates a graph representation of the asset's relationships up to the specified depth, properly
   * handled commits as runtime assets that are not in the graph. Add all assets and relationships
   * and possibly filter later. Only the elements that are in the knowledge graph of the passed
   * scope will be considered. The Graph may be nonlinear if relationships other than HAS_CHILD and
   * HAS_MEMBER are present.
   *
   * @param asset the runtime asset to represent
   * @param depth the maximum depth of relationships to include in the graph
   * @param scope the IDE context scope
   * @return a graph representation of the asset's relationships
   */
  public static Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> createGraph(
      RuntimeAsset asset, int depth, IDEContextScope scope) {
    return null;
  }

  /**
   * Get the outgoing related objects of the given asset.
   *
   * @param asset
   * @param scope
   * @param types
   * @param relationships
   * @return
   */
  public static List<Pair<RuntimeAsset, GraphModel.Relationship>> getChildren(
      RuntimeAsset asset,
      IDEContextScope scope,
      Set<RuntimeAsset.Type> types,
      Set<GraphModel.Relationship> relationships) {
    return null;
  }

  static class AssetTreeItem extends TreeItem<RuntimeAsset> {

    private final IDEContextScope scope;

    public AssetTreeItem(
        RuntimeAsset asset,
        Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> graph,
        IDEContextScope scope) {
      super(asset);
      this.scope = scope;
    }

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
          || (!(asset instanceof Observation
                  || asset
                      instanceof
                      KnowledgeGraph
                          .Commit) // TODO eventually this should be correct for all assets
              && scope
                  .getDigitalTwin()
                  .getKnowledgeGraph()
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
      } else if (asset != null
          && (!(asset instanceof Observation) || asset.getChildrenCount() > 0)) {
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
}

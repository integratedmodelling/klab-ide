package org.integratedmodelling.klab.ide.components.treeviews;

import jakarta.annotation.Nullable;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.integratedmodelling.cli.Test;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

import javax.management.relation.RelationType;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class TreeModel {

  /**
   * Create a new AssetTreeItem for the given runtime asset and IDE context scope.
   *
   * @param asset the runtime asset to represent
   * @param scope the IDE context scope
   * @return a new AssetTreeItem root
   */
  public static Pair<AssetTreeItem, AssetTreeItem> createTree(
      RuntimeAsset asset, @Nullable RuntimeAsset focus, IDEContextScope scope) {

    if (asset instanceof KnowledgeGraph.Commit) {
      System.out.println("DIOPOLLO");
    }

    var types = Set.of(RuntimeAsset.Type.OBSERVATION, RuntimeAsset.Type.COHORT);
    var relationships =
        Set.of(GraphModel.Relationship.HAS_CHILD, GraphModel.Relationship.HAS_MEMBER);
    var graph = createGraph(asset, scope.getGraphDepth(), scope, types, relationships);

    var focusItem = new AtomicReference<AssetTreeItem>();
    var tree =
        new AssetTreeItem(
            asset,
            graph,
            true,
            types,
            relationships,
            focusItem,
            focus,
            scope.getGraphDepth(),
            scope);

    return Pair.of(tree, focusItem.get());
  }

  /**
   * Creates a graph representation of the asset's relationships up to the specified depth, properly
   * handled commits as runtime assets that are not in the graph. Add all assets and relationships
   * and possibly filter later. Only the elements that are in the knowledge graph of the passed
   * scope will be considered. The Graph may be nonlinear if relationships other than HAS_CHILD and
   * HAS_MEMBER are present.
   *
   * @param asset the runtime asset to represent
   * @param depth the maximum depth of relationships to include in the graph. Use depth == 0 for
   *     dynamic graph
   * @param scope the IDE context scope
   * @return a graph representation of the asset's relationships
   */
  public static Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> createGraph(
      RuntimeAsset asset,
      int depth,
      IDEContextScope scope,
      Set<RuntimeAsset.Type> types,
      Set<GraphModel.Relationship> relationships) {
    var ret =
        new DefaultDirectedGraph<RuntimeAsset, ClientKnowledgeGraph.Relationship>(
            ClientKnowledgeGraph.Relationship.class);
    createGraph(asset, depth, scope, types, relationships, ret);
    return ret;
  }

  private static boolean createGraph(
      RuntimeAsset asset,
      int depth,
      IDEContextScope scope,
      Set<RuntimeAsset.Type> types,
      Set<GraphModel.Relationship> relationships,
      Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> graph) {
    graph.addVertex(asset);
    var ret = false;
    if (depth > 0) {
      for (var child : getChildren(asset, scope, types, relationships)) {
        ret = true;
        createGraph(child.getFirst(), depth - 1, scope, types, relationships, graph);
        graph.addEdge(
            asset,
            child.getFirst(),
            new ClientKnowledgeGraph.Relationship(
                child.getSecond(), asset.getId(), child.getFirst().getId(), Metadata.create()));
      }
    }
    return ret;
  }

  /**
   * Get the outgoing related objects of the given asset filtering for the specified types and
   * relationships. Walk the knowledge graph from the scope unless the passed asset is a commit, in
   * which case the strategy picks the results that have been committed and arranges them for best
   * visibility.
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

    var kg = scope.getDigitalTwin().getKnowledgeGraph();
    var ret = new ArrayList<Pair<RuntimeAsset, GraphModel.Relationship>>();

    /*
     * This can only happen at root level, as the commit is not stored in the KG
     */
    if (asset instanceof KnowledgeGraph.Commit commit) {
      // all observations that are children of the asset
      for (var observation : commit.getAddedObservations()) {
        var observationAsset = kg.getAsset(observation, scope, Observation.class);
        if (observationAsset != null && types.contains(observationAsset.classify())) {
          // to be added to the commit, it must not be the child of another observation
          var add = kg.incoming(observationAsset, GraphModel.Relationship.HAS_CHILD).isEmpty();
          if (add) {
            ret.add(Pair.of(observationAsset, GraphModel.Relationship.HAS_CHILD));
          }
        }
      }
      // if the asset is CONTEXT_ASSET and types contains Cohort, we also add any cohorts
      if (asset == RuntimeAsset.CONTEXT_ASSET && types.contains(RuntimeAsset.Type.COHORT)) {
        for (var cohort : commit.getAddedCohorts()) {
          var cohortAsset = kg.getAsset(cohort, scope, Cohort.class);
          if (cohortAsset != null) {
            ret.add(Pair.of(cohortAsset, GraphModel.Relationship.HAS_MEMBER));
          }
        }
      }
    } else {
      for (var diocan :
          kg.getLinks(
              asset,
              GraphModel.Relationship.Direction.OUTGOING,
              scope,
              relationships.toArray(GraphModel.Relationship[]::new))) {
        if (types.contains(diocan.target().classify())) {
          ret.add(Pair.of(diocan.target(), diocan.type()));
        }
      }
    }

    return ret;
  }

  static class AssetTreeItem extends TreeItem<RuntimeAsset> {

    private final IDEContextScope scope;
    private final boolean dynamic;
    private final Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> graph;
    private final Set<RuntimeAsset.Type> types;
    private final Set<GraphModel.Relationship> relationships;
    private final RuntimeAsset focalAsset;
    private final AtomicReference<AssetTreeItem> focus;
    private final int prefillDepth;

    public AssetTreeItem(
        RuntimeAsset asset,
        Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> graph,
        boolean dynamic,
        Set<RuntimeAsset.Type> types,
        Set<GraphModel.Relationship> relationships,
        AtomicReference<AssetTreeItem> focus,
        RuntimeAsset focalAsset,
        int prefillDepth,
        IDEContextScope scope) {
      super(asset);
      this.types = types;
      this.relationships = relationships;
      this.graph = graph;
      this.scope = scope;
      this.dynamic = dynamic;
      this.focalAsset = focalAsset;
      this.focus = focus;
      this.prefillDepth = prefillDepth;
      if (focalAsset != null && asset == focalAsset) {
        focus.set(this);
      }
      prefill();
    }

    private void prefill() {
      if (prefillDepth > 0) {
        getChildren();
      }
    }

    @Override
    public boolean isLeaf() {
      return computeChildren().isEmpty();
    }

    List<RuntimeAsset> computeChildren() {

      var ret = new ArrayList<RuntimeAsset>();

      if (dynamic && prefillDepth <= 0) {
        // TODO use the main kg
      } // TODO else {

      for (var childEdge : graph.outgoingEdgesOf(getValue())) {
        var child = graph.getEdgeTarget(childEdge);
        if (relationships.contains(childEdge.relationship) && types.contains(child.classify())) {
          ret.add(child);
        }
      }

      return ret;
    }

    @Override
    public ObservableList<TreeItem<RuntimeAsset>> getChildren() {

      if (!super.getChildren().isEmpty() && prefillDepth >= 0) {
        return super.getChildren();
      }

      super.getChildren().clear();
      computeChildren()
          .forEach(
              child ->
                  super.getChildren()
                      .add(
                          new AssetTreeItem(
                              child,
                              graph,
                              dynamic,
                              types,
                              relationships,
                              focus,
                              focalAsset,
                              prefillDepth - 1,
                              scope)));

      return super.getChildren();
    }
  }
}

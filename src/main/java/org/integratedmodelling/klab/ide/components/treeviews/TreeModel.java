package org.integratedmodelling.klab.ide.components.treeviews;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
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

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class TreeModel {

  private record AssetKey(RuntimeAsset.Type type, long id) {
    static AssetKey of(RuntimeAsset asset) {
      return new AssetKey(asset.classify(), asset.getId());
    }
  }

  private record AssetTraversal(
      RuntimeAsset asset,
      GraphModel.Relationship relationship,
      GraphModel.Relationship.Direction direction) {}

  /**
   * Create a new AssetTreeItem for the given runtime asset and IDE context scope.
   *
   * @param asset the runtime asset to represent
   * @param scope the IDE context scope
   * @return a new AssetTreeItem root
   */
  public static Pair<AssetTreeItem, AssetTreeItem> createTree(
      RuntimeAsset asset, @Nullable RuntimeAsset focus, IDEContextScope scope) {

    if (!isKnowledgeGraphAsset(asset)) {
      asset = RuntimeAsset.CONTEXT_ASSET;
    }
    if (!isKnowledgeGraphAsset(focus)) {
      focus = null;
    }
    var types = Set.of(RuntimeAsset.Type.OBSERVATION, RuntimeAsset.Type.COHORT);
    var relationships =
        Set.of(
            GraphModel.Relationship.CREATED,
            GraphModel.Relationship.HAS_CHILD,
            GraphModel.Relationship.HAS_MEMBER);
    var graph = createGraph(asset, scope.getGraphDepth(), scope, types, relationships, focus);

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
      Set<GraphModel.Relationship> relationships,
      RuntimeAsset focus) {
    return createGraph(asset, depth, scope, types, relationships, focus, false);
  }

  public static Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> createGraph(
      RuntimeAsset asset,
      int depth,
      IDEContextScope scope,
      Set<RuntimeAsset.Type> types,
      Set<GraphModel.Relationship> relationships,
      RuntimeAsset focus,
      boolean followAllRelationshipDirections) {
    var ret =
        new DefaultDirectedGraph<RuntimeAsset, ClientKnowledgeGraph.Relationship>(
            ClientKnowledgeGraph.Relationship.class);
    var recursiveDirections =
        followAllRelationshipDirections
            ? EnumSet.allOf(GraphModel.Relationship.Direction.class)
            : EnumSet.of(GraphModel.Relationship.Direction.OUTGOING);
    createGraph(
        asset,
        depth,
        scope,
        types,
        relationships,
        ret,
        focus,
        recursiveDirections,
        new HashMap<>(),
        new HashMap<>());
    return ret;
  }

  private static boolean createGraph(
      RuntimeAsset asset,
      int depth,
      IDEContextScope scope,
      Set<RuntimeAsset.Type> types,
      Set<GraphModel.Relationship> relationships,
      Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> graph,
      RuntimeAsset focus,
      Set<GraphModel.Relationship.Direction> recursiveDirections,
      Map<AssetKey, Integer> expandedDepths,
      Map<AssetKey, RuntimeAsset> graphAssets) {
    if (!isKnowledgeGraphAsset(asset)) {
      return false;
    }
    asset = graphAsset(asset, graphAssets);
    graph.addVertex(asset);
    var assetKey = AssetKey.of(asset);
    var expandedDepth = expandedDepths.get(assetKey);
    if (expandedDepth != null && expandedDepth >= depth) {
      return false;
    }
    expandedDepths.put(assetKey, depth);

    var ret = false;
    if (depth > 0) {
      for (var child :
          getTraversals(
              asset, scope, types, relationships, focus, recursiveDirections.size() > 1)) {
        if (!isKnowledgeGraphAsset(child.asset())) {
          continue;
        }
        var childAsset = graphAsset(child.asset(), graphAssets);
        if (sameAsset(asset, childAsset)) {
          // shouldn't happen, but happens
          continue;
        }
        ret = true;
        if (child.direction() == GraphModel.Relationship.Direction.OUTGOING) {
          graph.addVertex(childAsset);
          addEdge(graph, asset, childAsset, child.relationship());
        } else {
          graph.addVertex(childAsset);
          addEdge(graph, childAsset, asset, child.relationship());
        }
        if (recursiveDirections.contains(child.direction())) {
          createGraph(
              childAsset,
              depth - 1,
              scope,
              types,
              relationships,
              graph,
              focus,
              recursiveDirections,
              expandedDepths,
              graphAssets);
        }
      }
    }
    return ret;
  }

  private static RuntimeAsset graphAsset(
      RuntimeAsset asset, Map<AssetKey, RuntimeAsset> graphAssets) {
    return graphAssets.computeIfAbsent(AssetKey.of(asset), key -> asset);
  }

  private static boolean sameAsset(RuntimeAsset first, RuntimeAsset second) {
    if (first == second) {
      return true;
    }
    if (first == null || second == null) {
      return false;
    }
    return first.getId() == second.getId() && first.classify() == second.classify();
  }

  private static void addEdge(
      Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> graph,
      RuntimeAsset source,
      RuntimeAsset target,
      GraphModel.Relationship relationship) {
    var edge =
        new ClientKnowledgeGraph.Relationship(
            relationship, source.getId(), target.getId(), Metadata.create());
    if (!graph.containsEdge(edge) && !graph.containsEdge(source, target)) {
      graph.addEdge(source, target, edge);
    }
  }

  /**
   * Get the outgoing related objects of the given asset filtering for the specified types and
   * relationships. Walk the knowledge graph from the scope unless the passed asset is a commit, in
   * which case the strategy picks the results that have been committed and arranges them for the
   * best visibility.
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
      Set<GraphModel.Relationship> relationships,
      RuntimeAsset focus) {

    return getTraversals(asset, scope, types, relationships, focus, false).stream()
        .map(traversal -> Pair.of(traversal.asset(), traversal.relationship()))
        .toList();
  }

  private static List<AssetTraversal> getTraversals(
      RuntimeAsset asset,
      IDEContextScope scope,
      Set<RuntimeAsset.Type> types,
      Set<GraphModel.Relationship> relationships,
      RuntimeAsset focus,
      boolean includeBothDirections) {

    if (!isKnowledgeGraphAsset(asset)) {
      return List.of();
    }
    var kg = scope.getDigitalTwin().getKnowledgeGraph();
    var ret = new ArrayList<AssetTraversal>();

    /*
     * This can only happen at root level, as the commit is not stored in the KG
     */
    if (asset instanceof KnowledgeGraph.Commit commit) {
      // we only store observations that are at root level in the existing commit, forcing
      // the target observation to be at root level.
      for (var observation : commit.getAddedObservations()) {
        var observationAsset = kg.getAsset(observation, scope, Observation.class);
        if (observationAsset != null && types.contains(observationAsset.classify())) {

          boolean isRoot =
              commit.getAddedLinks().stream()
                  .filter(
                      l ->
                          l.getSecond().equals(observation)
                              && (l.getThird().equals(GraphModel.Relationship.HAS_CHILD.name())
                                  || l.getThird()
                                      .equals(GraphModel.Relationship.HAS_MEMBER.name())))
                  .findAny()
                  .isEmpty();

          // keep the target observation at root level anyway
          isRoot |= (focus != null && observation.equals(focus.getId()));

          if (isRoot) {
            ret.add(
                new AssetTraversal(
                    observationAsset,
                    GraphModel.Relationship.CREATED,
                    GraphModel.Relationship.Direction.OUTGOING));
          }
        }
      }
      if (types.contains(RuntimeAsset.Type.COHORT)) {
        for (var cohort : commit.getAddedCohorts()) {
          var cohortAsset = kg.getAsset(cohort, scope, Cohort.class);
          if (cohortAsset != null) {
            ret.add(
                new AssetTraversal(
                    cohortAsset,
                    GraphModel.Relationship.CREATED,
                    GraphModel.Relationship.Direction.OUTGOING));
          }
        }
      }
    } else {

      for (var link :
          kg.getLinks(
              asset,
              GraphModel.Relationship.Direction.OUTGOING,
              scope,
              relationships.toArray(GraphModel.Relationship[]::new))) {
        if (includesTraversal(
                link.type(), GraphModel.Relationship.Direction.OUTGOING, includeBothDirections)
            && types.contains(link.target().classify())) {
          ret.add(
              new AssetTraversal(
                  link.target(), link.type(), GraphModel.Relationship.Direction.OUTGOING));
        }
      }

      for (var link :
          kg.getLinks(
              asset,
              GraphModel.Relationship.Direction.INCOMING,
              scope,
              relationships.toArray(GraphModel.Relationship[]::new))) {
        if (includesTraversal(
                link.type(), GraphModel.Relationship.Direction.INCOMING, includeBothDirections)
            && types.contains(link.source().classify())) {
          ret.add(
              new AssetTraversal(
                  link.source(), link.type(), GraphModel.Relationship.Direction.INCOMING));
        }
      }
    }

    return ret;
  }

  static boolean isKnowledgeGraphAsset(RuntimeAsset asset) {
    if (asset == null) {
      return false;
    }
    long id = asset.getId();
    return id > 0
        || id == RuntimeAsset.CONTEXT_ASSET_ID
        || id == RuntimeAsset.PROVENANCE_ASSET_ID
        || id == RuntimeAsset.DATAFLOW_ASSET_ID;
  }

  static boolean followsPreferredDirection(
      GraphModel.Relationship relationship, GraphModel.Relationship.Direction traversalDirection) {
    return relationship.direction() == traversalDirection;
  }

  static boolean includesTraversal(
      GraphModel.Relationship relationship,
      GraphModel.Relationship.Direction traversalDirection,
      boolean includeBothDirections) {
    return includeBothDirections || followsPreferredDirection(relationship, traversalDirection);
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
    private boolean updatingChildren = false;

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
      return (dynamic && getValue() instanceof Observation)
          ? getValue().getChildrenCount() == 0
          : computeChildren().isEmpty();
    }

    List<RuntimeAsset> computeChildren() {

      var ret = new ArrayList<RuntimeAsset>();

      int nChildren = 0;
      for (var childEdge : graph.outgoingEdgesOf(getValue())) {
        var child = graph.getEdgeTarget(childEdge);
        if (childEdge.relationship == GraphModel.Relationship.HAS_CHILD) {
          nChildren++;
        }
        if (relationships.contains(childEdge.relationship) && types.contains(child.classify())) {
          ret.add(child);
        }
      }

      if (dynamic && getValue().getChildrenCount() > nChildren) {
        //  fish from the main kg
        for (var asset :
            scope
                .getDigitalTwin()
                .getKnowledgeGraph()
                .getLinks(
                    getValue(),
                    GraphModel.Relationship.Direction.OUTGOING,
                    scope,
                    relationships.toArray(GraphModel.Relationship[]::new))) {
          if (types.contains(asset.target().classify()) && !ret.contains(asset.target())) {
            graph.addVertex(asset.target());
            graph.addEdge(
                getValue(),
                asset.target(),
                new ClientKnowledgeGraph.Relationship(
                    asset.type(), getValue().getId(), asset.target().getId(), Map.of()));
            ret.add(asset.target());
          }
        }
      }

      return ret;
    }

    @Override
    public ObservableList<TreeItem<RuntimeAsset>> getChildren() {

      if (updatingChildren) {
        return super.getChildren();
      }

      if (!super.getChildren().isEmpty() && prefillDepth >= 0) {
        return super.getChildren();
      }

      updatingChildren = true;
      try {
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
      } finally {
        updatingChildren = false;
      }

      return super.getChildren();
    }
  }
}

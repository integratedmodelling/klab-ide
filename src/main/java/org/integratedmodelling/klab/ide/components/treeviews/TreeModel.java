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

  /**
   * Create a new AssetTreeItem for the given runtime asset and IDE context scope.
   *
   * @param asset the runtime asset to represent
   * @param scope the IDE context scope
   * @return a new AssetTreeItem root
   */
  public static Pair<AssetTreeItem, AssetTreeItem> createTree(
          RuntimeAsset asset, @Nullable RuntimeAsset focus, IDEContextScope scope) {

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
    var ret =
        new DefaultDirectedGraph<RuntimeAsset, ClientKnowledgeGraph.Relationship>(
            ClientKnowledgeGraph.Relationship.class);
    createGraph(asset, depth, scope, types, relationships, ret, focus);
    return ret;
  }

  private static boolean createGraph(
      RuntimeAsset asset,
      int depth,
      IDEContextScope scope,
      Set<RuntimeAsset.Type> types,
      Set<GraphModel.Relationship> relationships,
      Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> graph,
      RuntimeAsset focus) {
    graph.addVertex(asset);
    var ret = false;
    if (depth > 0) {
      for (var child : getChildren(asset, scope, types, relationships, focus)) {
        if (asset == child.getFirst()) {
          // shouldn't happen, but happens
          continue;
        }
        ret = true;
        if (child.getSecond().direction() == GraphModel.Relationship.Direction.OUTGOING) {
          createGraph(child.getFirst(), depth - 1, scope, types, relationships, graph, focus);
          graph.addEdge(
              asset,
              child.getFirst(),
              new ClientKnowledgeGraph.Relationship(
                  child.getSecond(), asset.getId(), child.getFirst().getId(), Metadata.create()));
        } else {
          graph.addVertex(child.getFirst());
          graph.addEdge(
              child.getFirst(),
              asset,
              new ClientKnowledgeGraph.Relationship(
                  child.getSecond(), child.getFirst().getId(), asset.getId(), Metadata.create()));
        }
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
      Set<GraphModel.Relationship> relationships,
      RuntimeAsset focus) {

    var kg = scope.getDigitalTwin().getKnowledgeGraph();
    var ret = new ArrayList<Pair<RuntimeAsset, GraphModel.Relationship>>();

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
            ret.add(Pair.of(observationAsset, GraphModel.Relationship.CREATED));
          }
        }
      }
      if (types.contains(RuntimeAsset.Type.COHORT)) {
        for (var cohort : commit.getAddedCohorts()) {
          var cohortAsset = kg.getAsset(cohort, scope, Cohort.class);
          if (cohortAsset != null) {
            ret.add(Pair.of(cohortAsset, GraphModel.Relationship.CREATED));
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
        if (types.contains(link.target().classify())) {
          ret.add(Pair.of(link.target(), link.type()));
        }
      }

      for (var link :
          kg.getLinks(
              asset,
              GraphModel.Relationship.Direction.INCOMING,
              scope,
              relationships.toArray(GraphModel.Relationship[]::new))) {
        if (types.contains(link.source().classify())) {
          ret.add(Pair.of(link.source(), link.type()));
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

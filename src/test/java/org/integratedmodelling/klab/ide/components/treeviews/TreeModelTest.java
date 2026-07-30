package org.integratedmodelling.klab.ide.components.treeviews;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class TreeModelTest {

  @Test
  void unresolvedAndQueryAssetsAreNeverGraphAddressable() {
    var unresolved = new ObservationImpl();
    unresolved.setId(-1);
    var query = new ObservationImpl();
    query.setId(0);
    var resolved = new ObservationImpl();
    resolved.setId(1);

    assertFalse(TreeModel.isKnowledgeGraphAsset(unresolved));
    assertFalse(TreeModel.isKnowledgeGraphAsset(query));
    assertTrue(TreeModel.isKnowledgeGraphAsset(resolved));
    assertTrue(TreeModel.isKnowledgeGraphAsset(RuntimeAsset.CONTEXT_ASSET));
  }

  @Test
  void hierarchyAndMembershipAreOnlyTraversedFromTheirSource() {
    assertTrue(
        TreeModel.followsPreferredDirection(
            GraphModel.Relationship.HAS_CHILD, GraphModel.Relationship.Direction.OUTGOING));
    assertFalse(
        TreeModel.followsPreferredDirection(
            GraphModel.Relationship.HAS_CHILD, GraphModel.Relationship.Direction.INCOMING));
    assertTrue(
        TreeModel.followsPreferredDirection(
            GraphModel.Relationship.HAS_MEMBER, GraphModel.Relationship.Direction.OUTGOING));
    assertFalse(
        TreeModel.followsPreferredDirection(
            GraphModel.Relationship.HAS_MEMBER, GraphModel.Relationship.Direction.INCOMING));
  }

  @Test
  void passiveRelationshipsAreTraversedFromTheirTarget() {
    assertTrue(
        TreeModel.followsPreferredDirection(
            GraphModel.Relationship.AFFECTS, GraphModel.Relationship.Direction.INCOMING));
    assertFalse(
        TreeModel.followsPreferredDirection(
            GraphModel.Relationship.AFFECTS, GraphModel.Relationship.Direction.OUTGOING));
  }

  @Test
  void graphExplorationCanFollowBothPhysicalDirections() {
    assertTrue(
        TreeModel.includesTraversal(
            GraphModel.Relationship.HAS_MEMBER,
            GraphModel.Relationship.Direction.INCOMING,
            true));
    assertTrue(
        TreeModel.includesTraversal(
            GraphModel.Relationship.CONTEXTUALIZED_BY,
            GraphModel.Relationship.Direction.OUTGOING,
            true));
  }
}

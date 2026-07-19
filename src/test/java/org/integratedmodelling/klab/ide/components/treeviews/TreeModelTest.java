package org.integratedmodelling.klab.ide.components.treeviews;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.junit.jupiter.api.Test;

class TreeModelTest {

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

package org.integratedmodelling.klab.ide.components.cards;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;

/**
 * TODO shows the related assets from the knowledge graph, optionally filtered by direction, along
 * with the relationship type.
 */
public class RelationshipCard extends BaseCard<RuntimeAsset> {

  public RelationshipCard(RuntimeAsset asset, GraphModel.Relationship.Direction... directions) {
    super(asset, true);
  }

  @Override
  protected void drawContent() {}
}

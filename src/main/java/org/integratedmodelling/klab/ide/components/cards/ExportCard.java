package org.integratedmodelling.klab.ide.components.cards;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;

/** TODO shows the available export options for the asset and enables triggering export actions. */
public class ExportCard extends BaseCard<RuntimeAsset> {

  public ExportCard(RuntimeAsset asset, GraphModel.Relationship.Direction... directions) {
    super(asset, true);
  }

  @Override
  protected void drawContent() {}
}

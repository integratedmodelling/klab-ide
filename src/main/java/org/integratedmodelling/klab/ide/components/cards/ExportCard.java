package org.integratedmodelling.klab.ide.components.cards;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.ide.IDEContextScope;

/** TODO shows the available export options for the asset and enables triggering export actions. */
public class ExportCard extends BaseCard<RuntimeAsset> {

  public ExportCard(
      RuntimeAsset asset, IDEContextScope scope, GraphModel.Relationship.Direction... directions) {
    super(asset, scope, true);
  }

  @Override
  protected void drawContent() {}
}

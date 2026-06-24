package org.integratedmodelling.klab.ide.components.cards;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.IDEContextScope;

import java.io.File;

public class ValueCard extends BaseCard<Observation> {

  public ValueCard(Observation asset, IDEContextScope scope, boolean extended) {
    super(asset, scope, extended);
  }

  @Override
  protected void drawContent() {
    File imageUrl =
        Utils.Files.copyInputStreamToTempFile(
            scope
                .getService(RuntimeService.class)
                .exportAsset(
                    asset.getUrn(),
                    KlabAsset.KnowledgeClass.classify(asset.getClass()),
                    "image/png",
                    Parameters.create("viewportX", 800, "viewportY", 800),
                    scope),
            "png");
  }
}

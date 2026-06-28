package org.integratedmodelling.klab.ide.components.cards;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.IDEContextScope;

public class ValueCard extends BaseCard<Observation> {

  private static Map<Observation, File> imageCache = new ConcurrentHashMap<>();

  private File imageUrl;

  public ValueCard(Observation asset, IDEContextScope scope, boolean extended) {
    super(asset, scope, extended);
    this.imageUrl =
        imageCache.computeIfAbsent(
            asset,
            a ->
                Utils.Files.copyInputStreamToTempFile(
                    scope
                        .getService(RuntimeService.class)
                        .exportAsset(
                            a.getUrn(),
                            KlabAsset.KnowledgeClass.classify(a.getClass()),
                            "image/png",
                            Parameters.create("viewportX", 800, "viewportY", 800),
                            scope),
                    "png"));
  }

  @Override
  protected void drawContent() {

  }
}

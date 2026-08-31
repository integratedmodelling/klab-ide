package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.junit.jupiter.api.Test;

class ResourcesViewSearchTest {

  @Test
  void browserKeepsOnlyTheNewestResultForEachUrn() {
    var resources = new LinkedHashMap<String, ResourceInfo>();
    ResourcesView.mergeResourceInfo(resources, info("1.0.0", "Old label"));
    ResourcesView.mergeResourceInfo(resources, info("1.1.0", "New label"));

    assertEquals(1, resources.size());
    assertEquals(
        "New label",
        resources.values().iterator().next().getMetadata().get(Metadata.DC_LABEL));
  }

  private static ResourceInfo info(String version, String label) {
    var info = new ResourceInfo();
    info.setUrn("resources.test:org:data:item");
    info.getMetadata().put("im:version", version);
    info.getMetadata().put(Metadata.DC_LABEL, label);
    return info;
  }
}

package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.junit.jupiter.api.Test;

class ResourceSmallViewComponentTest {

  @Test
  void usesCatalogNameAndDescriptionMetadataInPriorityOrder() {
    var info = resourceInfo();
    info.getMetadata().put(Metadata.DC_NAME, "Fallback name");
    info.getMetadata().put(Metadata.DC_TITLE, "Catalog title");
    info.getMetadata().put(Metadata.DC_COMMENT, "Fallback comment");
    info.getMetadata().put(Metadata.DC_DESCRIPTION, "Catalog description");

    assertEquals("Catalog title", ResourceSmallViewComponent.displayName(info));
    assertEquals("Catalog description", ResourceSmallViewComponent.description(info));
  }

  @Test
  void fallsBackToUrnAndShowsOwnerAdapterAndAvailability() {
    var info = resourceInfo();
    info.setType(ResourceInfo.Type.AVAILABLE);
    info.setOwner("alice");
    info.getMetadata().put("im:adapter", "netcdf");

    assertEquals(info.getUrn(), ResourceSmallViewComponent.displayName(info));
    assertEquals("No description available", ResourceSmallViewComponent.description(info));
    assertEquals("available · by alice · netcdf", ResourceSmallViewComponent.details(info));
  }

  private static ResourceInfo resourceInfo() {
    var info = new ResourceInfo();
    info.setUrn("local:alice:climate:temperature");
    return info;
  }
}

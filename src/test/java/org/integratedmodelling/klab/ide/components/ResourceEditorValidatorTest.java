package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.impl.AttributeImpl;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

class ResourceEditorValidatorTest {

  @Test
  void acceptsCompleteResource() {
    var resource = completeResource();
    assertTrue(ResourceEditorValidator.validate(resource, "resources.one", new Adapter.Parameter[0]).valid());
  }

  @Test
  void acceptsCasePreservingUrnTokensAllowedByTheUrnContract() {
    var resource = completeResource();
    resource.setUrn("resources.one:ExampleOrg:Climate_Data:Temperature-2026");

    assertTrue(ResourceEditorValidator.validate(resource, "resources.one", new Adapter.Parameter[0]).valid());
  }

  @Test
  void locatesIdentityParameterLicenseAndInterfaceErrors() {
    var resource = completeResource();
    resource.setUrn("another.service:im:data:temperature");
    resource.getMetadata().put(ResourceEditorValidator.LICENSE_ID, "custom");
    resource.getMetadata().remove(ResourceEditorValidator.LICENSE_TEXT);

    var attribute = new AttributeImpl();
    attribute.setName("value");
    attribute.setType(Artifact.Type.NUMBER);
    attribute.setKey(true);
    attribute.setOptional(true);
    resource.setAttributes(List.of(attribute));

    Adapter.Parameter required =
        new Adapter.Parameter() {
          public String getName() { return "url"; }
          public Artifact.Type getType() { return Artifact.Type.TEXT; }
          public List<String> getEnumValues() { return List.of(); }
          public String getDescription() { return "Source URL"; }
          public boolean isOptional() { return false; }
        };

    var result = ResourceEditorValidator.validate(resource, "resources.one", new Adapter.Parameter[] {required});
    assertFalse(result.valid());
    assertFalse(result.valid(ResourceEditorValidator.Section.OVERVIEW));
    assertFalse(result.valid(ResourceEditorValidator.Section.PARAMETERS));
    assertFalse(result.valid(ResourceEditorValidator.Section.LICENSE));
    assertFalse(result.valid(ResourceEditorValidator.Section.INTERFACE));
  }

  private static ResourceImpl completeResource() {
    var resource = new ResourceImpl();
    resource.setUrn("resources.one:im:data:temperature");
    resource.setServiceId("resources.one");
    resource.setAdapterType("table");
    resource.setVersion(new Version("1.0.0"));
    resource.setType(Artifact.Type.NUMBER);
    resource.setGeometry(Geometry.UNIVERSAL);
    resource.getMetadata().put(Metadata.DC_LABEL, "Temperature");
    resource.getMetadata().put(Metadata.DC_ORIGINATOR, "Example institute");
    resource.getMetadata().put(ResourceEditorValidator.CONTACT, "data@example.org");
    resource.getMetadata().put(Metadata.DC_DESCRIPTION, "A complete test resource");
    resource.getMetadata().put(ResourceEditorValidator.LICENSE_ID, "CC-BY-4.0");
    resource.getMetadata().put(ResourceEditorValidator.USAGE, "Open");
    return resource;
  }
}

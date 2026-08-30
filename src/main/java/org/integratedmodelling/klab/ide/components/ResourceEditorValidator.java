package org.integratedmodelling.klab.ide.components;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;

/** Fast client-side validation used while a resource is being edited. */
public final class ResourceEditorValidator {

  public static final String CONTACT = "dc:contact";
  public static final String LICENSE_ID = "dc:license";
  public static final String LICENSE_TEXT = "im:license-text";
  public static final String LICENSE_URL = "im:license-url";
  public static final String USAGE = "im:usage";
  public static final String PERMISSIONS = "im:permissions";

  private static final Pattern URN_PART = Pattern.compile("[a-z0-9][a-z0-9._-]*");

  public enum Section {
    OVERVIEW,
    GEOMETRY,
    INTERFACE,
    PARAMETERS,
    METADATA,
    LICENSE,
    PERMISSIONS,
    FILES,
    WORKFLOWS,
    HISTORY
  }

  public record Issue(Section section, String field, String message) {}

  public record Result(List<Issue> issues) {
    public Result {
      issues = List.copyOf(issues);
    }

    public boolean valid() {
      return issues.isEmpty();
    }

    public boolean valid(Section section) {
      return issues.stream().noneMatch(issue -> issue.section() == section);
    }

    public List<Issue> forSection(Section section) {
      return issues.stream().filter(issue -> issue.section() == section).toList();
    }
  }

  private ResourceEditorValidator() {}

  public static Result validate(
      Resource resource, String expectedServiceId, Adapter.Parameter[] adapterParameters) {
    List<Issue> issues = new ArrayList<>();
    if (resource == null) {
      issues.add(new Issue(Section.OVERVIEW, "resource", "No resource is loaded"));
      return new Result(issues);
    }

    validateUrn(resource.getUrn(), expectedServiceId, issues);
    required(resource.getAdapterType(), Section.OVERVIEW, "adapter", "Choose an adapter", issues);
    if (resource.getVersion() == null) {
      issues.add(new Issue(Section.OVERVIEW, "version", "Specify a semantic version"));
    }
    if (resource.getType() == null) {
      issues.add(new Issue(Section.OVERVIEW, "type", "Specify the produced data type"));
    }
    Geometry geometry = resource.getGeometry();
    if (geometry == null || geometry.encode().isBlank() || "X".equals(geometry.encode())) {
      issues.add(new Issue(Section.GEOMETRY, "geometry", "Specify a usable geometry"));
    }

    validateAttributes(resource.getAttributes(), "attribute", issues);
    validateAttributes(resource.getInputs(), "input", issues);
    validateAttributes(resource.getOutputs(), "output", issues);
    validateParameters(resource, adapterParameters, issues);
    validateMetadata(resource.getMetadata(), issues);
    validateFiles(resource.getLocalFiles(), issues);
    return new Result(issues);
  }

  private static void validateUrn(String urn, String serviceId, List<Issue> issues) {
    required(urn, Section.OVERVIEW, "urn", "Specify the resource URN", issues);
    if (urn == null || urn.isBlank()) return;
    String base = urn.startsWith("urn:klab:") ? urn.substring("urn:klab:".length()) : urn;
    base = base.split("#", 2)[0].split("@", 2)[0];
    String[] parts = base.split(":", -1);
    if (parts.length != 4) {
      issues.add(
          new Issue(
              Section.OVERVIEW,
              "urn",
              "Use service:originator:namespace:resource-id"));
      return;
    }
    for (int i = 0; i < parts.length; i++) {
      if (!URN_PART.matcher(parts[i]).matches()) {
        issues.add(
            new Issue(
                Section.OVERVIEW,
                "urn",
                "URN fields must use lowercase letters, numbers, '.', '_' or '-'"));
        break;
      }
    }
    if (serviceId != null && !serviceId.isBlank() && !serviceId.equals(parts[0])) {
      issues.add(
          new Issue(
              Section.OVERVIEW,
              "urn",
              "The first URN field must be the hosting service ID '" + serviceId + "'"));
    }
  }

  private static void validateAttributes(
      Collection<Resource.Attribute> attributes, String kind, List<Issue> issues) {
    if (attributes == null) return;
    Set<String> names = new HashSet<>();
    for (Resource.Attribute attribute : attributes) {
      if (attribute == null || attribute.getName() == null || attribute.getName().isBlank()) {
        issues.add(new Issue(Section.INTERFACE, kind, "Every " + kind + " needs a name"));
        continue;
      }
      String normalized = attribute.getName().toLowerCase(Locale.ROOT);
      if (!URN_PART.matcher(normalized).matches()) {
        issues.add(
            new Issue(
                Section.INTERFACE,
                kind,
                "'" + attribute.getName() + "' is not a valid identifier"));
      }
      if (!names.add(normalized)) {
        issues.add(
            new Issue(
                Section.INTERFACE, kind, "Duplicate " + kind + " name '" + attribute.getName() + "'"));
      }
      if (attribute.getType() == null) {
        issues.add(
            new Issue(
                Section.INTERFACE, kind, "Choose a data type for '" + attribute.getName() + "'"));
      }
      if (attribute.isKey() && attribute.isOptional()) {
        issues.add(
            new Issue(
                Section.INTERFACE,
                kind,
                "Key '" + attribute.getName() + "' cannot be optional"));
      }
    }
  }

  private static void validateParameters(
      Resource resource, Adapter.Parameter[] parameters, List<Issue> issues) {
    if (parameters == null) return;
    for (Adapter.Parameter parameter : parameters) {
      if (parameter == null || parameter.isOptional()) continue;
      Object value = resource.getParameters().get(parameter.getName());
      if (value == null || value.toString().isBlank()) {
        issues.add(
            new Issue(
                Section.PARAMETERS,
                parameter.getName(),
                "Required adapter parameter '" + parameter.getName() + "' is missing"));
      }
    }
  }

  private static void validateMetadata(Metadata metadata, List<Issue> issues) {
    if (metadata == null) {
      issues.add(new Issue(Section.METADATA, "metadata", "Metadata are missing"));
      return;
    }
    required(metadata.get(Metadata.DC_LABEL), Section.METADATA, "label", "Add a label", issues);
    required(
        metadata.get(Metadata.DC_ORIGINATOR),
        Section.METADATA,
        "originator",
        "Add the data originator",
        issues);
    required(
        metadata.get(CONTACT),
        Section.METADATA,
        "contact",
        "Add a reference contact",
        issues);
    required(
        metadata.get(Metadata.DC_DESCRIPTION),
        Section.METADATA,
        "description",
        "Add a description",
        issues);
    required(
        metadata.get(LICENSE_ID),
        Section.LICENSE,
        "license",
        "Choose or describe a license",
        issues);
    required(
        metadata.get(USAGE),
        Section.LICENSE,
        "usage",
        "State whether usage is open or restricted",
        issues);
    if ("custom".equals(metadata.get(LICENSE_ID))) {
      required(
          metadata.get(LICENSE_TEXT),
          Section.LICENSE,
          "licenseText",
          "Provide the custom license text",
          issues);
    }
  }

  private static void validateFiles(Collection<File> files, List<Issue> issues) {
    if (files == null) return;
    for (File file : files) {
      if (file == null || !file.isFile() || !file.canRead()) {
        issues.add(
            new Issue(
                Section.FILES,
                "file",
                "A local resource file is missing or unreadable: " + (file == null ? "null" : file)));
      }
    }
  }

  private static void required(
      Object value, Section section, String field, String message, List<Issue> issues) {
    if (value == null || value.toString().isBlank()) {
      issues.add(new Issue(section, field, message));
    }
  }
}

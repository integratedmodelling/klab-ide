package org.integratedmodelling.klab.ide.components;

import java.nio.file.Files;
import java.util.Objects;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;

/** Transfers source coordinates or project bytes; remote Git working trees are never copied. */
final class LocalProjectExport {
  private LocalProjectExport() {}

  static boolean present(ResourcesService local, String urn, UserScope user) {
    return local.list(Project.class, user).stream()
        .anyMatch(project -> Objects.equals(urn, project.getUrn()));
  }

  static String gitCoordinates(Project project) {
    var state = project.getRepositoryState();
    if (state == null || state.getOverallStatus() == org.integratedmodelling.klab.api.data.RepositoryState.Status.UNTRACKED)
      return null;
    if (state.getRepositoryUrl() == null)
      throw new IllegalStateException("The remote service did not provide Git coordinates for this tracked project");
    var url = state.getRepositoryUrl();
    if (!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol())
        || url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null)
      throw new IllegalArgumentException("Project Git coordinates must be an HTTP(S) URL without credentials");
    return url.toExternalForm()
        + (state.getCurrentBranch() == null || state.getCurrentBranch().isBlank()
            ? "" : "#" + state.getCurrentBranch());
  }

  static ResourceSet transfer(ResourcesService remote, ResourcesService local, Project project,
      String workspace, UserScope user) throws Exception {
    if (!local.isLocal() || local.status() == null || !local.status().isOperational())
      throw new IllegalStateException("A local resources service must be active");
    if (present(local, project.getUrn(), user))
      throw new IllegalStateException("Project is already present locally");
    var coordinates = gitCoordinates(project);
    if (coordinates != null) {
      var schema = schema(local, "project.git.import", user);
      return local.importAsset(schema,
          schema.asset("url", coordinates, "workspace", workspace, "project", project.getUrn()),
          Urn.UNDEFINED_URN, user).get();
    }
    var schema = schema(local, "project.zip.import", user);
    var archive = Files.createTempFile("klab-project-", ".zip");
    try {
      try (var stream = remote.exportAsset(project.getUrn(), KnowledgeClass.PROJECT,
          "application/zip", Parameters.create(), user)) {
        if (stream == null) throw new IllegalStateException("Remote service returned no project archive");
        Files.copy(stream, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      return local.importAsset(schema, schema.asset(archive.toFile()), project.getUrn(), user).get();
    } finally {
      Files.deleteIfExists(archive);
    }
  }

  private static ResourceTransport.Schema schema(ResourcesService local, String id, UserScope user) {
    return local.capabilities(user).getImportSchemata().values().stream().flatMap(java.util.List::stream)
        .filter(schema -> id.equals(schema.getSchemaId())).findFirst()
        .orElseThrow(() -> new IllegalStateException("Local resources service does not support " + id));
  }
}

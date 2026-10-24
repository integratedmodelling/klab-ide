package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.*;
import org.junit.jupiter.api.Test;

class LocalProjectExportTest {
  @Test void trackedProjectImportsCoordinatesAndBranchWithoutExportingRemoteEditsOrCredentials() throws Exception {
    var local = local(); var remote = mock(ResourcesService.class); var user = mock(UserScope.class);
    var project = project();
    project.getRepositoryState().setRepositoryUrl(java.net.URI.create("https://example.org/im.examples.git").toURL());
    project.getRepositoryState().setOverallStatus(RepositoryState.Status.CLEAN);
    project.getRepositoryState().setCurrentBranch("feature/inspection");
    var schema = schema(local, user, "project.git.import", ResourceTransport.Schema.Type.PROPERTIES);
    var result = new ResourceSet();
    when(local.importAsset(same(schema), any(), any(), same(user))).thenAnswer(call -> {
      ResourceTransport.Schema.Asset asset = call.getArgument(1);
      assertEquals(Map.of("url", "https://example.org/im.examples.git#feature/inspection",
          "workspace", "remote-workspace", "project", "im.examples"), asset.getProperties());
      return CompletableFuture.completedFuture(result);
    });
    assertSame(result, LocalProjectExport.transfer(remote, local, project, "remote-workspace", user));
    verifyNoInteractions(remote);
  }

  @Test void untrackedProjectTransfersBytesAndRemovesTemporaryArchiveAfterUpload() throws Exception {
    var local = local(); var remote = mock(ResourcesService.class); var user = mock(UserScope.class);
    var schema = schema(local, user, "project.zip.import", ResourceTransport.Schema.Type.STREAM);
    var uploaded = new java.util.concurrent.atomic.AtomicReference<java.nio.file.Path>();
    byte[] bytes = {0, -1, 7};
    when(remote.exportAsset(eq("im.examples"), eq(KnowledgeClass.PROJECT), eq("application/zip"), any(), same(user)))
        .thenReturn(new ByteArrayInputStream(bytes));
    when(local.importAsset(same(schema), any(), eq("im.examples"), same(user))).thenAnswer(call -> {
      ResourceTransport.Schema.Asset asset = call.getArgument(1); uploaded.set(asset.getFile().toPath());
      assertArrayEquals(bytes, Files.readAllBytes(uploaded.get()));
      return CompletableFuture.completedFuture(new ResourceSet());
    });
    LocalProjectExport.transfer(remote, local, project(), "remote-workspace", user);
    assertFalse(Files.exists(uploaded.get()));
  }

  @Test void rejectsDuplicateAndInactiveTargetsBeforeImportOrExport() {
    var local = local(); var remote = mock(ResourcesService.class); var user = mock(UserScope.class);
    when(local.list(Project.class, user)).thenReturn(List.of(project()));
    assertThrows(IllegalStateException.class, () -> LocalProjectExport.transfer(remote, local, project(), "workspace", user));
    when(local.status().isOperational()).thenReturn(false);
    assertThrows(IllegalStateException.class, () -> LocalProjectExport.transfer(remote, local, project(), "workspace", user));
    verifyNoInteractions(remote);
    verify(local, never()).importAsset(any(), any(), any(), any());
  }

  private static ResourcesService local() {
    var local = mock(ResourcesService.class, RETURNS_DEEP_STUBS);
    when(local.isLocal()).thenReturn(true); when(local.status().isOperational()).thenReturn(true);
    return local;
  }
  private static ProjectImpl project() {
    var project = new ProjectImpl(); project.setUrn("im.examples");
    project.setRepositoryState(new RepositoryState()); return project;
  }
  private static ResourceTransport.Schema schema(ResourcesService local, UserScope user, String id, ResourceTransport.Schema.Type type) {
    var schema = ResourceTransport.Schema.create(id, type, KnowledgeClass.PROJECT, "test");
    when(local.capabilities(user).getImportSchemata()).thenReturn(Map.of("project", List.of(schema)));
    return schema;
  }
}

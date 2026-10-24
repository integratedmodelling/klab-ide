package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.junit.jupiter.api.Test;

class ProjectMaterialBrowserTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path external;

  @Test void importsBinaryFilesWithoutChangingTheirSourceAndOnlyIntoUnmanagedFolders() throws Exception {
    var source = external.resolve("image.bin");
    byte[] bytes = {0, -1, 42};
    java.nio.file.Files.write(source, bytes);
    var material = ProjectMaterialBrowser.importedFile("project", "assets", source);
    assertEquals("project/assets/image.bin", material.getUrn());
    assertArrayEquals(bytes, material.getContent());
    assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(source));
    assertThrows(IllegalArgumentException.class, () -> ProjectMaterialBrowser.importedFile("project", "", source));
    assertThrows(IllegalArgumentException.class, () -> ProjectMaterialBrowser.importedFile("project", "apps", source));
    assertThrows(java.io.IOException.class, () -> ProjectMaterialBrowser.importedFile("project", "assets", external));
    for (String internal : java.util.List.of(".state.bin", "_private.bin", "-temporary.bin", "éfile.bin", "١file.bin")) {
      var hidden = external.resolve(internal);
      java.nio.file.Files.write(hidden, bytes);
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterialBrowser.importedFile("project", "assets", hidden));
    }
  }
  @Test void readOnlyAndDeleteOnlyUsersCannotDiscoverMaterialFolders() {
    var info = new ResourceInfo();
    info.setPermissions(EnumSet.of(CRUDOperation.READ, CRUDOperation.DELETE));
    assertFalse(ProjectMaterialBrowser.canBrowse(info));
    assertFalse(ProjectMaterialBrowser.canBrowse(null));
    for (var grant : EnumSet.of(CRUDOperation.CREATE, CRUDOperation.UPDATE,
        CRUDOperation.UPDATE_METADATA, CRUDOperation.ADMINISTER)) {
      info.setPermissions(EnumSet.of(grant));
      assertTrue(ProjectMaterialBrowser.canBrowse(info));
    }
    info.setPermissions(EnumSet.of(CRUDOperation.CREATE));
    assertFalse(ProjectMaterialBrowser.canWrite(info));
  }

  @Test void creationAndImportRejectReservedFoldersAndPathAliases() {
    for (var reserved : java.util.List.of("src", "apps", "scripts", "testcases", "behaviors",
        "strategies", "resources", "META-INF", "docs", "components", "SRC")) {
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterialBrowser.childPath("", reserved));
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterialBrowser.childPath(reserved, "style.css"));
    }
    for (var name : java.util.List.of("../file.css", "a/b", "a\\b", "..", "CON", ".git", "file ",
        "_cache", "-private", ".settings", "éfile", "١file", "中文"))
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterialBrowser.childPath("assets", name));
    assertThrows(IllegalArgumentException.class, () -> ProjectMaterialBrowser.childPath("assets/_private", "file.txt"));
    assertEquals("assets/0-icons", ProjectMaterialBrowser.childPath("assets", "0-icons"));
    assertEquals("assets/Aé.css", ProjectMaterialBrowser.childPath("assets", "Aé.css"));
    assertEquals("assets/locales/messages_es.properties", ProjectMaterialBrowser.childPath("assets/locales", "messages_es.properties"));
  }

  @Test void textEditingPreservesUtf8AndNeverDecodesBinaryOrMalformedContent() {
    String source = "body { content: 'España'; }\r\n";
    assertEquals(source, ProjectMaterialBrowser.editableText(source.getBytes(StandardCharsets.UTF_8)));
    assertEquals("", ProjectMaterialBrowser.editableText(new byte[0]));
    assertNull(ProjectMaterialBrowser.editableText(new byte[] {0, 1, 2}));
    assertNull(ProjectMaterialBrowser.editableText(new byte[] {(byte) 0xff}));
    assertNull(ProjectMaterialBrowser.editableText(new byte[2 * 1024 * 1024 + 1]));
  }
}

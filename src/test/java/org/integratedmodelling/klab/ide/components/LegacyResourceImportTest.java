package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyResourceImportTest {
  @TempDir Path directory;

  @Test
  void discoversNestedManifestsWithoutFlatteningOrDuplicatingOverlappingFolders() throws Exception {
    var first = Files.createDirectories(directory.resolve("a"));
    var second = Files.createDirectories(directory.resolve("b/nested"));
    var a = Files.writeString(first.resolve("resource.json"), "{}").toFile();
    var b = Files.writeString(second.resolve("resource.json"), "{}").toFile();
    Files.writeString(first.resolve("codelist.json"), "{}");
    assertEquals(List.of(a, b), LegacyResourceImport.discover(List.of(directory.toFile(), first.toFile())));
  }

  @Test
  void emptyFolderAndUnavailableFolderAreDistinguished() throws Exception {
    assertTrue(LegacyResourceImport.discover(List.of(directory.toFile())).isEmpty());
    assertThrows(java.io.IOException.class,
        () -> LegacyResourceImport.discover(List.of(directory.resolve("missing").toFile())));
  }

  @Test
  void submitsInSequenceAndContinuesAfterBothServiceErrorsAndExceptions() {
    var files = List.of(new File("a/resource.json"), new File("b/resource.json"), new File("c/resource.json"));
    var calls = new ArrayList<File>();
    var updates = new ArrayList<LegacyResourceImport.Outcome>();
    LegacyResourceImport.run(files, file -> {
      // The preceding request must have produced its terminal result before the next starts.
      assertEquals(calls.size() * 2 + 1, updates.size());
      calls.add(file);
      if (file.equals(files.get(0))) return ResourceSet.empty(Notification.error("Missing adapter"));
      if (file.equals(files.get(1))) throw new IllegalStateException("Cannot parse definition");
      return imported("new:urn");
    }, updates::add);
    assertEquals(files, calls);
    assertEquals(LegacyResourceImport.Status.ERROR, updates.get(1).status());
    assertEquals("Missing adapter", updates.get(1).explanation());
    assertEquals(LegacyResourceImport.Status.ERROR, updates.get(3).status());
    assertEquals("Cannot parse definition", updates.get(3).explanation());
    assertEquals(LegacyResourceImport.Status.SUCCESS, updates.get(5).status());
    assertEquals("new:urn", updates.get(5).urn());
  }

  @Test
  void preservesWarningsForSuccessfulImports() {
    var result = imported("new:urn");
    result.getNotifications().add(Notification.warning("Metadata only"));
    var outcome = LegacyResourceImport.outcome(new File("resource.json"), result);
    assertEquals(LegacyResourceImport.Status.SUCCESS, outcome.status());
    assertEquals("Metadata only", outcome.explanation());
  }

  @Test
  void nestedErrorsAndMissingUrnsNeverAppearSuccessful() {
    var file = new File("resource.json");
    var result = imported("new:urn");
    result.getResources().getFirst().getNotifications().add(Notification.error("Invalid geometry"));
    var outcome = LegacyResourceImport.outcome(file, result);
    assertEquals(LegacyResourceImport.Status.ERROR, outcome.status());
    assertEquals("Invalid geometry", outcome.explanation());
    assertEquals("", outcome.urn());
    assertEquals(LegacyResourceImport.Status.ERROR, LegacyResourceImport.outcome(file, null).status());
    assertEquals(LegacyResourceImport.Status.ERROR, LegacyResourceImport.outcome(file, new ResourceSet()).status());
  }

  @Test
  void acceptsResourceDescriptorsReturnedInResults() {
    var result = imported("new:urn");
    result.getResults().addAll(result.getResources());
    result.getResources().clear();
    assertEquals("new:urn", LegacyResourceImport.outcome(new File("resource.json"), result).urn());
  }

  private static ResourceSet imported(String urn) {
    var result = new ResourceSet();
    var resource = new ResourceSet.Resource();
    resource.setKnowledgeClass(KlabAsset.KnowledgeClass.RESOURCE);
    resource.setResourceUrn(urn);
    result.getResources().add(resource);
    return result;
  }
}

package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagedBehaviorMirrorsTest {

  @TempDir Path temporaryDirectory;

  @Test
  void repeatedCheckoutPreservesUnsubmittedLocalChanges() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var checkout = mirrors.checkout("service", "project", "behavior.one", "remote v1");
    Files.writeString(checkout.file(), "local work");

    var repeated = mirrors.checkout("service", "project", "behavior.one", "remote v2");

    assertFalse(repeated.created());
    assertEquals(checkout.file(), repeated.file());
    assertEquals("local work", Files.readString(repeated.file()));
    assertEquals("project", mirrors.origin(repeated.file()).orElseThrow().projectUrn());
  }

  @Test
  void cleanMirrorTracksRemoteUpdates() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var checkout = mirrors.checkout("service", "project", "behavior.one", "remote v1");

    var result = mirrors.synchronize("service", "project", "behavior.one", "remote v2");

    assertEquals(ManagedBehaviorMirrors.Synchronization.UPDATED, result);
    assertEquals("remote v2", Files.readString(checkout.file()));
    assertTrue(mirrors.origin(checkout.file()).isPresent());
  }

  @Test
  void dirtyMirrorRejectsRemoteOverwriteUntilSubmitted() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var checkout = mirrors.checkout("service", "project", "behavior.one", "remote v1");
    Files.writeString(checkout.file(), "local work");

    var result = mirrors.synchronize("service", "project", "behavior.one", "remote v2");

    assertEquals(ManagedBehaviorMirrors.Synchronization.DIRTY, result);
    assertEquals("local work", Files.readString(checkout.file()));

    mirrors.markSynchronized(checkout.file(), "local work");
    assertEquals(
        ManagedBehaviorMirrors.Synchronization.UNCHANGED,
        mirrors.synchronize("service", "project", "behavior.one", "local work"));
  }

  @Test
  void renamedOriginIsFoundAndReusedAtItsExistingLocalPath() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var checkout = mirrors.checkout("service", "project", "behavior.old", "remote v1");

    mirrors.markSynchronized(checkout.file(), "behavior.new", "remote v2");
    var repeated = mirrors.checkout("service", "project", "behavior.new", "remote v3");

    assertFalse(repeated.created());
    assertEquals(checkout.file(), repeated.file());
    assertEquals("behavior.new", repeated.origin().behaviorUrn());
    assertEquals(checkout.file(), mirrors.pathFor("service", "project", "behavior.new"));
  }
}

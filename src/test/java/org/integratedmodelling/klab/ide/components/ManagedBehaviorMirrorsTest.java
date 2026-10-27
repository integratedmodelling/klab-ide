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

  @Test void serviceTimestampsPersistAndRejectOlderSnapshotsForEveryBehaviorType() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    for (var type : org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Type.values()) {
      var checkout = mirrors.checkout("service", "project", "shared.urn", type, "v1", 100);
      assertEquals(ManagedBehaviorMirrors.Synchronization.UPDATED,
          mirrors.synchronize("service", "project", "shared.urn", type, "v2", 200));
      var reopened = new ManagedBehaviorMirrors(temporaryDirectory);
      assertEquals(200, reopened.origin(checkout.file()).orElseThrow().synchronizedTimestamp());
      assertEquals(ManagedBehaviorMirrors.Synchronization.STALE,
          reopened.synchronize("service", "project", "shared.urn", type, "v1", 100));
      assertEquals("v2", Files.readString(checkout.file()));
      // Equal timestamps still need a source comparison (coarse service clocks).
      reopened.synchronize("service", "project", "shared.urn", type, "v3", 200);
      assertEquals("v3", Files.readString(checkout.file()));
      Files.writeString(checkout.file(), "local edits");
      assertEquals(ManagedBehaviorMirrors.Synchronization.DIRTY,
          reopened.synchronize("service", "project", "shared.urn", type, "v4", 300));
      assertEquals("local edits", Files.readString(checkout.file()));
      assertEquals(200, reopened.origin(checkout.file()).orElseThrow().synchronizedTimestamp());
    }
  }

  @Test void recreatingDeletedSourceUpdatesItsSynchronizationMetadata() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var type = org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Type.UNITTEST;
    var checkout = mirrors.checkout("service", "project", "test", type, "v1", 100);
    Files.delete(checkout.file());
    var recreated = mirrors.checkout("service", "project", "test", type, "v2", 200);
    assertEquals(200, recreated.origin().synchronizedTimestamp());
    assertEquals(ManagedBehaviorMirrors.LocalState.SYNCHRONIZED, mirrors.localState(recreated.file()));
    mirrors.synchronize("service", "project", "test", type, "v3", 300);
    assertEquals("v3", Files.readString(recreated.file()));
  }

  @Test void sameUrnApplicationAndBehaviorHaveIndependentMirrors() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var behavior = mirrors.checkout("service", "project", "staging.vxii.demoapp",
        org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Type.BEHAVIOR, "original behavior");
    var app = mirrors.checkout("service", "project", "staging.vxii.demoapp",
        org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Type.APP, "saved application");
    assertFalse(behavior.file().equals(app.file()));
    mirrors.synchronize("service", "project", "staging.vxii.demoapp",
        org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Type.APP, "new application");
    assertEquals("original behavior", Files.readString(behavior.file()));
    assertEquals("new application", Files.readString(app.file()));
  }

  @Test void cleanLegacyMirrorCanBeMigratedToTheSelectedApplication() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var legacy = mirrors.checkout("service", "project", "staging.vxii.demoapp", "original template");
    var app = mirrors.checkout("service", "project", "staging.vxii.demoapp",
        org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Type.APP, "saved web application");
    assertEquals(legacy.file(), app.file());
    assertEquals("saved web application", Files.readString(app.file()));
    assertEquals(org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Type.APP, app.origin().behaviorType());
  }

  @Test
  void repeatedCheckoutReconcilesSuccessiveProjectSavesWithoutChangeNotifications() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var original = mirrors.checkout("service", "project", "application.one", "original template");

    for (String saved : java.util.List.of("saved project v2", "saved project v3")) {
      var reopened = mirrors.checkout("service", "project", "application.one", saved);
      assertFalse(reopened.created());
      assertEquals(original.file(), reopened.file());
      assertEquals(saved, Files.readString(reopened.file()));
      assertEquals(ManagedBehaviorMirrors.LocalState.SYNCHRONIZED, mirrors.localState(reopened.file()));
      assertEquals(reopened.origin(), mirrors.origin(reopened.file()).orElseThrow());
    }
  }

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

  @Test
  void localStateTracksUnsubmittedChangesAndSuccessfulSynchronization() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var checkout = mirrors.checkout("service", "project", "behavior.one", "remote v1");

    assertEquals(
        ManagedBehaviorMirrors.LocalState.SYNCHRONIZED,
        mirrors.localState(checkout.file()));

    Files.writeString(checkout.file(), "local work");
    assertEquals(
        ManagedBehaviorMirrors.LocalState.MODIFIED, mirrors.localState(checkout.file()));

    mirrors.markSynchronized(checkout.file(), "local work");
    assertEquals(
        ManagedBehaviorMirrors.LocalState.SYNCHRONIZED,
        mirrors.localState(checkout.file()));
  }
}

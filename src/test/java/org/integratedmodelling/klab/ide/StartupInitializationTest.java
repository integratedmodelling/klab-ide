package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.junit.jupiter.api.Test;

class StartupInitializationTest {

  @Test
  void blockingInitializationDoesNotBlockItsCaller() throws Exception {
    var caller = Thread.currentThread();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var worker = new AtomicReference<Thread>();

    var future =
        StartupInitialization.run(
            () -> {
              worker.set(Thread.currentThread());
              started.countDown();
              try {
                release.await();
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
              }
              return null;
            });

    try {
      assertTrue(started.await(2, TimeUnit.SECONDS));
      assertFalse(future.isDone());
      assertNotEquals(caller, worker.get());
      assertTrue(worker.get().isVirtual());
    } finally {
      release.countDown();
    }
    future.orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS).join();
  }

  @Test
  void startupSynchronizationRequiresAnInstalledNetworkDistribution() {
    var version = Version.create("1.0.0");
    var installed = Stack.Tag.of(version, "develop", "202608250900", true, false);

    assertTrue(StartupInitialization.isNetworkSynchronizationCandidate(true, installed));
    assertFalse(StartupInitialization.isNetworkSynchronizationCandidate(false, installed));
    assertFalse(
        StartupInitialization.isNetworkSynchronizationCandidate(
            true, Stack.Tag.of(Version.HEAD, "develop", "HEAD", true, false)));
    assertFalse(
        StartupInitialization.isNetworkSynchronizationCandidate(
            true, Stack.Tag.of(version, "develop", "202608250900", false, false)));
    assertFalse(
        StartupInitialization.isNetworkSynchronizationCandidate(
            true, Stack.Tag.of(version, "develop", "202608250900", true, true)));
    assertFalse(StartupInitialization.isNetworkSynchronizationCandidate(true, null));
  }

  @Test
  void startupSynchronizationRequiresFilesFromTheRemoteUpdate() {
    var file = new Distribution.FileData("hash", "engine.jar", 0);
    var target = new Distribution.FileTarget(null, null);
    var update = new Stack.Status(0, 0, Map.of(file, target), Map.of(file, target));

    assertTrue(StartupInitialization.hasAvailableUpdate(update));
    assertFalse(StartupInitialization.hasAvailableUpdate(Stack.Status.ABSENT));
    assertFalse(StartupInitialization.hasAvailableUpdate(null));
  }
}

package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
}

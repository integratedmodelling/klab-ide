package org.integratedmodelling.klab.ide.utils;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AsyncLoadTest {
  @Test void blockedRemoteRequestDoesNotBlockCallerAndPublishesOnPresentationExecutor() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var ready = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
    var values = new ArrayList<String>();
    var load = new AsyncLoad<String>(task -> Thread.startVirtualThread(task), ready::add);
    try {
      load.load(() -> {
        started.countDown();
        try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
        return "persisted graph";
      }, values::add, error -> fail(error));
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertTrue(values.isEmpty());
      release.countDown();
      var publication = ready.poll(5, TimeUnit.SECONDS);
      assertNotNull(publication);
      assertTrue(values.isEmpty());
      publication.run();
      assertEquals(java.util.List.of("persisted graph"), values);
    } finally { release.countDown(); }
  }

  @Test void lateCompletionCannotOverwriteNewerSelection() {
    var worker = new ArrayDeque<Runnable>();
    var ui = new ArrayDeque<Runnable>();
    var values = new ArrayList<String>();
    var load = new AsyncLoad<String>(worker::add, ui::add);
    load.load(() -> "old twin", values::add, error -> fail(error));
    load.load(() -> "new twin", values::add, error -> fail(error));
    worker.removeLast().run();
    worker.removeFirst().run();
    while (!ui.isEmpty()) ui.removeFirst().run();
    assertEquals(java.util.List.of("new twin"), values);
  }

  @Test void closingDropsQueuedResultsAndErrorsButCurrentFailuresAreReported() {
    var ui = new ArrayDeque<Runnable>();
    var errors = new ArrayList<Throwable>();
    var load = new AsyncLoad<String>(Runnable::run, ui::add);
    load.load(() -> { throw new IllegalStateException("offline"); }, value -> fail(), errors::add);
    load.invalidate();
    ui.removeFirst().run();
    assertTrue(errors.isEmpty());
    load.load(() -> { throw new IllegalStateException("offline"); }, value -> fail(), errors::add);
    ui.removeFirst().run();
    assertEquals("offline", errors.getFirst().getMessage());
  }
}

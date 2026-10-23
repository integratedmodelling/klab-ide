package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ApplicationUiSessionTest {
  static class Api implements ApplicationUiSession.Api {
    final CountDownLatch launching = new CountDownLatch(1), release = new CountDownLatch(1), stopped = new CountDownLatch(1);
    final AtomicInteger launches = new AtomicInteger(), stops = new AtomicInteger();
    final List<String> keys = new CopyOnWriteArrayList<>();
    boolean failLaunch, failStop;
    public JsonObject launch(String urn, String key) {
      launches.incrementAndGet(); keys.add(key); launching.countDown();
      try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
      catch (InterruptedException e) { throw new RuntimeException(e); }
      if (failLaunch) { failLaunch = false; throw new IllegalStateException("Temporary launch failure"); }
      var result = new JsonObject(); result.addProperty("id", "instance-1"); return result;
    }
    public String ticket(String id) { throw new IllegalStateException("Ticket unavailable"); }
    public boolean stop(String id) {
      assertEquals("instance-1", id); stops.incrementAndGet();
      if (failStop) { failStop = false; return false; }
      stopped.countDown(); return true;
    }
  }

  @Test void closesAnInstanceReturnedAfterTheEditorHasClosed() throws Exception {
    var api = new Api();
    var session = new ApplicationUiSession(api, URI.create("http://localhost:8283/runtime"), f -> {}, s -> {});
    session.start("project.app");
    assertTrue(api.launching.await(5, TimeUnit.SECONDS));
    session.close(); session.close(); api.release.countDown();
    assertTrue(api.stopped.await(5, TimeUnit.SECONDS));
    assertEquals(1, api.launches.get()); assertEquals(1, api.stops.get());
  }

  @Test void preservesLaunchKeyAndInstanceAcrossTransportAndStopFailures() throws Exception {
    var api = new Api(); api.failLaunch = true; api.failStop = true; api.release.countDown();
    var messages = new LinkedBlockingQueue<String>();
    try (var session = new ApplicationUiSession(api, URI.create("http://localhost:8283/"), f -> {}, messages::add)) {
      session.start("project.app"); await(messages, "Application error: Temporary launch failure");
      session.start("project.app"); await(messages, "Application error: Ticket unavailable");
      assertEquals(api.keys.get(0), api.keys.get(1));
      session.start("project.app"); await(messages, "Application already running; reconnect to refresh the view");
      assertEquals(2, api.launches.get());
      session.stop(); await(messages, "Application error: Could not stop application; retry Stop");
      session.stop(); assertTrue(api.stopped.await(5, TimeUnit.SECONDS));
    }
  }

  @Test void preservesRuntimeContextPathInTheWebSocketEndpoint() {
    assertEquals(URI.create("wss://localhost:8283/runtime/ui-bus"),
        ApplicationUiSession.busUri(URI.create("https://localhost:8283/runtime/")));
  }

  private static void await(BlockingQueue<String> messages, String expected) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (expected.equals(messages.poll(100, TimeUnit.MILLISECONDS))) return;
    }
    fail("Missing session status: " + expected);
  }
}

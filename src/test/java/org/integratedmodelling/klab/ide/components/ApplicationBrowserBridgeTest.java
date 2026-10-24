package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

class ApplicationBrowserBridgeTest {
  private static final String HTML = "<meta content=\"connect-src 'none'\"><body>renderer</body>";

  @Test void protectsTransportAndKeepsCredentialsOutOfThePage() throws Exception {
    try (var bridge = new ApplicationBrowserBridge(HTML, input -> {});
         var http = HttpClient.newHttpClient()) {
      URI base = bridge.url().resolve("/");
      var page = http.send(HttpRequest.newBuilder(base).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, page.statusCode());
      assertTrue(page.body().contains("connect-src 'self'"));
      assertFalse(page.body().contains(bridge.url().getFragment()));
      assertEquals("no-store", page.headers().firstValue("Cache-Control").orElseThrow());
      var denied = http.send(HttpRequest.newBuilder(base.resolve("/frames")).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(403, denied.statusCode());
    }
  }

  @Test void replaysCurrentSnapshotAndAcknowledgedSequenceOnBrowserReload() throws Exception {
    try (var bridge = new ApplicationBrowserBridge(HTML, input -> {});
         var http = HttpClient.newHttpClient()) {
      bridge.accept(frame("instance", "{\"id\":\"same-runtime-instance\"}"));
      bridge.accept(frame("snapshot", "{\"revision\":7}"));
      bridge.accept(frame("ready", "{\"lastAcceptedSequence\":2}"));
      bridge.accept(frame("ack", "3"));
      var response = http.send(request(bridge, "/frames?after=0").GET().build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      var replay = JsonParser.parseString(response.body()).getAsJsonObject();
      var frames = replay.getAsJsonArray("frames");
      assertEquals(3, frames.size());
      assertEquals("same-runtime-instance", frames.get(0).getAsJsonObject().getAsJsonObject("value").get("id").getAsString());
      assertEquals(7, frames.get(1).getAsJsonObject().getAsJsonObject("value").get("revision").getAsInt());
      assertEquals(3, frames.get(2).getAsJsonObject().getAsJsonObject("value").get("lastAcceptedSequence").getAsInt());
      bridge.accept(frame("disconnected", "{}"));
      var delta = http.send(request(bridge, "/frames?after=" + replay.get("cursor").getAsLong()).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertTrue(delta.body().contains("disconnected"));
    }
  }

  @Test void forwardsOnlyAuthenticatedSameOriginWidgetInput() throws Exception {
    var inputs = new LinkedBlockingQueue<String>();
    try (var bridge = new ApplicationBrowserBridge(HTML, inputs::add);
         var http = HttpClient.newHttpClient()) {
      String event = "{\"type\":\"input\",\"value\":{\"nodeId\":\"go\",\"sequence\":1}}";
      var denied = http.send(request(bridge, "/input").header("Origin", "https://example.invalid")
          .POST(HttpRequest.BodyPublishers.ofString(event)).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(403, denied.statusCode()); assertTrue(inputs.isEmpty());
      String origin = bridge.url().getScheme() + "://" + bridge.url().getRawAuthority();
      var accepted = http.send(request(bridge, "/input").header("Origin", origin)
          .POST(HttpRequest.BodyPublishers.ofString(event)).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(204, accepted.statusCode()); assertEquals(event, inputs.remove());
      var invalid = http.send(request(bridge, "/input").header("Origin", origin)
          .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"launch\"}")).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(400, invalid.statusCode()); assertTrue(inputs.isEmpty());
    }
  }

  @Test void fileTransfersRemainAuthenticatedSameOriginAndBounded() throws Exception {
    var requests = new LinkedBlockingQueue<String>();
    try (var bridge = new ApplicationBrowserBridge(HTML, input -> {}, json -> {
      requests.add(json); return java.util.concurrent.CompletableFuture.completedFuture(JsonParser.parseString("{\"id\":\"opaque-file\"}").getAsJsonObject());
    }); var http = HttpClient.newHttpClient()) {
      String body = "{\"operation\":\"download\",\"handle\":\"opaque-file\"}";
      String origin = bridge.url().getScheme() + "://" + bridge.url().getRawAuthority();
      assertEquals(403, http.send(request(bridge, "/file").header("Origin", "https://attacker.invalid")
          .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
      assertTrue(requests.isEmpty());
      var accepted = http.send(request(bridge, "/file").header("Origin", origin)
          .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, accepted.statusCode()); assertEquals(body, requests.remove()); assertTrue(accepted.body().contains("opaque-file"));
      assertEquals(413, http.send(request(bridge, "/file").header("Origin", origin)
          .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[5600001])).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
      assertTrue(requests.isEmpty());
    }
  }

  private static HttpRequest.Builder request(ApplicationBrowserBridge bridge, String route) {
    return HttpRequest.newBuilder(bridge.url().resolve(route))
        .header("Authorization", "Bearer " + bridge.url().getFragment());
  }
  private static JsonObject frame(String type, String value) {
    var frame = new JsonObject(); frame.addProperty("type", type);
    frame.add("value", JsonParser.parseString(value)); return frame;
  }
}

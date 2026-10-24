package org.integratedmodelling.klab.ide.components;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Per-run loopback transport for the same portable renderer and runtime instance as the WebView.
 */
final class ApplicationBrowserBridge implements AutoCloseable {
  private final HttpServer server;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Consumer<String> input;
  private final java.util.function.Function<String, CompletionStage<JsonObject>> files;
  private final String token;
  private final String origin;
  private final byte[] html;
  private final Deque<Event> events = new ArrayDeque<>();
  private long cursor;
  private JsonObject instance, snapshot, ready;
  private boolean connected, closed;

  private record Event(long cursor, JsonObject frame) {}

  ApplicationBrowserBridge(String renderer, Consumer<String> input) throws IOException {
    this(renderer, input, request -> CompletableFuture.failedFuture(new IllegalStateException("Application file transfers are unavailable")));
  }
  ApplicationBrowserBridge(String renderer, Consumer<String> input,
      java.util.function.Function<String, CompletionStage<JsonObject>> files) throws IOException {
    this.input = input; this.files = files;
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
    origin = "http://127.0.0.1:" + server.getAddress().getPort();
    html =
        renderer
            .replace("connect-src 'none'", "connect-src 'self'")
            .replace("</body>", BOOTSTRAP + "</body>")
            .getBytes(StandardCharsets.UTF_8);
    server.createContext("/", this::handle);
    server.setExecutor(executor);
    server.start();
  }

  URI url() {
    return URI.create(origin + "/#" + token);
  }

  synchronized void accept(JsonObject frame) {
    if (closed) return;
    frame = frame.deepCopy();
    switch (frame.get("type").getAsString()) {
      case "instance" -> {
        instance = frame;
        snapshot = null;
        ready = null;
        connected = false;
      }
      case "snapshot" -> snapshot = frame;
      case "ready" -> {
        ready = frame;
        connected = true;
      }
      case "ack" -> {
        if (ready != null)
          ready
              .getAsJsonObject("value")
              .addProperty("lastAcceptedSequence", frame.get("value").getAsLong());
      }
      case "resync" -> {
        var state = frame.getAsJsonObject("value");
        if (state.has("snapshot") && !state.get("snapshot").isJsonNull()) {
          snapshot = new JsonObject();
          snapshot.addProperty("type", "snapshot");
          snapshot.add("value", state.get("snapshot").deepCopy());
        }
        if (ready != null)
          ready
              .getAsJsonObject("value")
              .add("lastAcceptedSequence", state.get("lastAcceptedSequence").deepCopy());
      }
      case "disconnected", "stopped" -> connected = false;
      default -> {}
    }
    events.addLast(new Event(++cursor, frame));
    while (events.size() > 128) events.removeFirst();
    notifyAll();
  }

  private void handle(HttpExchange request) throws IOException {
    try (request) {
      var headers = request.getResponseHeaders();
      headers.set("Cache-Control", "no-store");
      headers.set("Referrer-Policy", "no-referrer");
      headers.set("X-Frame-Options", "DENY");
      headers.set("X-Content-Type-Options", "nosniff");
      if (!URI.create(origin)
          .getRawAuthority()
          .equals(request.getRequestHeaders().getFirst("Host"))) {
        reply(request, 403, "text/plain", new byte[0]);
        return;
      }
      String path = request.getRequestURI().getPath(), method = request.getRequestMethod();
      if (path.equals("/") && method.equals("GET")) {
        reply(request, 200, "text/html; charset=utf-8", html);
        return;
      }
      String authorization = request.getRequestHeaders().getFirst("Authorization");
      if (authorization == null
          || !MessageDigest.isEqual(
              ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
              authorization.getBytes(StandardCharsets.UTF_8))) {
        reply(request, 403, "text/plain", new byte[0]);
        return;
      }
      if (path.equals("/file") && method.equals("POST")) {
        if (!origin.equals(request.getRequestHeaders().getFirst("Origin"))) { reply(request, 403, "text/plain", new byte[0]); return; }
        byte[] body = request.getRequestBody().readNBytes(5600001);
        if (body.length > 5600000) { reply(request, 413, "text/plain", new byte[0]); return; }
        var response = new JsonObject();
        try { response.add("value", files.apply(new String(body, StandardCharsets.UTF_8)).toCompletableFuture().get(35, TimeUnit.SECONDS)); }
        catch (Exception failure) { response.addProperty("error", "Application file transfer failed"); }
        reply(request, 200, "application/json", response.toString().getBytes(StandardCharsets.UTF_8));
      } else if (path.equals("/input") && method.equals("POST")) {
        if (!origin.equals(request.getRequestHeaders().getFirst("Origin"))) {
          reply(request, 403, "text/plain", new byte[0]);
          return;
        }
        byte[] body = request.getRequestBody().readNBytes(65537);
        if (body.length > 65536) {
          reply(request, 413, "text/plain", new byte[0]);
          return;
        }
        try {
          var frame =
              JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
          if (!"input".equals(frame.get("type").getAsString()))
            throw new IllegalArgumentException();
          input.accept(frame.toString());
          reply(request, 204, "text/plain", new byte[0]);
        } catch (RuntimeException invalid) {
          reply(request, 400, "text/plain", new byte[0]);
        }
      } else if (path.equals("/frames") && method.equals("GET")) {
        try {
          String query = request.getRequestURI().getRawQuery();
          long after = query == null ? 0 : Long.parseLong(query.replaceFirst("^after=", ""));
          reply(
              request,
              200,
              "application/json",
              poll(after).toString().getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException invalid) {
          reply(request, 400, "text/plain", new byte[0]);
        }
      } else reply(request, 404, "text/plain", new byte[0]);
    }
  }

  private synchronized JsonObject poll(long after) {
    if (after == cursor && !closed) {
      try {
        wait(15000);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
    var result = new JsonObject();
    var frames = new JsonArray();
    if (after == 0 || !events.isEmpty() && after < events.getFirst().cursor() - 1) {
      if (instance != null) frames.add(instance.deepCopy());
      if (snapshot != null) frames.add(snapshot.deepCopy());
      if (connected && ready != null) frames.add(ready.deepCopy());
      else {
        var disconnected = new JsonObject();
        disconnected.addProperty("type", "disconnected");
        frames.add(disconnected);
      }
    } else
      for (var event : events) if (event.cursor() > after) frames.add(event.frame().deepCopy());
    result.addProperty("cursor", cursor);
    result.add("frames", frames);
    return result;
  }

  private static void reply(HttpExchange request, int status, String type, byte[] bytes)
      throws IOException {
    request.getResponseHeaders().set("Content-Type", type);
    request.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
    if (status != 204) request.getResponseBody().write(bytes);
  }

  @Override
  public void close() {
    synchronized (this) {
      closed = true;
      notifyAll();
    }
    server.stop(0);
    executor.shutdownNow();
  }

  private static final String BOOTSTRAP =
      """
      <script>
      (()=>{
        const token=location.hash.slice(1)||sessionStorage.getItem('ide-application');
        if(token)sessionStorage.setItem('ide-application',token);
        history.replaceState(null,'',location.pathname);
        const headers={Authorization:'Bearer '+token}; let cursor=0,stopped=false;
        window.ide={input:frame=>fetch('/input',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:frame})
          .then(r=>{if(!r.ok)throw Error('Application input failed');}).catch(fail),
          file:(id,request)=>fetch('/file',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:request})
            .then(async r=>{if(!r.ok)throw Error('Application file transfer failed');applicationUiFileReply(id,await r.text());})
            .catch(error=>applicationUiFileReply(id,JSON.stringify({error:error.message})))};
        function fail(){disconnected();document.getElementById('status').textContent='IDE connection unavailable. Return to the IDE to reconnect or start a new run.';}
        async function poll(){
          while(!stopped){
            try{
              const response=await fetch('/frames?after='+cursor,{headers,cache:'no-store'});
              if(!response.ok)throw Error('Application connection unavailable');
              const result=await response.json(); cursor=result.cursor;
              result.frames.forEach(frame=>receive(frame));
            }catch(error){fail();stopped=true;}
          }
        }
        poll(); window.addEventListener('beforeunload',()=>{stopped=true;});
      })();
      </script>
      """;
}

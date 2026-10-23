package org.integratedmodelling.klab.ide.components;

import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Serializes the lifetime of one runtime-owned web application, including close during launch. */
final class ApplicationUiSession implements AutoCloseable {
  interface Api {
    JsonObject launch(String urn, String key);

    String ticket(String id);

    boolean stop(String id);
  }

  private final Api api;
  private final URI bus;
  private final String origin;
  private final Consumer<JsonObject> frames;
  private final Consumer<String> status;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ScheduledExecutorService worker =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofVirtual().name("ide-application").factory());
  private String id;
  private WebSocket socket;
  private ScheduledFuture<?> heartbeat;
  private volatile boolean closed;
  private String creationKey = UUID.randomUUID().toString();

  ApplicationUiSession(Api api, URI runtime, Consumer<JsonObject> frames, Consumer<String> status) {
    this.api = api;
    this.bus = busUri(runtime);
    this.origin = runtime.getScheme() + "://" + runtime.getRawAuthority();
    this.frames = frames;
    this.status = status;
  }

  static URI busUri(URI runtime) {
    String base = runtime.toString().replaceAll("/+$", "");
    return URI.create(base.replaceFirst("^http", "ws") + "/ui-bus");
  }

  void start(String urn) {
    submit(
        () -> {
          if (id != null) {
            status.accept("Application already running; reconnect to refresh the view");
            return;
          }
          status.accept("Starting published application…");
          var result = api.launch(urn, creationKey);
          id = result.get("id").getAsString();
          frames.accept(envelope("instance", result));
          if (!closed) attach();
        });
  }

  void reconnect() {
    submit(
        () -> {
          if (id != null) attach();
        });
  }

  private void attach() {
    detach();
    status.accept("Connecting…");
    String ticket = api.ticket(id);
    var listener =
        new WebSocket.Listener() {
          private final StringBuilder text = new StringBuilder();

          public CompletionStage<?> onText(WebSocket ws, CharSequence part, boolean last) {
            text.append(part);
            if (text.length() > 1048576) {
              ws.abort();
              return null;
            }
            if (last) {
              String message = text.toString();
              text.setLength(0);
              submit(
                  () -> {
                    if (socket != ws) return;
                    var frame = JsonParser.parseString(message).getAsJsonObject();
                    if ("ready".equals(frame.get("type").getAsString())) status.accept("Running");
                    frames.accept(frame);
                  });
            }
            ws.request(1);
            return null;
          }

          public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            submit(
                () -> {
                  if (socket == ws) {
                    detach();
                    status.accept("Disconnected — use Reconnect to resume");
                  }
                });
            return null;
          }

          public void onError(WebSocket ws, Throwable failure) {
            submit(
                () -> {
                  if (socket == ws) {
                    detach();
                    status.accept("Connection failed — use Reconnect to resume");
                  }
                });
          }
        };
    socket =
        http.newWebSocketBuilder()
            .header("Origin", origin)
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .buildAsync(bus, listener)
            .join();
    socket.sendText(new Gson().toJson(Map.of("ticket", ticket)), true).join();
    heartbeat =
        worker.scheduleAtFixedRate(
            () -> {
              if (socket != null) socket.sendText("{\"type\":\"ping\"}", true);
            },
            20,
            20,
            TimeUnit.SECONDS);
  }

  void input(String json) {
    submit(
        () -> {
          if (socket == null || json.length() > 65536) return;
          var frame = JsonParser.parseString(json).getAsJsonObject();
          if (!"input".equals(frame.get("type").getAsString())) return;
          socket.sendText(frame.toString(), true).join();
        });
  }

  void stop() {
    submit(this::terminate);
  }

  private void terminate() {
    detach();
    if (id != null) {
      if (!api.stop(id)) throw new IllegalStateException("Could not stop application; retry Stop");
      id = null;
      creationKey = UUID.randomUUID().toString();
    }
    frames.accept(envelope("stopped", new JsonObject()));
    status.accept("Stopped");
  }

  private void detach() {
    if (heartbeat != null) {
      heartbeat.cancel(false);
      heartbeat = null;
    }
    if (socket != null) {
      socket.abort();
      socket = null;
    }
    frames.accept(envelope("disconnected", new JsonObject()));
  }

  private void submit(Runnable operation) {
    if (closed) return;
    try {
      worker.execute(
          () -> {
            try {
              operation.run();
            } catch (Exception failure) {
              status.accept("Application error: " + failure.getMessage());
              if (id == null) frames.accept(envelope("idle", new JsonObject()));
            }
          });
    } catch (RejectedExecutionException ignored) {
      /* Closing. */
    }
  }

  private static JsonObject envelope(String type, JsonElement value) {
    var result = new JsonObject();
    result.addProperty("type", type);
    result.add("value", value);
    return result;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    worker.execute(
        () -> {
          try {
            terminate();
          } catch (Exception failure) {
            status.accept("Application cleanup failed: " + failure.getMessage());
          } finally {
            http.close();
            worker.shutdown();
          }
        });
  }
}

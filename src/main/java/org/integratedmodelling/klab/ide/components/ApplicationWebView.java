package org.integratedmodelling.klab.ide.components;

import com.google.gson.*;
import atlantafx.base.theme.Styles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/** Embedded portable UI renderer. Credentials and runtime transport stay outside the WebView. */
public final class ApplicationWebView extends BorderPane implements AutoCloseable {
  private final WebView web = new WebView();
  private final Label status = new Label("Stopped");
  private final TextArea console = new TextArea();
  private final ApplicationUiSession session;
  private final ChoiceBox<ApplicationBrowser> destination;
  private final ApplicationBrowserOptions browserOptions;
  private final javafx.beans.value.ChangeListener<ApplicationBrowser> destinationChanged;
  private ApplicationBrowser chosen;
  private ApplicationBrowserBridge browserBridge;
  private boolean runActive;
  private boolean browserOpened;
  private String renderer;
  private JsonObject instanceMetadata;
  // Strong reference required while WebKit owns the JavaScript wrapper.
  private final InputBridge bridge = new InputBridge();
  private boolean ready;
  private boolean pendingStart;
  private volatile boolean closed;
  private final String urn;

  public ApplicationWebView(
      RuntimeService runtime,
      UserScope user,
      String urn,
      java.util.function.Consumer<String> cleanupFailure) {
    this(runtime, user, urn, new ApplicationBrowserOptions(), cleanupFailure);
  }

  ApplicationWebView(RuntimeService runtime, UserScope user, String urn,
      ApplicationBrowserOptions browserOptions, java.util.function.Consumer<String> cleanupFailure) {
    requireLocalRuntime(runtime);
    this.urn = urn;
    this.browserOptions = browserOptions;
    destination = browserOptions.createChoice(() -> getScene() == null ? null : getScene().getWindow());
    session =
        new ApplicationUiSession(
            new RuntimeApi(runtime, user),
            java.net.URI.create(runtime.getUrl().toString()),
            frame ->
                Platform.runLater(
                    () -> {
                      if (closed) return;
                      if ("instance".equals(frame.get("type").getAsString())) instanceMetadata = frame.deepCopy();
                      if (browserBridge != null) browserBridge.accept(frame);
                      else if (ready)
                        web.getEngine()
                            .executeScript(
                                "receive(JSON.parse(" + new Gson().toJson(frame.toString()) + "))");
                      String type = frame.get("type").getAsString();
                      if (type.equals("instance") && browserBridge != null && !browserOpened)
                        openBrowser();
                      if (type.equals("stopped") || type.equals("idle")) finishRun();
                    }),
            message ->
                Platform.runLater(
                    () -> {
                      if (closed) {
                        if (message.startsWith("Application cleanup failed"))
                          cleanupFailure.accept(message);
                        return;
                      }
                      status.setText(message);
                      console.appendText(message + System.lineSeparator());
                    }));
    var run = toolbarButton(Material2MZ.PLAY_ARROW, "Run saved application", this::start);
    var stop = toolbarButton(Material2MZ.STOP, "Stop application", this::stop);
    var reconnect =
        toolbarButton(
            Material2AL.AUTORENEW,
            "Reconnect application",
            () -> {
              session.reconnect();
              if (browserBridge != null) openBrowser();
            });
    destination.setPrefWidth(170);
    destinationChanged = (obs, previous, selected) -> {
      if (runActive && !closed && !selected.equals(chosen)) switchDestination(selected);
    };
    browserOptions.selectedProperty().addListener(destinationChanged);
    var statusSpacer = new Region();
    HBox.setHgrow(statusSpacer, Priority.ALWAYS);
    var toolbar = new HBox(4, run, stop, reconnect, new Separator(), new Label("Open in:"), destination,
        statusSpacer, status);
    toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    toolbar.setPadding(new javafx.geometry.Insets(4, 8, 4, 8));
    toolbar.getStyleClass().add(Styles.DENSE);
    var notice =
        new Label("Project application: " + urn + " — save changes, then Stop and Run to load them.");
    notice.setWrapText(true);
    notice.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
    var noticePane = new VBox(notice);
    noticePane.setPadding(new javafx.geometry.Insets(4, 8, 4, 8));
    noticePane.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-muted; -fx-border-width: 1 0 1 0;");
    setTop(new VBox(toolbar, noticePane));
    console.setEditable(false);
    console.setPrefRowCount(4);
    var transcript = new TitledPane("Application connection console", console);
    transcript.setExpanded(false);
    setBottom(transcript);
    setCenter(web);
    web.setContextMenuEnabled(false);
    web.getEngine().setCreatePopupHandler(features -> null);
    web.getEngine()
        .getLoadWorker()
        .stateProperty()
        .addListener(
            (obs, old, state) -> {
              if (state == Worker.State.SUCCEEDED && !closed) {
                ((JSObject) web.getEngine().executeScript("window")).setMember("ide", bridge);
                ready = true;
                if (pendingStart) {
                  pendingStart = false;
                  session.start(urn);
                }
              } else if (state == Worker.State.FAILED) {
                status.setText("Could not load the embedded application renderer");
              }
            });
    try {
      renderer = ApplicationRenderer.load();
      web.getEngine().loadContent(renderer);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static IconButton toolbarButton(
      org.kordamp.ikonli.Ikon icon, String description, Runnable action) {
    var button =
        new IconButton(icon, 16, "-color-fg-default", "-color-fg-muted", false) {
          @Override
          protected void action() {
            action.run();
          }
        };
    button.tooltip(description);
    button.setAccessibleText(description);
    button.getStyleClass().add(Styles.FLAT);
    button.setMinSize(28, 28);
    button.setPrefSize(28, 28);
    button.setMaxSize(28, 28);
    return button;
  }

  public void start() {
    if (closed) return;
    if (runActive) {
      status.setText("Already running — Stop and Run to load the latest saved project source");
      return;
    }
    chosen = destination.getValue();
    if (chosen == null) return;
    if (!chosen.equals(ApplicationBrowserOptions.INTERNAL)) {
      try {
        browserBridge = new ApplicationBrowserBridge(renderer, session::input, session::file);
      } catch (IOException failure) {
        status.setText("Could not open browser connection: " + failure.getMessage());
        return;
      }
      setCenter(
          new Label(
              "Application destination: "
                  + chosen.name()
                  + ". Change browser to reconnect to the same running instance."));
    } else setCenter(web);
    runActive = true;
    if (ready) session.start(urn);
    else pendingStart = true;
  }

  public void stop() {
    pendingStart = false;
    session.stop();
  }

  private void switchDestination(ApplicationBrowser selected) {
    ApplicationBrowserBridge replacement = null;
    if (!selected.equals(ApplicationBrowserOptions.INTERNAL)) {
      try { replacement = new ApplicationBrowserBridge(renderer, session::input, session::file); }
      catch (IOException failure) {
        status.setText("Could not open browser connection: " + failure.getMessage());
        browserOptions.selectedProperty().set(chosen); return;
      }
    }
    if (browserBridge != null) browserBridge.close();
    browserBridge = replacement; browserOpened = false; chosen = selected;
    setCenter(replacement == null ? web : new Label("Application destination: " + selected.name()));
    if (instanceMetadata != null) {
      var metadata = instanceMetadata.deepCopy();
      metadata.getAsJsonObject("value").getAsJsonObject("state").add("snapshot", JsonNull.INSTANCE);
      if (replacement != null) { replacement.accept(metadata); openBrowser(); }
      else if (ready) web.getEngine().executeScript("receive(JSON.parse(" + new Gson().toJson(metadata.toString()) + "))");
      session.reconnect();
    }
  }

  void projectSourceChanged() {
    String message = runActive
        ? "Project source updated — Stop and Run to load the saved changes"
        : "Project source updated — the next run uses the saved project source";
    status.setText(message);
    console.appendText(message + System.lineSeparator());
  }

  private void openBrowser() {
    var bridge = browserBridge;
    var browser = chosen;
    if (bridge == null) return;
    browserOpened = true;
    Thread.ofVirtual()
        .name("ide-open-browser")
        .start(
            () -> {
              try {
                if (!closed) browser.open(bridge.url());
              } catch (Exception failure) {
                Platform.runLater(
                    () -> {
                      if (!closed && browserBridge == bridge) {
                        browserOpened = false;
                        status.setText(
                            "Could not open "
                                + browser.name()
                                + ". Use Reconnect to retry: "
                                + failure.getMessage());
                      }
                    });
              }
            });
  }

  private void finishRun() {
    if (browserBridge != null) {
      browserBridge.close();
      browserBridge = null;
      if (!closed) setCenter(new Label("Application stopped. Choose a destination and run again."));
    }
    browserOpened = false;
    instanceMetadata = null;
    runActive = false;
    destination.setDisable(false);
  }

  public final class InputBridge {
    public void input(String frame) {
      if (!closed) session.input(frame);
    }
    public void file(String requestId, String request) {
      if (closed) return;
      session.file(request).whenComplete((value, failure) -> {
        var response = new JsonObject();
        if (failure == null) response.add("value", value);
        else response.addProperty("error", "Application file transfer failed: " + failure.getMessage());
        Platform.runLater(() -> {
          if (!closed && ready) web.getEngine().executeScript("applicationUiFileReply("
              + new Gson().toJson(requestId) + "," + new Gson().toJson(response.toString()) + ")");
        });
      });
    }
    public void save(String name, String content) {
      if (closed || content.length() > 5592408) throw new IllegalStateException("Download unavailable");
      var chooser = new javafx.stage.FileChooser();
      chooser.setTitle("Save application download"); chooser.setInitialFileName(name);
      var target = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
      if (target != null) {
        byte[] bytes = java.util.Base64.getDecoder().decode(content);
        Thread.ofVirtual().name("ide-application-download").start(() -> {
          try { java.nio.file.Files.write(target.toPath(), bytes); }
          catch (IOException error) { Platform.runLater(() -> status.setText("Could not save application download: " + error.getMessage())); }
        });
      }
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    browserOptions.selectedProperty().removeListener(destinationChanged);
    ((Runnable) destination.getProperties().get("dispose-browser-options")).run();
    pendingStart = false;
    finishRun();
    session.close();
    web.getEngine().load(null);
  }

  private record RuntimeApi(RuntimeService runtime, UserScope user)
      implements ApplicationUiSession.Api {
    private Utils.Http.Client client() {
      requireLocalRuntime(runtime);
      return Utils.Http.getServiceClient(runtime.getUrl())
          .withScope(user)
          .withTimeout(30)
          .withHeader(
              ServicesAPI.SERVER_KEY_HEADER,
              Configuration.INSTANCE.getServiceSecret(
                  org.integratedmodelling.klab.api.services.KlabService.Type.RUNTIME));
    }

    private <T> T request(java.util.function.Function<Utils.Http.Client, T> operation) {
      try (var client = client()) {
        return operation.apply(client);
      } catch (RuntimeException failure) {
        throw failure;
      } catch (Exception failure) {
        throw new IllegalStateException("Runtime connection failed", failure);
      }
    }

    public JsonObject launch(String urn, String key) {
      return request(
          client -> {
            var result =
                client.postRequired(
                    "/api/v1/ui/instances",
                    Map.of(
                        "urn",
                        urn,
                        "locale",
                        java.util.Locale.getDefault().toLanguageTag(),
                        "idempotencyKey",
                        key),
                    Map.class);
            return new Gson().toJsonTree(result).getAsJsonObject();
          });
    }

    public String ticket(String id) {
      return request(
          client -> {
            var result =
                client.postRequired("/api/v1/ui/instances/" + id + "/ticket", Map.of(), Map.class);
            return result.get("ticket").toString();
          });
    }

    public boolean stop(String id) {
      return request(client -> client.delete("/api/v1/ui/instances/" + id));
    }
    public JsonObject file(String id, JsonObject request) {
      return ApplicationRuntimeFiles.request(runtime, user, id, request);
    }
  }

  static void requireLocalRuntime(RuntimeService runtime) {
    if (runtime == null || !runtime.isLocal() || runtime.getUrl() == null
        || !org.integratedmodelling.klab.api.utils.Utils.URLs.isLocalHost(runtime.getUrl()))
      throw new IllegalArgumentException("IDE applications require a locally running runtime service");
  }
}

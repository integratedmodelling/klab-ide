package org.integratedmodelling.klab.ide.components;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import netscape.javascript.JSObject;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;

/** Embedded portable UI renderer. Credentials and runtime transport stay outside the WebView. */
public final class ApplicationWebView extends BorderPane implements AutoCloseable {
  private final WebView web = new WebView();
  private final Label status = new Label("Stopped");
  private final TextArea console = new TextArea();
  private final ApplicationUiSession session;
  private final ChoiceBox<ApplicationBrowser> destination = new ChoiceBox<>();
  private final ApplicationBrowser internal =
      new ApplicationBrowser("Internal WebView", java.util.List.of());
  private final ApplicationBrowser custom =
      new ApplicationBrowser("Choose browser…", java.util.List.of());
  private ApplicationBrowser chosen;
  private ApplicationBrowserBridge browserBridge;
  private boolean runActive;
  private boolean browserOpened;
  private String renderer;
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
    this.urn = urn;
    session =
        new ApplicationUiSession(
            new RuntimeApi(runtime, user),
            java.net.URI.create(runtime.getUrl().toString()),
            frame ->
                Platform.runLater(
                    () -> {
                      if (closed) return;
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
    var run = new Button("Run published application");
    run.setOnAction(event -> start());
    var stop = new Button("Stop");
    stop.setOnAction(event -> stop());
    var reconnect = new Button("Reconnect");
    reconnect.setOnAction(
        event -> {
          session.reconnect();
          if (browserBridge != null) openBrowser();
        });
    destination.getItems().add(internal);
    destination.getItems().addAll(ApplicationBrowser.installed());
    destination.getItems().add(custom);
    destination.setValue(internal);
    destination.setPrefWidth(170);
    destination.setTooltip(
        new Tooltip("Choose the destination before running. Stop to change browsers."));
    destination
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, previous, selected) -> {
              if (selected != custom) return;
              var chooser = new FileChooser();
              chooser.setTitle("Choose browser executable");
              var executable =
                  chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
              if (executable == null) destination.setValue(previous == null ? internal : previous);
              else {
                var browser =
                    new ApplicationBrowser(
                        executable.getName(), java.util.List.of(executable.getAbsolutePath()));
                destination.getItems().add(destination.getItems().size() - 1, browser);
                destination.setValue(browser);
              }
            });
    var toolbar = new HBox(8, run, stop, reconnect, new Label("Open in:"), destination, status);
    toolbar.setStyle("-fx-padding: 8;");
    var notice =
        new Label("Published application: " + urn + " — publish source changes before running.");
    notice.setWrapText(true);
    notice.setStyle("-fx-padding: 4 8 4 8;");
    setTop(new VBox(toolbar, notice));
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
    try (var source = ApplicationWebView.class.getResourceAsStream("/application-ui.html")) {
      if (source == null) throw new IOException("Missing embedded application renderer");
      renderer = new String(source.readAllBytes(), StandardCharsets.UTF_8);
      web.getEngine().loadContent(renderer);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  public void start() {
    if (closed || runActive) return;
    chosen = destination.getValue();
    if (chosen == null || chosen == custom) return;
    if (chosen != internal) {
      try {
        browserBridge = new ApplicationBrowserBridge(renderer, session::input);
      } catch (IOException failure) {
        status.setText("Could not open browser connection: " + failure.getMessage());
        return;
      }
      setCenter(
          new Label(
              "Application destination: "
                  + chosen.name()
                  + ". Use Stop to select another browser."));
    } else setCenter(web);
    runActive = true;
    destination.setDisable(true);
    if (ready) session.start(urn);
    else pendingStart = true;
  }

  public void stop() {
    pendingStart = false;
    session.stop();
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
    runActive = false;
    destination.setDisable(false);
  }

  public final class InputBridge {
    public void input(String frame) {
      if (!closed) session.input(frame);
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    pendingStart = false;
    finishRun();
    session.close();
    web.getEngine().load(null);
  }

  private record RuntimeApi(RuntimeService runtime, UserScope user)
      implements ApplicationUiSession.Api {
    private Utils.Http.Client client() {
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
  }
}

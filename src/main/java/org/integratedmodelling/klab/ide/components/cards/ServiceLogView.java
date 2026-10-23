package org.integratedmodelling.klab.ide.components.cards;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.ide.components.generic.LogViewer;
import org.integratedmodelling.klab.ide.components.generic.RemoteLogSource;

/** Selected service's managed log. Capability probes and remote polling never run on FX. */
public final class ServiceLogView extends BorderPane implements AutoCloseable {
  private final KlabService service;
  private final Path localFile;
  private final boolean administrator;
  private final Label status = new Label();
  private final Button reload = new Button("Reload logs / reset gap");
  private LogViewer viewer;
  private CompletableFuture<?> probe;
  private long generation;
  private boolean active = true, closed;
  private javafx.stage.Window window;
  private final java.util.List<javafx.scene.Node> ancestors = new java.util.ArrayList<>();
  private final javafx.beans.value.ChangeListener<Boolean> visibilityChanged =
      (property, before, value) -> { if (treeVisible()) start(); else stop(); };
  private final javafx.beans.value.ChangeListener<javafx.scene.Parent> ancestryChanged =
      (property, before, value) -> watchAncestors();

  private boolean treeVisible() {
    for (var node : ancestors) if (!node.isVisible()) return false;
    return true;
  }

  private void unwatchAncestors() {
    for (var node : ancestors) {
      node.visibleProperty().removeListener(visibilityChanged);
      node.parentProperty().removeListener(ancestryChanged);
    }
    ancestors.clear();
  }

  private void watchAncestors() {
    if (closed) return;
    unwatchAncestors();
    for (javafx.scene.Node node = this; node != null; node = node.getParent()) {
      ancestors.add(node);
      node.visibleProperty().addListener(visibilityChanged);
      node.parentProperty().addListener(ancestryChanged);
    }
    stop(); start();
  }
  private final javafx.beans.value.ChangeListener<Boolean> showing =
      (property, before, visible) -> { if (visible) start(); else stop(); };
  private final javafx.beans.value.ChangeListener<javafx.stage.Window> windowChanged =
      (property, before, value) -> watchWindow(value);

  private void watchWindow(javafx.stage.Window value) {
    if (closed) return;
    if (window != null) window.showingProperty().removeListener(showing);
    window = value;
    if (window != null) window.showingProperty().addListener(showing);
    stop();
    start();
  }

  ServiceLogView(KlabService service, Path localFile, boolean administrator) {
    setId("service-log-view");
    this.service = service;
    this.localFile = localFile;
    this.administrator = administrator;
    setTop(new HBox(10, status, reload));
    reload.setOnAction(event -> { stop(); start(); });
    sceneProperty().addListener((property, oldScene, scene) -> {
      if (closed) return;
      if (oldScene != null) oldScene.windowProperty().removeListener(windowChanged);
      if (scene != null) scene.windowProperty().addListener(windowChanged);
      watchWindow(scene == null ? null : scene.getWindow());
    });
    status.setText(service.isLocal() ? "Local service log" : "Remote diagnostics; active file only");
    watchAncestors();
  }

  /** Tabs must call this as selection changes: an unselected tab may retain its Scene. */
  public void setActive(boolean value) {
    active = value;
    if (value) start(); else stop();
  }

  private void start() {
    if (closed || !active || !treeVisible() || getScene() == null || (window != null && !window.isShowing())
        || viewer != null || probe != null) return;
    if (service.isLocal()) {
      if (localFile == null) { status.setText("Local service log is unavailable"); return; }
      viewer = new LogViewer(localFile);
      setCenter(viewer);
      status.setText("Local service log");
      return;
    }
    if (!administrator) {
      status.setText("Remote logs require service administration permission");
      reload.setDisable(true);
      return;
    }
    if (!(service instanceof BaseServiceClient client)) {
      status.setText("This service client does not support remote logs");
      return;
    }
    long selected = ++generation;
    status.setText("Checking remote log capability...");
    reload.setDisable(true);
    probe = CompletableFuture.supplyAsync(client::logCapabilities).whenComplete((capabilities, error) ->
        Platform.runLater(() -> {
          if (closed || !active || selected != generation || getScene() == null) return;
          probe = null;
          reload.setDisable(false);
          if (error != null || capabilities == null || capabilities.streamIds() == null
              || !capabilities.streamIds().contains("service")
              || !"vetted-diagnostics-v1".equals(capabilities.redactionPolicy())) {
            status.setText("Remote log capability unavailable or access denied; reload to retry");
            return;
          }
          viewer = new LogViewer(new RemoteLogSource(client));
          setCenter(viewer);
          status.setText("Vetted remote diagnostics; active file only. Gaps require explicit reload.");
        }));
  }

  private void stop() {
    generation++;
    if (probe != null) { probe.cancel(true); probe = null; }
    if (viewer != null) { viewer.shutdown(); viewer = null; setCenter(null); }
    reload.setDisable(false);
  }

  @Override public void close() {
    closed = true;
    stop();
    unwatchAncestors();
    if (getScene() != null) getScene().windowProperty().removeListener(windowChanged);
    if (window != null) window.showingProperty().removeListener(showing);
  }
}

package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DigitalTwinControlPanelTest {

  @BeforeAll
  static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try {
      Platform.startup(
          () -> {
            Platform.setImplicitExit(false);
            ready.complete(null);
          });
    } catch (IllegalStateException started) {
      Platform.runLater(() -> ready.complete(null));
    }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test
  void showDropProgressDisplaysSpinnerInDropZone() throws Exception {
    var future = new CompletableFuture<Void>();
    Platform.runLater(
        () -> {
          try {
            var panel = new DigitalTwinControlPanel(220, null);
            panel.showDropProgress();
            assertEquals(DigitalTwinControlPanel.Status.RECEIVING, panel.getStatus());
            var center = panel.getCenter();
            assertInstanceOf(StackPane.class, center);
            var dropZone = (StackPane) center;
            assertEquals(1, dropZone.getChildren().size());
            assertInstanceOf(ProgressIndicator.class, dropZone.getChildren().getFirst());
            panel.endReceiving();
            assertEquals(DigitalTwinControlPanel.Status.IDLE, panel.getStatus());
            assertTrue(dropZone.getChildren().isEmpty());
            future.complete(null);
          } catch (Throwable t) {
            future.completeExceptionally(t);
          }
        });
    future.get(10, TimeUnit.SECONDS);
  }
}

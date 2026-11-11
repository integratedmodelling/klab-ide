package org.integratedmodelling.klab.ide.components.generic;

import java.util.function.Supplier;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.util.Duration;

/** Hover documentation that remains interactive while the pointer is over its content. */
public final class HoverCard extends Popup {
  private final PauseTransition opening;
  private final PauseTransition closing;
  private final StackPane content = new StackPane();
  private Node source;

  public HoverCard() {
    this(Duration.seconds(1), Duration.millis(400));
  }

  HoverCard(Duration showDelay, Duration hideDelay) {
    opening = new PauseTransition(showDelay);
    closing = new PauseTransition(hideDelay);
    closing.setOnFinished(event -> hide());
    setAutoFix(true);
    setAutoHide(true);
    setHideOnEscape(true);
    setConsumeAutoHidingEvents(false);
    content.setStyle("-fx-background-color: -color-bg-default; -fx-padding: 4; "
        + "-fx-border-color: -color-border-default; -fx-background-radius: 4; -fx-border-radius: 4;");
    content.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> closing.stop());
    content.addEventHandler(MouseEvent.MOUSE_EXITED, event -> closing.playFromStart());
    getContent().add(content);
    setOnHidden(event -> reset());
  }

  /** Content is created lazily on the FX thread, using the latest available documentation. */
  public void install(Node node, Supplier<? extends Node> documentation) {
    node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
      if (source == node && isShowing()) {
        closing.stop();
        return;
      }
      hide();
      source = node;
      opening.setOnFinished(ready -> {
        var bounds = node.localToScreen(node.getBoundsInLocal());
        if (source != node || bounds == null || node.getScene() == null
            || !node.getScene().getWindow().isShowing()) return;
        content.getChildren().setAll(documentation.get());
        show(node, bounds.getMaxX() + 6, bounds.getMinY());
      });
      opening.playFromStart();
    });
    node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
      if (source != node) return;
      opening.stop();
      if (isShowing()) closing.playFromStart();
      else source = null;
    });
    node.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> hide());
    node.sceneProperty().addListener((property, previous, scene) -> {
      if (scene == null && source == node) hide();
    });
  }

  @Override
  public void hide() {
    super.hide();
    reset();
  }

  private void reset() {
    opening.stop();
    closing.stop();
    opening.setOnFinished(null);
    source = null;
    content.getChildren().clear();
  }
}

package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Message;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.material2.Material2AL;

public class TextResult extends VBox {

  private final Message message;

  public TextResult(String text) {
    this(text, null);
  }

  public void setMessageStyle(String... styles) {
    message.getStyleClass().addAll(styles);
  }

  public TextResult(String text, String title) {

    this.message = new Message(title, text);
    this.message.setId("message");

    var copy = new IconLabel(Material2AL.CONTENT_COPY, 12, Color.DARKGRAY);
    copy.setId("copy");
    var tooltip = new Tooltip("Copy to clipboard");
    tooltip.setShowDelay(javafx.util.Duration.millis(200));
    copy.setTooltip(tooltip);
    copy.setOnMouseClicked(
        event -> {
          final var clipboard = Clipboard.getSystemClipboard();
          final var ct = new ClipboardContent();
          ct.putString(text);
          clipboard.setContent(ct);
        });

    var content = new StackPane(this.message, copy);
    StackPane.setAlignment(copy, Pos.TOP_RIGHT);
    StackPane.setMargin(copy, new Insets(6, 8, 0, 0));
    getChildren().add(content);
    VBox.setVgrow(content, Priority.ALWAYS);
  }
}

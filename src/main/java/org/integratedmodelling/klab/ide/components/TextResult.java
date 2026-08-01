package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Message;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    this.message.setMaxWidth(Double.MAX_VALUE);
    // Preserve the Message's computed height so it cannot be laid out at zero height.
    this.message.setMinHeight(Region.USE_PREF_SIZE);

    var scroller = new ScrollPane(this.message);
    scroller.setFitToWidth(true);
    scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroller.setMinWidth(0);
    scroller.setMinHeight(Region.USE_PREF_SIZE);
    scroller.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    scroller.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

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

    var content = new StackPane(scroller, copy);
    content.setMinWidth(0);
    content.setMinHeight(Region.USE_PREF_SIZE);
    content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    StackPane.setAlignment(copy, Pos.TOP_RIGHT);
    StackPane.setMargin(copy, new Insets(6, 20, 0, 0));
    getChildren().add(content);
    setMinSize(0, 0);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    HBox.setHgrow(this, Priority.ALWAYS);
    VBox.setVgrow(this, Priority.ALWAYS);
    HBox.setHgrow(content, Priority.ALWAYS);
    VBox.setVgrow(content, Priority.ALWAYS);
  }
}

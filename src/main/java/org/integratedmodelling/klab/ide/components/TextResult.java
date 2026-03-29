package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Message;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.material2.Material2AL;

public class TextResult extends VBox {

  public TextResult(String text) {
    var bottom = new HBox(0);
    HBox.setHgrow(bottom, Priority.ALWAYS);
    bottom.setAlignment(Pos.CENTER_RIGHT);
    bottom.setPadding(new Insets(2, 0, 0, 0));
    var copy = new IconLabel(Material2AL.CONTENT_COPY, 12, Color.DARKGRAY);
    copy.setOnMouseClicked(
        event -> {
          final var clipboard = Clipboard.getSystemClipboard();
          final var ct = new ClipboardContent();
          ct.putString(text);
          clipboard.setContent(ct);
        });
    bottom.getChildren().add(copy);
    getChildren().addAll(bottom, new Message(null, text));
  }
}

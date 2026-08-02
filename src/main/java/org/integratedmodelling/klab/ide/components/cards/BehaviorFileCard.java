package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2MZ;
import org.kordamp.ikonli.material2.Material2AL;

/** Compact recent-file card used by the Session view browser. */
public class BehaviorFileCard extends VBox {

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  public BehaviorFileCard(Path file, Consumer<Path> openHandler, Consumer<Path> forgetHandler) {
    this(file, Theme.BEHAVIOR_ICON, openHandler, forgetHandler);
  }

  public BehaviorFileCard(
      Path file, Ikon behaviorIcon, Consumer<Path> openHandler, Consumer<Path> forgetHandler) {
    Path normalized = file.toAbsolutePath().normalize();
    var card = new Card();
    var title = new Label(normalized.getFileName().toString());
    title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
    title.setMaxWidth(175);
    title.setTooltip(new Tooltip(normalized.toString()));

    var spacer = new HBox();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var open = new Label(null, new IconLabel(Material2MZ.OPEN_IN_NEW, 16, Color.DARKGREEN));
    open.setTooltip(new Tooltip("Open behavior"));
    open.setCursor(Cursor.HAND);
    open.setOnMouseClicked(
        event -> {
          event.consume();
          openHandler.accept(normalized);
        });
    var forget = new Label(null, new IconLabel(Material2AL.DELETE, 16, Color.DARKRED));
    forget.setTooltip(new Tooltip("Remove from recent files"));
    forget.setCursor(Cursor.HAND);
    forget.setOnMouseClicked(
        event -> {
          event.consume();
          forgetHandler.accept(normalized);
        });

    var header =
        new HBox(
            8,
            new IconLabel(behaviorIcon, 17, Theme.FOREGROUND_COLOR),
            title,
            spacer,
            open,
            forget);
    header.setAlignment(Pos.CENTER_LEFT);
    var parent = normalized.getParent();
    var location = new Label(parent == null ? normalized.toString() : parent.toString());
    location.setWrapText(true);
    location.setStyle("-fx-font-size: 11px;");
    card.setHeader(header);
    card.setBody(new VBox(location));
    card.setFooter(new Label(modified(normalized)));
    card.setOnMouseClicked(event -> openHandler.accept(normalized));
    getChildren().add(card);
  }

  private String modified(Path file) {
    try {
      return "Edited " + TIME.format(Files.getLastModifiedTime(file).toInstant());
    } catch (IOException e) {
      return "File unavailable";
    }
  }
}

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
import org.integratedmodelling.klab.ide.components.ManagedBehaviorMirrors;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;
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
    this(
        file,
        behaviorIcon,
        null,
        ManagedBehaviorMirrors.LocalState.NOT_MANAGED,
        openHandler,
        forgetHandler);
  }

  public BehaviorFileCard(
      Path file,
      Ikon behaviorIcon,
      ManagedBehaviorMirrors.Origin managedOrigin,
      ManagedBehaviorMirrors.LocalState localState,
      Consumer<Path> openHandler,
      Consumer<Path> forgetHandler) {
    Path normalized = file.toAbsolutePath().normalize();
    var card = new Card();
    var title =
        new Label(
            managedOrigin == null
                ? normalized.getFileName().toString()
                : managedOrigin.behaviorUrn());
    title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
    title.setMaxWidth(210);
    title.setTooltip(
        new Tooltip(
            managedOrigin == null
                ? normalized.toString()
                : "Managed behavior in "
                    + managedOrigin.projectUrn()
                    + System.lineSeparator()
                    + "Service: "
                    + managedOrigin.serviceId()
                    + System.lineSeparator()
                    + "Local mirror: "
                    + normalized));

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
    var location =
        new Label(
            managedOrigin == null
                ? parent == null ? normalized.toString() : parent.toString()
                : "Local mirror: " + normalized);
    location.setWrapText(true);
    location.setStyle(
        managedOrigin == null
            ? "-fx-font-size: 11px;"
            : "-fx-font-size: 10px; -fx-opacity: 0.65;");
    card.setHeader(header);
    if (managedOrigin == null) {
      card.setBody(new VBox(location));
      card.setFooter(new Label(modified(normalized)));
    } else {
      var project =
          new Label(
              "Project: " + managedOrigin.projectUrn(),
              new IconLabel(MaterialDesign.MDI_CLOUD_SYNC, 14, Color.DODGERBLUE));
      project.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
      project.setTooltip(
          new Tooltip(
              "This behavior is managed by project "
                  + managedOrigin.projectUrn()
                  + " on service "
                  + managedOrigin.serviceId()));
      var hasLocalChanges = localState == ManagedBehaviorMirrors.LocalState.MODIFIED;
      var synchronizedState = localState == ManagedBehaviorMirrors.LocalState.SYNCHRONIZED;
      var state =
          new Label(
              hasLocalChanges
                  ? "Local changes not submitted"
                  : synchronizedState ? "Up to date with project" : "Mirror state unavailable",
              new IconLabel(
                  hasLocalChanges
                      ? Material2AL.EDIT
                      : synchronizedState ? Material2AL.CHECK_CIRCLE : Material2AL.ERROR,
                  13,
                  hasLocalChanges
                      ? Color.DARKORANGE
                      : synchronizedState ? Color.GREEN : Color.DARKRED));
      state.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
      state.setTooltip(
          new Tooltip(
              hasLocalChanges
                  ? "This local mirror differs from the last version synchronized with the project"
                  : synchronizedState
                      ? "This local mirror matches the last version synchronized with the project"
                      : "The local mirror could not be compared with its project state"));
      card.setBody(new VBox(4, project, state, location));
      card.setFooter(new Label("Managed project • " + modified(normalized)));
    }
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

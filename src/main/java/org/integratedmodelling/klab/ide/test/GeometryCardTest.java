package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.PrimerLight;
import java.time.Instant;
import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.ide.components.cards.GeometryCard;

/** Standalone showcase for the {@link GeometryCard} component. */
public class GeometryCardTest extends Application {

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    Label selectedMark = new Label("Click a marked timeline to select the nearest mark to the left.");
    selectedMark.getStyleClass().add("geometry-card-chip");

    GridPane cards = new GridPane();
    cards.setHgap(16);
    cards.setVgap(16);
    cards.setStyle("-fx-background-color: -color-bg-subtle;");

    GeometryCard regional = new GeometryCard(regionalGrid());
    regional.setPrefSize(320, 320);

    GeometryCard city = new GeometryCard(cityGrid());
    city.setPrefSize(280, 280);
    city.setTimelineMarks(
        List.of(
            Instant.parse("2024-03-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2024-06-15T00:00:00Z").toEpochMilli(),
            Instant.parse("2024-10-01T00:00:00Z").toEpochMilli()));
    city.setTimelineMarkClickHandler(
        mark -> selectedMark.setText("City mark: " + Instant.ofEpochMilli(mark)));

    GeometryCard temporal = new GeometryCard(Geometry.builder().years(1980, 2030).build());
    temporal.setPrefSize(260, 240);
    temporal.setTimelineMarks(
        List.of(
            Instant.parse("1990-01-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2005-01-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()));
    temporal.setTimelineMarkClickHandler(
        mark -> selectedMark.setText("Temporal mark: " + Instant.ofEpochMilli(mark)));

    GeometryCard abstractGrid = new GeometryCard(Geometry.create("S2(64,32)"));
    abstractGrid.setPrefSize(240, 220);

    cards.add(regional, 0, 0);
    cards.add(city, 1, 0);
    cards.add(temporal, 0, 1);
    cards.add(abstractGrid, 1, 1);

    VBox content = new VBox(12, selectedMark, cards);
    content.setPadding(new Insets(16));
    content.setStyle("-fx-background-color: -color-bg-subtle;");

    ScrollPane scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(false);
    scrollPane.setFitToHeight(false);
    scrollPane.setStyle("-fx-background-color: -color-bg-subtle;");

    Scene scene = new Scene(scrollPane, 760, 520);
    scene
        .getStylesheets()
        .add(
            GeometryCardTest.class
                .getResource("/org/integratedmodelling/klab/ide/custom.css")
                .toExternalForm());

    stage.setTitle("GeometryCard Showcase");
    stage.setScene(scene);
    stage.show();
  }

  private static Geometry regionalGrid() {
    return Geometry.builder().grid(-10.0, 30.0, 35.0, 60.0, "5 km").years(2020, 2025).build();
  }

  private static Geometry cityGrid() {
    var builder = Geometry.builder();
    builder
        .space()
        .regular()
        .boundingBox(-74.25, -73.65, 40.45, 40.98)
        .size(96, 72)
        .resolution("250 m")
        .build();
    builder
        .time()
        .start(Instant.parse("2024-01-01T00:00:00Z").toEpochMilli())
        .end(Instant.parse("2025-01-01T00:00:00Z").toEpochMilli())
        .size(12)
        .resolution(Time.Resolution.Type.MONTH, 1)
        .build();
    return builder.build();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

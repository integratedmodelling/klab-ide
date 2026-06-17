package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.PrimerLight;
import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.ide.components.cards.MetadataCard;

/** Standalone showcase for the {@link MetadataCard} component. */
public class MetadataCardTest extends Application {

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    Metadata metadata = sampleMetadata();
    Label status = new Label("Tree values can be edited with Enter or Tab.");
    status.getStyleClass().add("metadata-card-chip");

    MetadataCard flat =
        new MetadataCard(
            metadata,
            new MetadataCard.Options()
                .unsupportedValuePolicy(MetadataCard.UnsupportedValuePolicy.SHOW_AS_STRING)
                .inlineStringLimit(58));
    flat.setPrefSize(330, 230);

    MetadataCard tree =
        new MetadataCard(
            metadata,
            new MetadataCard.Options()
                .pathTree(true)
                .unsupportedValuePolicy(MetadataCard.UnsupportedValuePolicy.SHOW_AS_STRING)
                .complexValueRenderer(MetadataCardTest::renderComplexValue)
                .editHandler(
                    (key, oldValue, editedValue) -> {
                      status.setText(key + ": " + oldValue + " -> " + editedValue + " (accepted)");
                      return true;
                    })
                .inlineStringLimit(48));
    tree.setPrefSize(360, 270);

    MetadataCard strict =
        new MetadataCard(
            metadata,
            new MetadataCard.Options()
                .unsupportedValuePolicy(MetadataCard.UnsupportedValuePolicy.SKIP)
                .complexValueRenderer(MetadataCardTest::renderComplexValue)
                .inlineStringLimit(44));
    strict.setPrefSize(300, 190);

    CheckBox treeMode = new CheckBox("Tree mode");
    treeMode.setSelected(true);
    treeMode.setOnAction(e -> tree.setPathTree(treeMode.isSelected()));

    HBox controls = new HBox(12, treeMode, status);
    controls.setAlignment(Pos.CENTER_LEFT);

    GridPane grid = new GridPane();
    grid.setHgap(16);
    grid.setVgap(16);
    grid.add(flat, 0, 0);
    grid.add(tree, 1, 0);
    grid.add(strict, 0, 1);

    VBox content = new VBox(12, controls, grid);
    content.setPadding(new Insets(16));
    content.setStyle("-fx-background-color: -color-bg-subtle;");

    ScrollPane scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(false);
    scrollPane.setFitToHeight(false);
    scrollPane.setStyle("-fx-background-color: -color-bg-subtle;");

    Scene scene = new Scene(scrollPane, 820, 560);
    scene
        .getStylesheets()
        .add(
            MetadataCardTest.class
                .getResource("/org/integratedmodelling/klab/ide/custom.css")
                .toExternalForm());

    stage.setTitle("MetadataCard Showcase");
    stage.setScene(scene);
    stage.show();
  }

  private static Metadata sampleMetadata() {
    return Metadata.create(
        Metadata.DC_TITLE,
        "Urban water balance",
        Metadata.DC_COMMENT,
        "This metadata value is intentionally long. It should remain compact in the card "
            + "while still allowing the full text to be browsed without stretching the "
            + "visualization beyond its preferred size. The popup viewer is auto-hidden.",
        "analysis.version",
        3,
        "analysis.threshold",
        0.734,
        "analysis.enabled",
        true,
        "runtime.cache.enabled",
        false,
        "runtime.cache.ttl.hours",
        24,
        "runtime.execution.mode",
        ExecutionMode.SCHEDULED,
        "runtime.notes",
        "A second long text nested under a dot-separated path, useful for checking tree-mode "
            + "string display and editing behavior.",
        "visual.palette",
        List.of("forest", "water", "urban", "cropland"),
        "visual.style",
        new DemoStyle("graduated", 5),
        "storage.url",
        "file:/data/klab/resources/urban-water");
  }

  private static Node renderComplexValue(String key, Object value) {
    if (value instanceof List<?> values) {
      FlowPane tags = new FlowPane(3, 3);
      tags.setMaxWidth(180);
      for (Object item : values) {
        Label label = new Label(item.toString());
        label.getStyleClass().add("metadata-card-chip");
        tags.getChildren().add(label);
      }
      return tags;
    }
    return null;
  }

  private record DemoStyle(String mode, int classes) {}

  private enum ExecutionMode {
    MANUAL,
    SCHEDULED,
    REAL_TIME
  }

  public static void main(String[] args) {
    launch(args);
  }
}

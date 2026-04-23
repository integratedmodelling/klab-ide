package org.integratedmodelling.klab.ide.test;

import atlantafx.base.controls.Card;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.ide.components.cards.AssetViewComponent;
import org.integratedmodelling.klab.ide.components.cards.BaseAssetViewComponent;
import org.integratedmodelling.klab.ide.components.generic.AutoScrollPane;

import java.util.ArrayList;
import java.util.List;

/** A component that demonstrates the AutoScrollPane with a list of sample components. */
public class AutoScrollDemoViewComponent extends BaseAssetViewComponent {

  public AutoScrollDemoViewComponent() {
    super(AssetViewComponent.Type.Object, "Auto Scroll Demo", true);
  }

  @Override
  protected Node createContent() {
    var card = new Card();
    VBox content = new VBox(20);
    content.setPadding(new Insets(20));

    // Create some sample components to scroll
    List<Node> horizontalComponents = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      Label label = new Label("Horizontal Component " + i);
      label.setPrefWidth(200);
      label.setPrefHeight(100);
      label.setAlignment(Pos.CENTER);
      label.setStyle(
          "-fx-background-color: "
              + getRandomColor()
              + "; -fx-text-fill: white; -fx-font-weight: bold;");
      horizontalComponents.add(label);
    }

    // Create a horizontal auto-scroll pane
    AutoScrollPane horizontalScroller = new AutoScrollPane(Orientation.HORIZONTAL, 50);
    horizontalScroller.setPrefHeight(120);
    horizontalScroller.setPrefWidth(400);
    horizontalScroller.setComponents(horizontalComponents);

    // Create some sample components to scroll vertically
    List<Node> verticalComponents = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      Label label = new Label("Vertical Component " + i);
      label.setPrefWidth(200);
      label.setPrefHeight(100);
      label.setAlignment(Pos.CENTER);
      label.setStyle(
          "-fx-background-color: "
              + getRandomColor()
              + "; -fx-text-fill: white; -fx-font-weight: bold;");
      verticalComponents.add(label);
    }
    // Create a vertical auto-scroll pane
    AutoScrollPane verticalScroller = new AutoScrollPane(Orientation.VERTICAL, 50);
    verticalScroller.setPrefHeight(300);
    verticalScroller.setPrefWidth(220);
    verticalScroller.setComponents(verticalComponents);

    // Create controls for the horizontal scroller
    Label horizontalLabel = new Label("Horizontal Scroller");
    horizontalLabel.setStyle("-fx-font-weight: bold;");
    Slider horizontalSpeedSlider = new Slider(10, 200, 50);
    horizontalSpeedSlider.setShowTickLabels(true);
    horizontalSpeedSlider.setShowTickMarks(true);
    horizontalSpeedSlider
        .valueProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              horizontalScroller.setScrollSpeed(newVal.doubleValue());
            });
    Button horizontalToggleButton = new Button("Pause");
    horizontalToggleButton.setOnAction(
        e -> {
          if (horizontalToggleButton.getText().equals("Pause")) {
            horizontalScroller.stopScrolling();
            horizontalToggleButton.setText("Resume");
          } else {
            horizontalScroller.startScrolling();
            horizontalToggleButton.setText("Pause");
          }
        });
    HBox horizontalControls =
        new HBox(10, new Label("Speed:"), horizontalSpeedSlider, horizontalToggleButton);
    horizontalControls.setAlignment(Pos.CENTER_LEFT);

    // Create controls for the vertical scroller
    Label verticalLabel = new Label("Vertical Scroller");
    verticalLabel.setStyle("-fx-font-weight: bold;");
    Slider verticalSpeedSlider = new Slider(10, 200, 50);
    verticalSpeedSlider.setShowTickLabels(true);
    verticalSpeedSlider.setShowTickMarks(true);
    verticalSpeedSlider
        .valueProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              verticalScroller.setScrollSpeed(newVal.doubleValue());
            });
    Button verticalToggleButton = new Button("Pause");
    verticalToggleButton.setOnAction(
        e -> {
          if (verticalToggleButton.getText().equals("Pause")) {
            verticalScroller.stopScrolling();
            verticalToggleButton.setText("Resume");
          } else {
            verticalScroller.startScrolling();
            verticalToggleButton.setText("Pause");
          }
        });
    HBox verticalControls =
        new HBox(10, new Label("Speed:"), verticalSpeedSlider, verticalToggleButton);
    verticalControls.setAlignment(Pos.CENTER_LEFT);

    // Add everything to the content
    VBox horizontalSection = new VBox(10, horizontalLabel, horizontalScroller, horizontalControls);
    VBox verticalSection = new VBox(10, verticalLabel, verticalScroller, verticalControls);
    HBox scrollers = new HBox(20, horizontalSection, verticalSection);
    content.getChildren().add(scrollers);

    card.setBody(content);
    this.getChildren().add(card);
    return card;
  }

  private String getRandomColor() {
    String[] colors = {
      "#3498db", // Blue
      "#e74c3c", // Red
      "#2ecc71", // Green
      "#f39c12", // Orange
      "#9b59b6" // Purple
    };
    return colors[(int) (Math.random() * colors.length)];
  }
}

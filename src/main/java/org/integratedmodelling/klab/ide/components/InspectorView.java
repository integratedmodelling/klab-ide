package org.integratedmodelling.klab.ide.components;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.integratedmodelling.klab.ide.Theme;

public class InspectorView extends BorderPane {

  private static final double INSPECTOR_HEIGHT = 300;

  public Object currentObject = null;
  private final StackPane contentArea = new StackPane();

  public InspectorView() {
    super();
    setMinSize(0, INSPECTOR_HEIGHT);
    setPrefHeight(INSPECTOR_HEIGHT);
    setMaxSize(Double.MAX_VALUE, INSPECTOR_HEIGHT);
    HBox.setHgrow(this, Priority.ALWAYS);
    getStyleClass().add("inspector-view");

    contentArea.getStyleClass().add("inspector-content");
    var clip = new Rectangle();
    clip.widthProperty().bind(contentArea.widthProperty());
    clip.heightProperty().bind(contentArea.heightProperty());
    contentArea.setClip(clip);
    setCenter(contentArea);
  }

  public Object getCurrentObject() {
    return currentObject;
  }

  public void inspect(Object value) {

    currentObject = value;

    Platform.runLater(
        () -> {
          if (value == null) {
            contentArea.getChildren().clear();
            return;
          }

          var component = Theme.getDisplayObject(value, Theme.Detail.CARD);
          if (component instanceof Node node) {
            configureInspectableNode(node);
            // TODO set up navigation for previous components
            contentArea.getChildren().setAll(node);
          }
        });
  }

  private void configureInspectableNode(Node node) {
    HBox.setHgrow(node, Priority.ALWAYS);
    VBox.setVgrow(node, Priority.ALWAYS);

    if (node instanceof Region region) {
      region.setMinSize(0, 0);
      region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }
  }
}

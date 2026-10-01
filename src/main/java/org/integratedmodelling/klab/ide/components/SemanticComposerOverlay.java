package org.integratedmodelling.klab.ide.components;

import java.util.function.Function;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Scene-sized modal host shared by observable selection and digital-twin composition. */
final class SemanticComposerOverlay {
  private SemanticComposerOverlay() {}

  static Stage show(Node owner, Function<Runnable, SemanticComposer> factory, Runnable onClosed) {
    var ownerScene = owner.getScene();
    if (ownerScene == null || ownerScene.getWindow() == null)
      throw new IllegalStateException("The composer needs an attached owner window");
    var ownerWindow = ownerScene.getWindow();
    var stage = new Stage(StageStyle.TRANSPARENT);
    stage.initOwner(ownerWindow); stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle("Compose observable"); stage.setResizable(false);
    var composer = factory.apply(stage::close);
    var panel = new ScrollPane(composer) {
      @Override protected double computePrefHeight(double width) {
        double contentWidth = width < 0 ? composer.prefWidth(-1)
            : Math.max(0, width - snappedLeftInset() - snappedRightInset());
        return Math.ceil(composer.prefHeight(contentWidth)) + snappedTopInset() + snappedBottomInset() + 2;
      }
    };
    panel.setId("semantic-composer-panel");
    panel.setFitToWidth(true); panel.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    panel.setMinSize(0, 0); panel.setMaxWidth(900); panel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    panel.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;"
        + " -fx-background-radius: 8; -fx-padding: 0;");
    var backdrop = new StackPane(panel);
    backdrop.setId("semantic-composer-backdrop"); backdrop.setPadding(new Insets(24));
    backdrop.setStyle("-fx-background-color: rgba(20, 26, 36, 0.42);");
    panel.prefWidthProperty().bind(backdrop.widthProperty().subtract(48));
    var scene = new Scene(backdrop, Color.TRANSPARENT);
    scene.getStylesheets().setAll(ownerScene.getStylesheets()); stage.setScene(scene);
    Runnable synchronize = () -> {
      stage.setX(ownerWindow.getX() + ownerScene.getX());
      stage.setY(ownerWindow.getY() + ownerScene.getY());
      stage.setWidth(ownerScene.getWidth()); stage.setHeight(ownerScene.getHeight());
    };
    InvalidationListener geometry = o -> synchronize.run();
    ownerWindow.xProperty().addListener(geometry); ownerWindow.yProperty().addListener(geometry);
    ownerScene.widthProperty().addListener(geometry); ownerScene.heightProperty().addListener(geometry);
    ChangeListener<Scene> detached = (o, before, after) -> { if (after != ownerScene) stage.close(); };
    ChangeListener<Boolean> hidden = (o, before, showing) -> { if (!showing) stage.close(); };
    owner.sceneProperty().addListener(detached); ownerWindow.showingProperty().addListener(hidden);
    stage.setOnHidden(e -> {
      ownerWindow.xProperty().removeListener(geometry); ownerWindow.yProperty().removeListener(geometry);
      ownerScene.widthProperty().removeListener(geometry); ownerScene.heightProperty().removeListener(geometry);
      owner.sceneProperty().removeListener(detached); ownerWindow.showingProperty().removeListener(hidden);
      composer.close(); onClosed.run();
    });
    synchronize.run(); stage.show(); synchronize.run();
    return stage;
  }
}

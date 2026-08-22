package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.components.generic.WordPressPostViewer;

/** Small live showcase for the WordPress-backed carousel. */
public class WordPressPostViewerTest extends Application {
  private static final String SITE = "https://aries.integratedmodelling.org";

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    WordPressPostViewer viewer = new WordPressPostViewer(SITE);
    viewer.setPrefHeight(300);
    viewer.setMaxWidth(Double.MAX_VALUE);
    Label status = new Label("Loading posts from " + SITE);
    status.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    Button pages = new Button("Show pages");
    pages.setOnAction(event -> {
      viewer.setShowingPages(true);
      pages.setDisable(true);
      status.setText("Loading pages from " + SITE);
      viewer.load().whenComplete((ignored, error) ->
          javafx.application.Platform.runLater(() -> {
            pages.setDisable(false);
            status.setText(error == null ? "Pages loaded — double-click a card to open it"
                : "Unable to load pages: " + error.getCause());
          }));
    });

    VBox root = new VBox(10, status, viewer, pages);
    root.setPadding(new Insets(16));
    VBox.setVgrow(viewer, javafx.scene.layout.Priority.NEVER);
    Scene scene = new Scene(root, 600, 380);
    scene.getStylesheets().add(
        WordPressPostViewerTest.class
            .getResource("/org/integratedmodelling/klab/ide/custom.css")
            .toExternalForm());
    stage.setScene(scene);
    stage.setTitle("WordPress Post Viewer");
    stage.show();

    viewer.load().whenComplete((ignored, error) ->
        javafx.application.Platform.runLater(() -> status.setText(
            error == null ? "Posts loaded — double-click a card to open it"
                : "Unable to load posts: " + error.getCause())));
  }

  public static void main(String[] args) { launch(args); }
}

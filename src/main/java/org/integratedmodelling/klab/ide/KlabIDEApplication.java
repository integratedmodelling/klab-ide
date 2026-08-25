package org.integratedmodelling.klab.ide;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.ide.components.DownloadMonitor;
import org.integratedmodelling.klab.ide.utils.AppContext;

public class KlabIDEApplication extends Application {

  public static final int MIN_WIDTH = 1200;
  public static final int SIDEBAR_WIDTH = 270;

  private static Scene scene;
  private static KlabIDEApplication instance;

  private boolean inspectorShown;
  private static Stage primaryStage;
  private KlabIDEController controller;
  private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

  @Override
  public void start(Stage stage) throws IOException {

    instance = this;
    primaryStage = stage;

    // Add icon to the stage
    stage.getIcons().add(new Image(getClass().getResourceAsStream("/package/linux/klab.png")));

    /*
     * TODO choose theme from settings and expose it to components
     */
    Application.setUserAgentStylesheet(Theme.CURRENT_THEME.getStylesheet());
    AppContext.setHostServices(getHostServices());

    FXMLLoader fxmlLoader = new FXMLLoader(KlabIDEApplication.class.getResource("ide.fxml"));
    Parent applicationRoot = fxmlLoader.load();
    controller = fxmlLoader.getController();
    var waitScreen = createWaitScreen();
    var sceneRoot = new StackPane(applicationRoot, waitScreen.container());
    applicationRoot.setDisable(true);
    scene = new Scene(sceneRoot, 1480, 1060);
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownController, "klab-ide-shutdown"));
    scene.getStylesheets().add(getClass().getResource("custom.css").toExternalForm());
    stage.setTitle("k.LAB Modeler :: v1.0 pre-alpha :: © 2025 Integrated Modelling Partnership");
    stage.setOnCloseRequest(
        event -> {
          Platform.exit();
        });
    stage.setScene(scene);
    //    // Initialize the notification manager
    //    this.notificationManager = new NotificationManager(scene);

    stage.show();

    controller
        .startInitialization(waitScreen.distributionProgress())
        .whenComplete(
            (unused, failure) ->
                Platform.runLater(
                    () -> {
                      if (failure == null) {
                        applicationRoot.setDisable(false);
                        sceneRoot.getChildren().remove(waitScreen.container());
                      } else {
                        var cause = rootCause(failure);
                        Logging.INSTANCE.error(cause);
                        waitScreen.showFailure(cause);
                      }
                    }));
  }

  private WaitScreen createWaitScreen() {
    var progress = new ProgressIndicator();
    progress.setMaxSize(72, 72);

    var title = new Label("Initializing k.LAB Modeler");
    title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
    var detail = new Label("Authenticating and connecting services…");
    detail.setWrapText(true);
    detail.setMaxWidth(520);
    detail.setAlignment(Pos.CENTER);

    var distributionProgress = new DownloadMonitor();
    distributionProgress.setVisible(false);
    distributionProgress.setManaged(false);
    distributionProgress.setMaxWidth(680);
    distributionProgress
        .visibleProperty()
        .addListener(
            (observable, wasVisible, isVisible) -> {
              progress.setVisible(!isVisible);
              progress.setManaged(!isVisible);
              if (isVisible) {
                title.setText("Updating the k.LAB software stack");
                detail.setText("The IDE will become available when synchronization finishes.");
              }
            });

    var content = new VBox(18, progress, title, detail, distributionProgress);
    content.setAlignment(Pos.CENTER);
    content.setMaxSize(760, 520);
    content.setStyle(
        "-fx-background-color: -color-bg-default; -fx-background-radius: 12;"
            + " -fx-border-color: -color-border-default; -fx-border-radius: 12; -fx-padding: 36;");

    var container = new StackPane(content);
    container.setStyle("-fx-background-color: rgba(0, 0, 0, 0.38);");
    return new WaitScreen(container, progress, title, detail, distributionProgress);
  }

  private static Throwable rootCause(Throwable failure) {
    var cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private record WaitScreen(
      StackPane container,
      ProgressIndicator progress,
      Label title,
      Label detail,
      DownloadMonitor distributionProgress) {

    private void showFailure(Throwable failure) {
      progress.setVisible(false);
      progress.setManaged(false);
      title.setText("Initialization failed");
      title.setStyle(
          "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -color-danger-fg;");
      var message = failure.getMessage();
      detail.setText(
          message == null || message.isBlank()
              ? "The Modeler could not be initialized. Close the window and inspect the application log."
              : "The Modeler could not be initialized: " + message);
    }
  }

  @Override
  public void stop() {
    shutdownController();
    System.exit(0);
  }

  private void shutdownController() {
    if (shutdownRequested.compareAndSet(false, true) && controller != null) {
      controller.shutdown(false);
    }
  }

  public static Stage primaryStage() {
    return primaryStage;
  }

  public static KlabIDEApplication instance() {
    return instance;
  }

  public static Scene scene() {
    return scene;
  }

  public static void main(String[] args) {
    launch();
  }

  public void setInspectorShown(boolean b) {
    this.inspectorShown = b;
  }

  public boolean isInspectorShown() {
    return this.inspectorShown;
  }
}

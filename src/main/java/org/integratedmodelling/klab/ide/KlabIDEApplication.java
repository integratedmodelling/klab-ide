package org.integratedmodelling.klab.ide;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
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
    scene = new Scene(fxmlLoader.load(), 1480, 1060);
    controller = fxmlLoader.getController();
    Runtime.getRuntime()
        .addShutdownHook(new Thread(this::shutdownController, "klab-ide-shutdown"));
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

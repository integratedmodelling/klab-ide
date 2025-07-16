package org.integratedmodelling.klab.ide;

import java.io.IOException;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.eclipse.xtext.ide.server.ServerLauncher;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.notifications.NotificationManager;

public class KlabIDEApplication extends Application {

  public static final int MIN_WIDTH = 1200;
  public static final int SIDEBAR_WIDTH = 270;

  private static Scene scene;
  private static KlabIDEApplication instance;

  private Thread lspThread;
  private boolean inspectorShown;
//  private NotificationManager notificationManager;

  @Override
  public void start(Stage stage) throws IOException {

    instance = this;

    // Add icons to the stage
    //    stage
    //        .getIcons()
    //        .addAll(
    //            new Image(getClass().getResourceAsStream("/icons/app_16.png")),
    //            new Image(getClass().getResourceAsStream("/icons/app_32.png")),
    //            new Image(getClass().getResourceAsStream("/icons/app_64.png")));

    /*
     * TODO choose theme from settings and expose it to components
     */
    Application.setUserAgentStylesheet(Theme.CURRENT_THEME.getStylesheet());

    /*
     * TODO if local, at this point it should be enough to launch
     *  org.eclipse.xtext.ide.server.ServerLauncher to start the LSP server for all languages on the
     *  classpath.
     */
    Logging.INSTANCE.info("Starting language services for k.LAB language editors");
    this.lspThread =
        new Thread(
            () -> {
              try {
                ServerLauncher.main(new String[0]);
              } catch (Throwable t) {
                Logging.INSTANCE.error(
                    "Error launching LSP server: language services not available", t);
              }
            });

    this.lspThread.start();

    FXMLLoader fxmlLoader = new FXMLLoader(KlabIDEApplication.class.getResource("ide.fxml"));
    scene = new Scene(fxmlLoader.load(), 1480, 1060);
    scene.getStylesheets().add(getClass().getResource("custom.css").toExternalForm());
    stage.setTitle("k.LAB Modeler :: v1.0 pre-alpha :: © 2025 Integrated Modelling Partnership");
    stage.setOnCloseRequest(
        event -> {
          /* TODO save status, ask to stop engine etc. */
          System.exit(0);
        });
    stage.setScene(scene);
//    // Initialize the notification manager
//    this.notificationManager = new NotificationManager(scene);

    stage.show();
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

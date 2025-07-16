package org.integratedmodelling.klab.ide.test;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.notifications.NotificationManager;

/**
 * Test class to verify notification functionality
 */
public class NotificationTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create a simple UI for testing
//        VBox root = new VBox(10);
//        Scene scene = new Scene(root, 400, 300);
//        NotificationManager notificationManager = new NotificationManager(scene);
//
//        // Create test buttons
//        Button infoButton = new Button("Show Info Notification");
//        infoButton.setOnAction(e ->
//                                       notificationManager.showInformation("Info", "This is an information notification"));
//
//        Button warningButton = new Button("Show Warning Notification");
//        warningButton.setOnAction(e ->
//                                          notificationManager.showWarning("Warning", "This is a warning notification"));
//
//        Button errorButton = new Button("Show Error Notification");
//        errorButton.setOnAction(e ->
//                                        notificationManager.showError("Error", "This is an error notification"));
//
//        Button successButton = new Button("Show Success Notification");
//        successButton.setOnAction(e ->
//                                          notificationManager.showSuccess("Success", "This is a success notification"));
//
//        root.getChildren().addAll(infoButton, warningButton, errorButton, successButton);
//
//        primaryStage.setTitle("Notification Test");
//        primaryStage.setScene(scene);
//        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

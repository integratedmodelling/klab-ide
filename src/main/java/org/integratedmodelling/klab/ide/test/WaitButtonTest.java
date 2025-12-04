package org.integratedmodelling.klab.ide.test;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;

/**
 * A simple test application for the WaitButton component.
 * This class demonstrates the functionality of the WaitButton by creating
 * buttons with different task durations and outcomes (success/failure).
 * It also demonstrates the reset() method that brings buttons back to their initial state.
 */
public class WaitButtonTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        
        // Add a title
        Label titleLabel = new Label("WaitButton Test");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Create a button with a short successful task (1 second)
        WaitButton shortSuccessButton = new WaitButton("Short Success (1s)");
        shortSuccessButton.setOnAction(() -> {
            try {
                // Simulate a short task
                Thread.sleep(1000);
                return true; // Task succeeded
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false; // Task failed
            }
        });
        
        // Create a button with a medium successful task (3 seconds)
        WaitButton mediumSuccessButton = new WaitButton("Medium Success (3s)");
        mediumSuccessButton.setOnAction(() -> {
            try {
                // Simulate a medium task
                Thread.sleep(3000);
                return true; // Task succeeded
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false; // Task failed
            }
        });
        
        // Create a button with a short failing task (1 second)
        WaitButton shortFailButton = new WaitButton("Short Fail (1s)");
        shortFailButton.setOnAction(() -> {
            try {
                // Simulate a short task
                Thread.sleep(1000);
                return false; // Task failed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false; // Task failed
            }
        });
        
        // Create a button with a medium failing task (3 seconds)
        WaitButton mediumFailButton = new WaitButton("Medium Fail (3s)");
        mediumFailButton.setOnAction(() -> {
            try {
                // Simulate a medium task
                Thread.sleep(3000);
                return false; // Task failed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false; // Task failed
            }
        });
        
        // Create a button with a random outcome (2 seconds)
        WaitButton randomButton = new WaitButton("Random Outcome (2s)");
        randomButton.setOnAction(() -> {
            try {
                // Simulate a task
                Thread.sleep(2000);
                // 50% chance of success
                return Math.random() > 0.5;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false; // Task failed
            }
        });
        
        // Add a section label for success buttons
        Label successLabel = new Label("Success buttons (show checkmark when done):");
        successLabel.setStyle("-fx-font-weight: bold;");
        
        // Add a section label for failure buttons
        Label failureLabel = new Label("Failure buttons (show error mark when done):");
        failureLabel.setStyle("-fx-font-weight: bold;");
        
        // Add a section label for random outcome button
        Label randomLabel = new Label("Random outcome button (50% chance of success):");
        randomLabel.setStyle("-fx-font-weight: bold;");
        
        // Add a section label for reset functionality
        Label resetLabel = new Label("Reset functionality (brings buttons back to initial state):");
        resetLabel.setStyle("-fx-font-weight: bold;");
        
        // Create a container for reset buttons
        HBox resetButtonsContainer = new HBox(10);
        resetButtonsContainer.setAlignment(Pos.CENTER);
        
        // Create buttons to reset specific buttons
        Button resetSuccessButton = new Button("Reset Success Buttons");
        resetSuccessButton.setOnAction(e -> {
            shortSuccessButton.reset();
            mediumSuccessButton.reset();
        });
        
        Button resetFailButton = new Button("Reset Failure Buttons");
        resetFailButton.setOnAction(e -> {
            shortFailButton.reset();
            mediumFailButton.reset();
        });
        
        Button resetRandomButton = new Button("Reset Random Button");
        resetRandomButton.setOnAction(e -> {
            randomButton.reset();
        });
        
        Button resetAllButton = new Button("Reset All Buttons");
        resetAllButton.setOnAction(e -> {
            shortSuccessButton.reset();
            mediumSuccessButton.reset();
            shortFailButton.reset();
            mediumFailButton.reset();
            randomButton.reset();
        });
        
        // Add reset buttons to container
        resetButtonsContainer.getChildren().addAll(
            resetSuccessButton, 
            resetFailButton, 
            resetRandomButton, 
            resetAllButton
        );
        
        // Add components to the root
        root.getChildren().addAll(
            titleLabel,
            successLabel,
            shortSuccessButton,
            mediumSuccessButton,
            failureLabel,
            shortFailButton,
            mediumFailButton,
            randomLabel,
            randomButton,
            resetLabel,
            resetButtonsContainer
        );
        
        // Create the scene
        Scene scene = new Scene(root, 600, 600);
        
        // Set up the stage
        primaryStage.setTitle("WaitButton Test");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    /**
     * Main method to launch the application.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
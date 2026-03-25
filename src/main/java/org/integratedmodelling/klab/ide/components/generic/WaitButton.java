package org.integratedmodelling.klab.ide.components.generic;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * A button that shows a spinning progress indicator when clicked and executes a task. The button is
 * disabled during task execution and returns to its normal state when the task completes.
 *
 * <p>The button shows: - A right arrow icon before the task is started - A spinning progress
 * indicator during task execution - A checkmark icon if the task succeeds - An error icon if the
 * task fails
 *
 * <p>Usage example:
 *
 * <pre>
 * WaitButton button = new WaitButton("Click Me");
 * button.setOnAction(() -> {
 *     // This code will run in a background thread
 *     try {
 *         Thread.sleep(2000); // ... do work
 *         return true; // Task succeeded
 *     } catch (InterruptedException e) {
 *         Thread.currentThread().interrupt();
 *         return false; // Task failed
 *     }
 * });
 * </pre>
 */
public class WaitButton extends Button {

  private final HBox contentPane;
  private final Label textLabel;
  private final StackPane iconContainer;
  private final ProgressIndicator progressIndicator;
  private final IconLabel arrowIcon;
  private final IconLabel successIcon;
  private final IconLabel errorIcon;
  private final ExecutorService executorService;
  private Supplier<Boolean> action;
  private String originalText;
  private boolean taskSucceeded;

  public WaitButton(String text) {
    this(text, 16);
  }

  /**
   * Creates a new WaitButton with the specified text.
   *
   * @param text The text to display on the button
   */
  public WaitButton(String text, int size) {
    super();
    this.originalText = text;
    this.executorService = Executors.newCachedThreadPool();

    // Create text label
    this.textLabel = new Label(text);
    textLabel.setAlignment(Pos.CENTER);
    HBox.setHgrow(textLabel, Priority.ALWAYS);

    // Create icons for different states
    this.arrowIcon = new IconLabel(Material2MZ.NAVIGATE_NEXT, size, Color.GRAY);
    this.successIcon = new IconLabel(Material2AL.CHECK_CIRCLE, size, Color.GREEN);
    this.errorIcon = new IconLabel(Material2AL.ERROR, size, Color.RED);

    // Initially only show the arrow icon
    this.arrowIcon.setVisible(true);
    this.successIcon.setVisible(false);
    this.errorIcon.setVisible(false);

    // Create progress indicator with AtlantaFX styling
    this.progressIndicator = new ProgressIndicator();
    // Make the indicator larger for better visibility
    progressIndicator.setMaxSize(size + 8, size + 8);
    progressIndicator.setMinSize(size + 8, size + 8);
    progressIndicator.setPrefSize(size + 8, size + 8);
    progressIndicator.setVisible(false);

    // Ensure the indicator is always fully visible, even when the button is in a "disabled" state
    progressIndicator.setStyle("-fx-opacity: 1.0; -fx-background-color: transparent;");

    // Apply AtlantaFX styling for consistent look and feel
    Styles.addStyleClass(progressIndicator, Styles.ACCENT);

    // Make sure the indicator is always on top
    progressIndicator.setViewOrder(-1);

    // Create a stack pane to hold the icons and progress indicator
    this.iconContainer = new StackPane();
    iconContainer.getChildren().addAll(arrowIcon, successIcon, errorIcon, progressIndicator);

    // Create content pane to hold both text and icon container
    this.contentPane = new HBox(5);
    contentPane.setAlignment(Pos.CENTER);
    contentPane.getChildren().addAll(textLabel, iconContainer);

    // Set up the button
    setGraphic(contentPane);
    getStyleClass().add("wait-button");

    // Set up the action handler
    super.setOnAction(
        event -> {
          if (action != null) {
            executeTask();
          }
        });
  }

  /**
   * Sets the action to be executed when the button is clicked. The action will be executed in a
   * background thread. The action should return true if the task succeeded, false otherwise.
   *
   * @param action The action to execute
   */
  public void setOnAction(Supplier<Boolean> action) {
    this.action = action;
  }

  /** Executes the task in a background thread and updates the button UI accordingly. */
  private void executeTask() {
    // Instead of disabling the entire button (which affects all children),
    // just disable the click functionality and update the visual state manually
    setMouseTransparent(true); // Prevents clicks but doesn't change visual styling

    // Hide all icons
    arrowIcon.setVisible(false);
    successIcon.setVisible(false);
    errorIcon.setVisible(false);

    // Show progress indicator with full opacity
    progressIndicator.setVisible(true);
    progressIndicator.setProgress(-1); // Indeterminate progress

    // Apply a visual indication that the button is processing
    setStyle("-fx-opacity: 0.8;");
    progressIndicator.setStyle("-fx-opacity: 1.0;"); // Ensure indicator remains fully visible

    // Keep the text but make it slightly transparent to indicate processing
    textLabel.setOpacity(0.7);

    Task<Boolean> task =
        new Task<>() {
          @Override
          protected Boolean call() throws Exception {
            try {
              if (action != null) {
                return action.get();
              }
              return false;
            } catch (Exception e) {
              // Log the exception but don't rethrow to ensure cleanup happens
              e.printStackTrace();
              return false;
            }
          }

          @Override
          protected void succeeded() {
            Boolean result = getValue();
            taskSucceeded = result != null && result;
            Platform.runLater(() -> resetButton());
          }

          @Override
          protected void failed() {
            taskSucceeded = false;
            Platform.runLater(() -> resetButton());
          }

          @Override
          protected void cancelled() {
            taskSucceeded = false;
            Platform.runLater(() -> resetButton());
          }
        };

    // Execute the task
    executorService.submit(task);
  }

  /**
   * Resets the button to its original state and shows the appropriate icon based on task result.
   * The success or failure icon will remain visible until reset() is called.
   */
  private void resetButton() {
    // Restore text and opacity
    textLabel.setText(originalText);
    textLabel.setOpacity(1.0);

    // Hide progress indicator
    progressIndicator.setVisible(false);
    progressIndicator.setProgress(0);

    // Show the appropriate icon based on task result
    arrowIcon.setVisible(false);
    successIcon.setVisible(taskSucceeded);
    errorIcon.setVisible(!taskSucceeded);

    // Restore button interactivity and styling
    setMouseTransparent(false);
    setStyle(""); // Remove any custom styling
    progressIndicator.setStyle("-fx-opacity: 1.0;"); // Keep this style for next use
  }

  /**
   * Resets the button to its initial state with the arrow icon visible. This can be used to
   * manually clear the success/failure state.
   */
  public void reset() {
    // Restore text and opacity
    textLabel.setText(originalText);
    textLabel.setOpacity(1.0);

    // Hide progress indicator
    progressIndicator.setVisible(false);
    progressIndicator.setProgress(0);

    // Show only the arrow icon
    arrowIcon.setVisible(true);
    successIcon.setVisible(false);
    errorIcon.setVisible(false);

    // Ensure button is interactive
    setMouseTransparent(false);
    setStyle(""); // Remove any custom styling
  }

  /**
   * Updates the text of the button and stores it for reset.
   *
   * @param text The new text for the button
   */
  public void updateText(String text) {
    textLabel.setText(text);
    // Only update the original text if the button is not in processing state
    if (!isMouseTransparent()) {
      this.originalText = text;
    }
  }

  /**
   * Shuts down the executor service when the button is no longer needed. Call this method to clean
   * up resources when the button is being removed from the scene.
   */
  public void shutdown() {
    executorService.shutdown();
  }
}

package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.PrimerLight;
import java.util.concurrent.CompletableFuture;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;

/** Standalone visual check for each supported {@link WaitButton} lifecycle. */
public class WaitButtonTest extends Application {

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    var blockingSuccess = new WaitButton("Blocking success");
    blockingSuccess.setOnAction(() -> sleepAndReturn(true));

    var asyncSuccess = new WaitButton("Asynchronous success");
    asyncSuccess.setOnActionAsync(() -> delayedResult(true));

    var asyncFailure = new WaitButton("Asynchronous failure");
    asyncFailure.setOnActionAsync(() -> delayedResult(false));

    var externallyManaged = new WaitButton("Externally managed");
    var start = new Button("Wait");
    start.setOnAction(event -> externallyManaged.showWaiting());
    var succeed = new Button("Succeed");
    succeed.setOnAction(event -> externallyManaged.showSucceeded());
    var fail = new Button("Fail");
    fail.setOnAction(event -> externallyManaged.showFailed());
    var reset = new Button("Reset");
    reset.setOnAction(event -> externallyManaged.reset());
    var externalControls = new HBox(6, start, succeed, fail, reset);

    var grid = new GridPane();
    grid.setHgap(16);
    grid.setVgap(14);
    addRow(grid, 0, blockingSuccess, "Runs a blocking Supplier on a worker thread");
    addRow(grid, 1, asyncSuccess, "Waits for a successful CompletionStage");
    addRow(grid, 2, asyncFailure, "Waits for a failed Boolean result");
    addRow(grid, 3, externallyManaged, "Lifecycle controlled by the buttons below");
    grid.add(externalControls, 1, 4, 2, 1);

    var title = new Label("WaitButton contract check");
    title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
    var instructions =
        new Label(
            "Every action should immediately show a moving spinner, reject repeated clicks, "
                + "and finish with the expected icon.");
    instructions.setWrapText(true);

    var root = new VBox(18, title, instructions, grid);
    root.setPadding(new Insets(24));
    stage.setScene(new Scene(root, 720, 390));
    stage.setTitle("WaitButton Test");
    stage.show();
  }

  private static void addRow(GridPane grid, int row, WaitButton button, String description) {
    var state = new Label();
    state.textProperty()
        .bind(Bindings.createStringBinding(() -> button.getState().name(), button.stateProperty()));
    state.setMinWidth(85);
    var buttonAndState = new HBox(10, button, state);
    buttonAndState.setAlignment(Pos.CENTER_LEFT);
    grid.add(buttonAndState, 0, row);
    grid.add(new Label(description), 1, row);
  }

  private static boolean sleepAndReturn(boolean result) {
    try {
      Thread.sleep(2000);
      return result;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static CompletableFuture<Boolean> delayedResult(boolean result) {
    var future = new CompletableFuture<Boolean>();
    var delay = new PauseTransition(Duration.seconds(2));
    delay.setOnFinished(event -> future.complete(result));
    delay.play();
    return future;
  }

  public static void main(String[] args) {
    launch(args);
  }
}

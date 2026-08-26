package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.PrimerLight;
import java.util.concurrent.CompletableFuture;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.components.generic.TaskCancellationButton;

/** Standalone visual check for the ephemeral submission stop control. */
public class TaskCancellationButtonTest extends Application {

  private CompletableFuture<Void> task;

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    var search = new TextField();
    search.setPromptText("Search activities");
    HBox.setHgrow(search, Priority.ALWAYS);
    var stop = new TaskCancellationButton();
    var header = new HBox(0, search, stop);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setStyle("-fx-background-color: -color-neutral-muted;");

    var state = new Label("No task: the stop icon is absent");
    var start = new Button("Start task");
    start.setOnAction(
        event -> {
          task = new CompletableFuture<>();
          var startedTask = task;
          stop.monitor(startedTask);
          state.setText("Running: click the red stop icon in the header");
          startedTask.whenComplete(
              (result, failure) ->
                  Platform.runLater(
                      () ->
                          state.setText(startedTask.isCancelled() ? "Cancelled" : "Completed")));
        });
    var complete = new Button("Complete task");
    complete.setOnAction(event -> {
      if (task != null) task.complete(null);
    });

    var instructions =
        new Label(
            "Start a task: the stop icon must appear to the right of the search field. "
                + "Stopping or completing the task must remove it.");
    instructions.setWrapText(true);
    var controls = new HBox(8, start, complete);
    var root = new VBox(16, instructions, header, controls, state);
    root.setPadding(new Insets(24));

    stage.setScene(new Scene(root, 620, 220));
    stage.setTitle("Submission Stop Control Test");
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

package org.integratedmodelling.klab.ide.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.integratedmodelling.common.runtime.actors.AgentConsole;
import org.integratedmodelling.klab.api.actors.Agent;

/**
 * JavaFX peer for {@link AgentConsole}. It displays remote stdout/stderr and forwards one input
 * line when Enter or Send is pressed.
 */
public final class AgentConsoleView extends BorderPane implements AutoCloseable {

  private final TextArea output = new TextArea();
  private final TextField input = new TextField();
  private final Button send = new Button("Send");
  private AgentConsole console;
  private AutoCloseable outputSubscription;

  public AgentConsoleView() {
    output.setEditable(false);
    output.setWrapText(true);
    input.setPromptText("Send input to the agent");
    HBox.setHgrow(input, Priority.ALWAYS);
    var inputBar = new HBox(6, input, send);
    inputBar.setPadding(new Insets(6, 0, 0, 0));
    setPadding(new Insets(8));
    setCenter(output);
    setBottom(inputBar);
    send.setOnAction(event -> sendInput());
    input.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER) {
            sendInput();
            event.consume();
          }
        });
    setDisable(true);
  }

  public AgentConsoleView(Agent agent) {
    this();
    setAgent(agent);
  }

  /** Replace the current debug target without stopping or disconnecting either agent. */
  public void setAgent(Agent agent) {
    detach();
    output.clear();
    if (agent == null) {
      setDisable(true);
      return;
    }
    console = new AgentConsole(agent);
    outputSubscription =
        console.onOutput(
            chunk ->
                Platform.runLater(
                    () -> {
                      output.appendText(chunk.text());
                      output.positionCaret(output.getLength());
                    }));
    setDisable(false);
    input.requestFocus();
  }

  public Agent getAgent() {
    return console == null ? null : console.getAgent();
  }

  @Override
  public void close() {
    detach();
  }

  private void sendInput() {
    if (console == null) {
      return;
    }
    String line = input.getText();
    input.clear();
    console.sendLine(line);
  }

  private void detach() {
    if (outputSubscription != null) {
      try {
        outputSubscription.close();
      } catch (Exception ignored) {
        // The backing listener is local and teardown should remain idempotent.
      }
      outputSubscription = null;
    }
    if (console != null) {
      console.close();
      console = null;
    }
  }
}

package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.integratedmodelling.common.runtime.actors.AgentConsole;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * JavaFX peer for {@link AgentConsole}. Each view is associated with one single-use agent and
 * remains useful as an execution transcript after that agent stops.
 */
public final class AgentConsoleView extends BorderPane implements AutoCloseable {

  private final TextArea output = new TextArea();
  private final TextField input = new TextField();
  private final Button send = new Button("Send");
  private final Label agentName = new Label();
  private final Label state = new Label("Stopped");
  private final Label started = new Label("Started: —");
  private final Label idle = new Label("Idle: —");
  private final Button stop = new Button();
  private final Timeline statusRefresh =
      new Timeline(
          new KeyFrame(Duration.ZERO, event -> refreshStatus()),
          new KeyFrame(Duration.seconds(1), event -> refreshStatus()));
  private final DateTimeFormatter timestampFormat =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  private final Consumer<Agent> stoppedHandler;
  private Agent agent;
  private AgentConsole console;
  private AutoCloseable outputSubscription;
  private boolean stopRequested;
  private boolean stoppedReported;

  public AgentConsoleView() {
    this(null, ignored -> {});
  }

  public AgentConsoleView(Agent agent) {
    this(agent, ignored -> {});
  }

  public AgentConsoleView(Agent agent, Consumer<Agent> stoppedHandler) {
    this.stoppedHandler = stoppedHandler == null ? ignored -> {} : stoppedHandler;
    output.setEditable(false);
    output.setWrapText(true);
    input.setPromptText("Send input to the agent");
    HBox.setHgrow(input, Priority.ALWAYS);
    var inputBar = new HBox(6, input, send);
    inputBar.setPadding(new Insets(6, 0, 0, 0));

    agentName.getStyleClass().add(Styles.TEXT_BOLD);
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    stop.setGraphic(new IconLabel(Material2MZ.STOP, 14, Color.DARKRED));
    stop.setTooltip(new Tooltip("Stop and release this agent"));
    stop.setAccessibleText("Stop agent");
    stop.getStyleClass().add(Styles.DANGER);
    var statusBar = new HBox(10, agentName, state, spacer, started, idle, stop);
    statusBar.setAlignment(Pos.CENTER_LEFT);
    statusBar.setPadding(new Insets(5, 6, 0, 6));
    statusBar.setStyle("-fx-background-color: -color-neutral-muted;");

    setPadding(new Insets(8));
    setCenter(output);
    setBottom(new VBox(inputBar, statusBar));
    send.setOnAction(event -> sendInput());
    stop.setOnAction(event -> stopAgent());
    input.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER) {
            sendInput();
            event.consume();
          }
        });
    setDisable(true);
    statusRefresh.setCycleCount(Timeline.INDEFINITE);
    if (agent != null) {
      setAgent(agent);
    }
  }

  /** Attach this view to one agent handle without taking ownership of the agent lifecycle. */
  public void setAgent(Agent agent) {
    detach();
    output.clear();
    this.agent = agent;
    stopRequested = false;
    stoppedReported = false;
    if (agent == null) {
      setDisable(true);
      refreshStatus();
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
    statusRefresh.playFromStart();
    focusInput();
  }

  public Agent getAgent() {
    return agent;
  }

  /** Bring keyboard input to the console after its containing tab is selected. */
  public void focusInput() {
    Platform.runLater(input::requestFocus);
  }

  @Override
  public void close() {
    statusRefresh.stop();
    detach();
    agent = null;
  }

  private void sendInput() {
    if (console == null || stopRequested) {
      return;
    }
    String line = input.getText();
    input.clear();
    console.sendLine(line);
  }

  private void stopAgent() {
    if (agent == null || stopRequested) {
      return;
    }
    stopRequested = true;
    state.setText("Stopping…");
    setInteractionEnabled(false);
    if (!agent.stop()) {
      stopRequested = false;
      refreshStatus();
    }
  }

  private void refreshStatus() {
    if (agent == null) {
      agentName.setText("");
      state.setText("Stopped");
      started.setText("Started: —");
      idle.setText("Idle: —");
      setInteractionEnabled(false);
      return;
    }

    var name = agent.getName();
    agentName.setText(name == null || name.isBlank() ? agent.getUrn() : name);
    boolean alive;
    try {
      alive = agent.isAlive();
    } catch (Throwable ignored) {
      alive = false;
    }
    if (alive && !stopRequested) {
      state.setText("Running");
      setInteractionEnabled(true);
    } else {
      state.setText(stopRequested && alive ? "Stopping…" : agent.isViable() ? "Stopped" : "Failed");
      setInteractionEnabled(false);
      if (!alive) {
        reportStopped();
      }
    }

    if (agent instanceof AgentImpl clientAgent) {
      long startedAt = clientAgent.getStartedAt();
      long lastActivityAt = clientAgent.getLastActivityAt();
      started.setText(
          startedAt > 0 ? "Started: " + timestampFormat.format(Instant.ofEpochMilli(startedAt)) : "Started: —");
      idle.setText(
          lastActivityAt > 0
              ? "Idle: " + formatElapsed(System.currentTimeMillis() - lastActivityAt)
              : "Idle: —");
    } else {
      started.setText("Started: —");
      idle.setText("Idle: —");
    }
  }

  private void setInteractionEnabled(boolean enabled) {
    input.setDisable(!enabled);
    send.setDisable(!enabled);
    stop.setDisable(!enabled);
  }

  private String formatElapsed(long milliseconds) {
    long seconds = Math.max(0, milliseconds / 1000);
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long remainder = seconds % 60;
    return hours > 0
        ? String.format("%dh %02dm %02ds", hours, minutes, remainder)
        : String.format("%dm %02ds", minutes, remainder);
  }

  private void reportStopped() {
    if (!stoppedReported && agent != null) {
      stoppedReported = true;
      stoppedHandler.accept(agent);
    }
  }

  private void detach() {
    statusRefresh.stop();
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

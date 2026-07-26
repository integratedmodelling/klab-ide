package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * Compact debugger hosted below the behavior tree. It currently provides message tracing and
 * manual custom-message submission; execution controls are placeholders for the future debugger
 * protocol.
 */
public final class AgentDebuggerView extends BorderPane implements AutoCloseable {

  private static final int MAX_MESSAGES = 5000;
  private static final DateTimeFormatter MESSAGE_TIME =
      DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter START_TIME =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

  private enum Direction {
    IN("<-"),
    OUT("->");

    private final String symbol;

    Direction(String symbol) {
      this.symbol = symbol;
    }
  }

  private record MessageRow(
      String time, String direction, String type, String sender, String payload) {}

  private static final class DebugSession implements AutoCloseable {

    private final Agent agent;
    private final ObservableList<MessageRow> messages = FXCollections.observableArrayList();
    private AutoCloseable incoming;
    private AutoCloseable outgoing;

    private DebugSession(Agent agent) {
      this.agent = agent;
    }

    @Override
    public void close() {
      closeQuietly(incoming);
      closeQuietly(outgoing);
      incoming = null;
      outgoing = null;
    }

    private static void closeQuietly(AutoCloseable subscription) {
      if (subscription != null) {
        try {
          subscription.close();
        } catch (Exception ignored) {
          // Runtime-only listener teardown is intentionally idempotent.
        }
      }
    }
  }

  private final Map<String, DebugSession> sessions = new LinkedHashMap<>();
  private final TableView<MessageRow> messages = new TableView<>();
  private final TextField messageClass = new TextField("MESSAGE");
  private final TextField payload = new TextField();
  private final Button send = new Button();
  private final Label agentName = new Label();
  private final Label state = new Label("Stopped");
  private final Label started = new Label("Start: -");
  private final Label idle = new Label("Idle: -");
  private final Button stop = new Button();
  private final Timeline statusRefresh =
      new Timeline(
          new KeyFrame(Duration.ZERO, event -> refreshStatus()),
          new KeyFrame(Duration.seconds(1), event -> refreshStatus()));
  private final Consumer<Agent> stoppedHandler;
  private DebugSession focusedSession;

  public AgentDebuggerView(Consumer<Agent> stoppedHandler) {
    this.stoppedHandler = stoppedHandler == null ? ignored -> {} : stoppedHandler;
    getStyleClass().add("agent-debugger");
    setStyle("-fx-font-size: 10px;");
    setPadding(new Insets(3));
    setMinHeight(220);
    setPrefHeight(300);
    setTop(createControlBar());
    setCenter(createMessageTabs());
    setBottom(createStatusBar());
    setVisible(false);
    setManaged(false);
    statusRefresh.setCycleCount(Timeline.INDEFINITE);
  }

  /** Start retaining incoming and outgoing messages for a debug-launched agent. */
  public void registerAgent(Agent agent) {
    if (!(agent instanceof AgentImpl clientAgent)
        || agent.getUrn() == null
        || sessions.containsKey(agent.getUrn())) {
      return;
    }
    var session = new DebugSession(agent);
    session.incoming =
        clientAgent.addMessageListener(message -> append(session, Direction.IN, message));
    session.outgoing =
        clientAgent.addSentMessageListener(message -> append(session, Direction.OUT, message));
    sessions.put(agent.getUrn(), session);
  }

  /** Stop retaining messages for an agent and discard its debugging transcript. */
  public void unregisterAgent(Agent agent) {
    if (agent == null || agent.getUrn() == null) {
      return;
    }
    var removed = sessions.remove(agent.getUrn());
    if (removed != null) {
      removed.close();
    }
    if (focusedSession == removed) {
      focusAgent(null);
    }
  }

  /** Focus the debugger and message table on one registered agent. */
  public void focusAgent(Agent agent) {
    focusedSession = agent == null ? null : sessions.get(agent.getUrn());
    messages.setItems(
        focusedSession == null
            ? FXCollections.observableArrayList()
            : focusedSession.messages);
    boolean available = focusedSession != null;
    setVisible(available);
    setManaged(available);
    if (available) {
      statusRefresh.playFromStart();
      if (!messages.getItems().isEmpty()) {
        messages.scrollTo(messages.getItems().size() - 1);
      }
      Platform.runLater(payload::requestFocus);
    } else {
      statusRefresh.stop();
      refreshStatus();
    }
  }

  @Override
  public void close() {
    statusRefresh.stop();
    sessions.values().forEach(DebugSession::close);
    sessions.clear();
    focusedSession = null;
  }

  private HBox createControlBar() {
    var label = new Label("Debugger");
    label.getStyleClass().add(Styles.TEXT_BOLD);
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var resume = debugButton(Material2MZ.PLAY_ARROW, "Resume (not available yet)");
    var pause = debugButton(Material2MZ.PAUSE, "Pause (not available yet)");
    var step = debugButton(Material2MZ.SKIP_NEXT, "Step (not available yet)");
    var bar = new HBox(3, label, spacer, resume, pause, step);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(2, 3, 3, 3));
    return bar;
  }

  private Button debugButton(Ikon icon, String tooltip) {
    var button = new Button();
    button.setGraphic(new IconLabel(icon, 11, Color.GRAY));
    button.setTooltip(new Tooltip(tooltip));
    button.setDisable(true);
    button.setMinSize(22, 22);
    button.setPrefSize(22, 22);
    button.setMaxSize(22, 22);
    return button;
  }

  private VBox createMessageTabs() {
    messages.setPlaceholder(new Label("No agent messages"));
    messages.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    messages.setFixedCellSize(20);
    messages.getStyleClass().addAll(Styles.DENSE, Styles.SMALL);

    var timeColumn = column("Time", MessageRow::time, 72);
    var directionColumn = column("", MessageRow::direction, 25);
    var typeColumn = column("Type", MessageRow::type, 105);
    var senderColumn = column("Sender", MessageRow::sender, 95);
    var payloadColumn = column("Payload", MessageRow::payload, 160);
    messages
        .getColumns()
        .addAll(timeColumn, directionColumn, typeColumn, senderColumn, payloadColumn);

    messageClass.setPromptText("CONSTANT");
    messageClass.setPrefColumnCount(10);
    payload.setPromptText("Text payload");
    HBox.setHgrow(payload, Priority.ALWAYS);
    send.setGraphic(new IconLabel(Material2MZ.SEND, 11, Theme.FOREGROUND_COLOR));
    send.setTooltip(new Tooltip("Send custom message"));
    send.setMinSize(24, 24);
    send.setPrefSize(24, 24);
    var composer = new HBox(3, messageClass, payload, send);
    composer.setPadding(new Insets(3, 0, 0, 0));
    send.setOnAction(event -> sendMessage());
    payload.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER) {
            sendMessage();
            event.consume();
          }
        });

    var body = new VBox(messages, composer);
    VBox.setVgrow(messages, Priority.ALWAYS);
    var tab = new Tab("Messages", body);
    tab.setClosable(false);
    var tabs = new TabPane(tab);
    tabs.getStyleClass().addAll(Styles.DENSE, Styles.SMALL);
    VBox.setVgrow(tabs, Priority.ALWAYS);
    return new VBox(tabs);
  }

  private TableColumn<MessageRow, String> column(
      String title, java.util.function.Function<MessageRow, String> value, double width) {
    var column = new TableColumn<MessageRow, String>(title);
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    column.setPrefWidth(width);
    return column;
  }

  private HBox createStatusBar() {
    agentName.getStyleClass().add(Styles.TEXT_BOLD);
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    stop.setGraphic(new IconLabel(Material2MZ.STOP, 11, Color.DARKRED));
    stop.setTooltip(new Tooltip("Stop and release this agent"));
    stop.setMinSize(22, 22);
    stop.setPrefSize(22, 22);
    stop.setOnAction(event -> stopAgent());
    var bar = new HBox(5, agentName, state, spacer, started, idle, stop);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(3));
    bar.setStyle("-fx-background-color: -color-neutral-muted; -fx-font-size: 10px;");
    return bar;
  }

  private void append(DebugSession session, Direction direction, Message message) {
    Platform.runLater(
        () -> {
          var custom =
              message.getMessageType() == Message.MessageType.CustomAgentMessage
                  ? message.getPayload(RuntimeAgent.CustomMessage.class)
                  : null;
          String type =
              custom == null || custom.type() == null
                  ? String.valueOf(message.getMessageType())
                  : custom.type().getValue();
          Object value = custom == null ? message.getPayload(Object.class) : custom.payload();
          var row =
              new MessageRow(
                  MESSAGE_TIME.format(
                      Instant.ofEpochMilli(
                          message.getTimestamp() > 0
                              ? message.getTimestamp()
                              : System.currentTimeMillis())),
                  direction.symbol,
                  type,
                  message.getDispatchId() == null ? "" : message.getDispatchId(),
                  value == null ? "" : String.valueOf(value));
          if (session.messages.size() == MAX_MESSAGES) {
            session.messages.removeFirst();
          }
          session.messages.add(row);
          if (focusedSession == session) {
            messages.scrollTo(session.messages.size() - 1);
          }
        });
  }

  private void sendMessage() {
    if (focusedSession == null || !focusedSession.agent.isAlive()) {
      return;
    }
    String type = messageClass.getText() == null ? "" : messageClass.getText().trim();
    if (type.isBlank()) {
      type = "MESSAGE";
      messageClass.setText(type);
    }
    focusedSession.agent.tell(
        new RuntimeAgent.CustomMessage(
            Constant.create(type.toUpperCase()), payload.getText()));
    payload.clear();
  }

  private void stopAgent() {
    if (focusedSession == null) {
      return;
    }
    var agent = focusedSession.agent;
    stop.setDisable(true);
    state.setText("Stopping...");
    if (agent.stop()) {
      stoppedHandler.accept(agent);
    } else {
      refreshStatus();
    }
  }

  private void refreshStatus() {
    if (focusedSession == null) {
      agentName.setText("");
      state.setText("Stopped");
      started.setText("Start: -");
      idle.setText("Idle: -");
      stop.setDisable(true);
      send.setDisable(true);
      payload.setDisable(true);
      messageClass.setDisable(true);
      return;
    }
    var agent = focusedSession.agent;
    String name = agent.getName();
    agentName.setText(name == null || name.isBlank() ? agent.getUrn() : name);
    boolean alive = agent.isAlive();
    state.setText(alive ? "Running" : agent.isViable() ? "Stopped" : "Failed");
    stop.setDisable(!alive);
    send.setDisable(!alive);
    payload.setDisable(!alive);
    messageClass.setDisable(!alive);
    if (agent instanceof AgentImpl clientAgent) {
      started.setText(
          clientAgent.getStartedAt() > 0
              ? "Start: " + START_TIME.format(Instant.ofEpochMilli(clientAgent.getStartedAt()))
              : "Start: -");
      idle.setText(
          clientAgent.getLastActivityAt() > 0
              ? "Idle: "
                  + formatElapsed(System.currentTimeMillis() - clientAgent.getLastActivityAt())
              : "Idle: -");
    }
  }

  private String formatElapsed(long milliseconds) {
    long seconds = Math.max(0, milliseconds / 1000);
    long minutes = seconds / 60;
    return String.format("%d:%02d", minutes, seconds % 60);
  }
}

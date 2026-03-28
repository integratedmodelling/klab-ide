package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.NordDark;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.components.generic.Notebook;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone demo application for the {@link Notebook} component.
 *
 * <p>Demonstrates all public API methods: addCard, removeCard, focusCard, pinCard, unpinCard.
 * The left panel provides interactive controls; the right area holds the Notebook.
 */
public class NotebookTest extends Application {

  // IDs for the pre-built cards
  private static final String ID_WELCOME = "welcome";
  private static final String ID_CHART = "chart";
  private static final String ID_SETTINGS = "settings";
  private static final String ID_LOG = "log";

  private Notebook notebook;

  // Counter for dynamically added cards
  private final AtomicInteger dynamicCount = new AtomicInteger(0);

  // ---- entry point ----

  public static void main(String[] args) {
    launch(args);
  }

  // ---- Application lifecycle ----

  @Override
  public void start(Stage primaryStage) {
    Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());

    notebook = new Notebook();

    // Populate the notebook with four demo cards
    notebook.addCard(
        ID_WELCOME,
        Material2AL.HOME,
        "Welcome",
        "Getting started with Notebook",
        buildWelcomeCard());

    notebook.addCard(
        ID_CHART,
        Material2AL.BAR_CHART,
        "Sample Chart",
        "Placeholder visualisation",
        buildChartCard());

    notebook.addCard(
        ID_SETTINGS,
        Material2MZ.SETTINGS,
        "Settings",
        "Configuration options",
        buildSettingsCard());

    notebook.addCard(
        ID_LOG,
        Material2AL.DESCRIPTION,
        "Log Output",
        null, // no subtitle — demonstrates optional subtitle
        buildLogCard());

    // Lay out: control panel left, notebook fills the rest
    BorderPane root = new BorderPane();
    root.setLeft(buildControlPanel());
    root.setCenter(notebook);
    root.setStyle("-fx-background-color: #1e1e1e;");

    Scene scene = new Scene(root, 1100, 720);
    primaryStage.setTitle("Notebook Component Demo");
    primaryStage.setScene(scene);
    primaryStage.show();
  }

  // ---- control panel ----

  private VBox buildControlPanel() {
    VBox panel = new VBox(10);
    panel.setPadding(new Insets(16));
    panel.setPrefWidth(210);
    panel.setStyle("-fx-background-color: #252525;");

    Label title = new Label("Controls");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #eceff4;");

    // ---- focus section ----
    Label focusTitle = sectionLabel("Focus card");
    Button focusWelcome = actionBtn("Welcome", () -> notebook.focusCard(ID_WELCOME));
    Button focusChart   = actionBtn("Chart",   () -> notebook.focusCard(ID_CHART));
    Button focusSettings = actionBtn("Settings", () -> notebook.focusCard(ID_SETTINGS));
    Button focusLog     = actionBtn("Log",      () -> notebook.focusCard(ID_LOG));

    // ---- pin section ----
    Label pinTitle = sectionLabel("Pin / Unpin");
    Button pinWelcome   = actionBtn("Pin Welcome",   () -> notebook.pinCard(ID_WELCOME));
    Button unpinWelcome = actionBtn("Unpin Welcome", () -> notebook.unpinCard(ID_WELCOME));
    Button pinChart     = actionBtn("Pin Chart",     () -> notebook.pinCard(ID_CHART));
    Button unpinChart   = actionBtn("Unpin Chart",   () -> notebook.unpinCard(ID_CHART));

    // ---- remove section ----
    Label removeTitle = sectionLabel("Remove card");
    Button removeChart    = actionBtn("Remove Chart",    () -> notebook.removeCard(ID_CHART));
    Button removeSettings = actionBtn("Remove Settings", () -> notebook.removeCard(ID_SETTINGS));

    // ---- add dynamic card section ----
    Label addTitle = sectionLabel("Add dynamic card");
    TextField titleField = new TextField("New Card " + (dynamicCount.get() + 1));
    titleField.setPromptText("Card title");
    titleField.setStyle("-fx-background-color: #3b4252; -fx-text-fill: #eceff4;");

    Button addBtn = new Button("Add card");
    addBtn.setMaxWidth(Double.MAX_VALUE);
    addBtn.setOnAction(e -> {
      int n = dynamicCount.incrementAndGet();
      String id = "dynamic-" + n;
      String cardTitle = titleField.getText().isBlank() ? "Card " + n : titleField.getText();
      notebook.addCard(id, Material2AL.ADD_CIRCLE, cardTitle, "Dynamically added", buildDynamicCard(id, cardTitle));
      titleField.setText("New Card " + (n + 1));
    });

    Label hint = new Label("Tip: double-click a card header to collapse it.");
    hint.setWrapText(true);
    hint.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");

    panel.getChildren().addAll(
        title,
        new Separator(),
        focusTitle, focusWelcome, focusChart, focusSettings, focusLog,
        new Separator(),
        pinTitle, pinWelcome, unpinWelcome, pinChart, unpinChart,
        new Separator(),
        removeTitle, removeChart, removeSettings,
        new Separator(),
        addTitle, titleField, addBtn,
        new Separator(),
        hint
    );

    return panel;
  }

  // ---- card content builders ----

  private VBox buildWelcomeCard() {
    VBox box = new VBox(10);
    box.setPadding(new Insets(4));

    Label intro = new Label(
        "This demo showcases the Notebook component.\n\n"
        + "Each entry is a collapsible card with an icon, title,\n"
        + "and optional subtitle. Use the index pane on the right\n"
        + "to navigate, and the controls on the left to exercise\n"
        + "the full API.");
    intro.setWrapText(true);
    intro.setStyle("-fx-text-fill: #d0d0d0;");

    HBox badges = new HBox(8);
    for (String text : new String[]{"addCard", "removeCard", "focusCard", "pinCard"}) {
      Label badge = new Label(text);
      badge.setStyle(
          "-fx-background-color: #3b4252; -fx-text-fill: #88c0d0; -fx-padding: 3 7 3 7;"
          + " -fx-background-radius: 4; -fx-font-size: 11px;");
      badges.getChildren().add(badge);
    }

    box.getChildren().addAll(intro, badges);
    return box;
  }

  private VBox buildChartCard() {
    VBox box = new VBox(8);
    box.setPadding(new Insets(4));

    Label lbl = new Label("Bar chart placeholder");
    lbl.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

    // Simple bar-chart simulation with coloured rectangles
    HBox bars = new HBox(6);
    bars.setAlignment(Pos.BOTTOM_LEFT);
    double[] heights = {60, 120, 90, 150, 110, 80, 140};
    Color[] colors = {
      Color.web("#5e81ac"), Color.web("#81a1c1"), Color.web("#88c0d0"),
      Color.web("#8fbcbb"), Color.web("#a3be8c"), Color.web("#ebcb8b"), Color.web("#bf616a")
    };
    for (int i = 0; i < heights.length; i++) {
      Rectangle bar = new Rectangle(28, heights[i], colors[i]);
      bar.setArcWidth(3);
      bar.setArcHeight(3);
      bars.getChildren().add(bar);
    }

    box.getChildren().addAll(lbl, bars);
    return box;
  }

  private VBox buildSettingsCard() {
    VBox box = new VBox(10);
    box.setPadding(new Insets(4));

    for (String[] row : new String[][]{
        {"Enable notifications", "true"},
        {"Auto-save on close",   "true"},
        {"Show debug output",    "false"},
        {"Dark mode",            "true"}
    }) {
      CheckBox cb = new CheckBox(row[0]);
      cb.setSelected(Boolean.parseBoolean(row[1]));
      cb.setStyle("-fx-text-fill: #d0d0d0;");
      box.getChildren().add(cb);
    }

    Label sliderLabel = new Label("Refresh interval (s)");
    sliderLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

    Slider slider = new Slider(1, 60, 10);
    slider.setShowTickLabels(true);
    slider.setShowTickMarks(true);
    slider.setMajorTickUnit(15);
    slider.setMaxWidth(Double.MAX_VALUE);

    box.getChildren().addAll(new Separator(), sliderLabel, slider);
    return box;
  }

  private VBox buildLogCard() {
    VBox box = new VBox(6);
    box.setPadding(new Insets(4));

    TextArea log = new TextArea();
    log.setEditable(false);
    log.setPrefRowCount(6);
    log.setStyle(
        "-fx-control-inner-background: #2e3440; -fx-text-fill: #a3be8c;"
        + " -fx-font-family: monospace; -fx-font-size: 11px;");
    log.setText(
        "[INFO]  Notebook component initialised\n"
        + "[INFO]  4 cards loaded\n"
        + "[DEBUG] Index pane rendered with 4 entries\n"
        + "[INFO]  Ready\n");

    Button appendBtn = new Button("Append log line");
    appendBtn.setOnAction(e ->
        log.appendText("[INFO]  Log entry #" + (log.getParagraphs().size()) + "\n"));

    box.getChildren().addAll(log, appendBtn);
    VBox.setVgrow(log, Priority.ALWAYS);
    return box;
  }

  private VBox buildDynamicCard(String id, String title) {
    VBox box = new VBox(8);
    box.setPadding(new Insets(4));

    Label lbl = new Label("This card was added at runtime.\nID: " + id);
    lbl.setStyle("-fx-text-fill: #d0d0d0;");
    lbl.setWrapText(true);

    Button removeBtn = new Button("Remove this card");
    removeBtn.setOnAction(e -> notebook.removeCard(id));

    Button pinBtn = new Button("Pin this card");
    pinBtn.setOnAction(e -> notebook.pinCard(id));

    HBox btnRow = new HBox(8, removeBtn, pinBtn);

    box.getChildren().addAll(lbl, btnRow);
    return box;
  }

  // ---- helpers ----

  private static Label sectionLabel(String text) {
    Label lbl = new Label(text);
    lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #81a1c1;");
    return lbl;
  }

  private static Button actionBtn(String label, Runnable action) {
    Button btn = new Button(label);
    btn.setMaxWidth(Double.MAX_VALUE);
    btn.setOnAction(e -> action.run());
    return btn;
  }
}

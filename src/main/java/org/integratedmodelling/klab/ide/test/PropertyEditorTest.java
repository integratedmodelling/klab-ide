package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.PrimerLight;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.ide.components.generic.PropertyEditor;

/**
 * Showcase application for the {@link PropertyEditor} component.
 *
 * <p>Three tabs demonstrate the main usage modes:
 *
 * <ol>
 *   <li><b>Known types only</b> – a read-only-key editor where the first column shows human-
 *       readable labels for known properties and a ComboBox lets the user switch between those that
 *       are not yet in the map.
 *   <li><b>Mixed properties</b> – a mix of known and unknown keys; known ones render as labels with
 *       a ComboBox, unknown ones as plain text.
 *   <li><b>With addition</b> – all of the above plus the ability to add new rows freely and to edit
 *       any key directly (the "add" icon is shown in the top-right corner).
 * </ol>
 *
 * <p>Each tab has an "Inspect" button that dumps the current contents of the underlying map to a
 * small text area so you can verify that edits are reflected correctly.
 */
public class PropertyEditorTest extends Application {

  // ── Shared known-type descriptors ──────────────────────────────────────────

  /**
   * A small catalogue of well-known property slots. Each entry maps a (label, actualKey) pair to
   * the expected value type. The editor uses this to render labels and restrict the ComboBox to
   * properties not yet present in the map.
   */
  private static Map<Pair<String, String>, Class<?>> knownTypes() {
    Map<Pair<String, String>, Class<?>> m = new LinkedHashMap<>();
    m.put(Pair.of("Host name", "host"), String.class);
    m.put(Pair.of("Port number", "port"), Integer.class);
    m.put(Pair.of("Username", "user"), String.class);
    m.put(Pair.of("Password", "password"), String.class);
    m.put(Pair.of("Database", "database"), String.class);
    m.put(Pair.of("Max connections", "maxConnections"), Integer.class);
    return m;
  }

  // ── Application entry point ─────────────────────────────────────────────────

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    TabPane tabs = new TabPane();
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().addAll(
        buildKnownOnlyTab(),
        buildMixedTab(),
        buildAdditionTab());

    Scene scene = new Scene(tabs, 700, 480);
    stage.setTitle("PropertyEditor – showcase");
    stage.setScene(scene);
    stage.show();
  }

  // ── Tab 1: known-type properties only ──────────────────────────────────────

  private Tab buildKnownOnlyTab() {
    // Only a subset of known keys is pre-populated; the rest are available via the ComboBox
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("host", "localhost");
    props.put("port", "5432");
    props.put("database", "mydb");

    PropertyEditor editor = new PropertyEditor(props, knownTypes());
    VBox.setVgrow(editor, Priority.ALWAYS);

    TextArea dump = buildDumpArea();
    Button inspect = new Button("Inspect properties");
    inspect.setOnAction(e -> dumpProperties(editor.getTargetProperties(), dump));

    return buildTab("Known types", editor, inspect, dump,
        "Known properties are shown with a human-readable label.\n"
        + "The first column ComboBox lets you switch to any known property not yet in the map.\n"
        + "Edit a value in the second column and press Enter to commit it.");
  }

  // ── Tab 2: mix of known and unknown properties ──────────────────────────────

  private Tab buildMixedTab() {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("host", "db.example.com");
    props.put("port", "3306");
    // "timeout" and "charset" are not in knownTypes → shown as plain text labels
    props.put("timeout", "30");
    props.put("charset", "UTF-8");

    PropertyEditor editor = new PropertyEditor(props, knownTypes());
    VBox.setVgrow(editor, Priority.ALWAYS);

    TextArea dump = buildDumpArea();
    Button inspect = new Button("Inspect properties");
    inspect.setOnAction(e -> dumpProperties(editor.getTargetProperties(), dump));

    return buildTab("Mixed", editor, inspect, dump,
        "Known properties (host, port) render with a label and a ComboBox.\n"
        + "Unknown properties (timeout, charset) are shown as plain text.\n"
        + "Values in the second column are editable – press Enter to commit.");
  }

  // ── Tab 3: with free-form addition enabled ──────────────────────────────────

  private Tab buildAdditionTab() {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("user", "admin");
    props.put("password", "secret");
    // Start with an unknown property too
    props.put("sslMode", "require");

    PropertyEditor editor = new PropertyEditor(props, knownTypes(), true);
    VBox.setVgrow(editor, Priority.ALWAYS);

    TextArea dump = buildDumpArea();
    Button inspect = new Button("Inspect properties");
    inspect.setOnAction(e -> dumpProperties(editor.getTargetProperties(), dump));

    return buildTab("With addition", editor, inspect, dump,
        "Addition mode: the '+' icon (top-right of the table) appends a new blank row.\n"
        + "Known-property ComboBoxes are editable, so you can also type a custom key.\n"
        + "Unknown-property key fields are editable text boxes – press Enter to rename the key.");
  }

  // ── Shared helpers ──────────────────────────────────────────────────────────

  private Tab buildTab(
      String title,
      PropertyEditor editor,
      Button inspectButton,
      TextArea dump,
      String description) {

    Label descLabel = new Label(description);
    descLabel.setWrapText(true);
    descLabel.getStyleClass().add(atlantafx.base.theme.Styles.TEXT_MUTED);

    HBox toolbar = new HBox(inspectButton);
    toolbar.setAlignment(Pos.CENTER_RIGHT);
    toolbar.setPadding(new Insets(4, 0, 4, 0));

    VBox content = new VBox(8,
        descLabel,
        new Separator(),
        editor,
        new Separator(),
        toolbar,
        dump);
    content.setPadding(new Insets(12));
    VBox.setVgrow(editor, Priority.ALWAYS);

    Tab tab = new Tab(title, content);
    return tab;
  }

  private TextArea buildDumpArea() {
    TextArea area = new TextArea();
    area.setEditable(false);
    area.setPrefRowCount(5);
    area.setPromptText("Click 'Inspect properties' to see the current map contents…");
    area.getStyleClass().add(atlantafx.base.theme.Styles.TEXT_SMALL);
    return area;
  }

  private void dumpProperties(Map<String, Object> props, TextArea target) {
    StringBuilder sb = new StringBuilder();
    props.forEach((k, v) -> sb.append(k).append(" = ").append(v).append('\n'));
    target.setText(sb.isEmpty() ? "(empty)" : sb.toString());
  }

  // ── Main ────────────────────────────────────────────────────────────────────

  public static void main(String[] args) {
    launch(args);
  }
}

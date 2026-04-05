package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.components.generic.LogViewer;
import org.integratedmodelling.klab.ide.components.generic.LogViewer.Column;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;

/**
 * Showcase application for the {@link LogViewer} component.
 *
 * <p>The window consists of:
 *
 * <ul>
 *   <li>A toolbar with a file-path field, a Browse button, and per-column visibility toggles.
 *   <li>The {@link LogViewer} filling the remaining space.
 *   <li>A status bar that shows the watched file path and the current entry count.
 * </ul>
 *
 * <p>By default the viewer opens the k.LAB runtime log at {@code
 * ~/.klab/services/runtime/logs/runtime.log} (if it exists).
 *
 * <p>Run via {@code main()} – no extra arguments required.
 */
public class LogViewerTest extends Application {

  /** Default log file – adjust if your runtime log lives elsewhere. */
  private static final String DEFAULT_LOG =
      System.getProperty("user.home") + "/.klab/services/runtime/logs/runtime.log";

  private LogViewer logViewer;
  private Label statusLabel;
  private TextField filePathField;

  @Override
  public void start(Stage stage) {
    // Apply AtlantaFX dark theme so the log level colours look their best
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    // ── File toolbar ──────────────────────────────────────────────────────────

    filePathField = new TextField(DEFAULT_LOG);
    filePathField.setPromptText("Path to log file…");
    HBox.setHgrow(filePathField, Priority.ALWAYS);
    filePathField.setOnAction(e -> openFile(Paths.get(filePathField.getText().trim())));

    Button browseButton = new Button("Browse…");
    browseButton.setOnAction(
        e -> {
          FileChooser chooser = new FileChooser();
          chooser.setTitle("Open Log File");
          chooser
              .getExtensionFilters()
              .addAll(
                  new FileChooser.ExtensionFilter("Log files", "*.log", "*.txt"),
                  new FileChooser.ExtensionFilter("All files", "*.*"));

          // Pre-navigate to the directory of the current file
          try {
            Path current = Paths.get(filePathField.getText().trim());
            if (current.getParent() != null) {
              chooser.setInitialDirectory(current.getParent().toFile());
            }
          } catch (Exception ignored) {
          }

          File chosen = chooser.showOpenDialog(stage);
          if (chosen != null) {
            filePathField.setText(chosen.getAbsolutePath());
            openFile(chosen.toPath());
          }
        });

    Button clearButton = new Button("Clear");
    clearButton.setOnAction(e -> logViewer.clear());

    HBox fileBar = new HBox(6, new Label("File:"), filePathField, browseButton, clearButton);
    fileBar.setAlignment(Pos.CENTER_LEFT);
    fileBar.setPadding(new Insets(6, 8, 6, 8));

    // ── Column-visibility toolbar ─────────────────────────────────────────────

    Label colLabel = new Label("Columns:");
    colLabel.getStyleClass().add(atlantafx.base.theme.Styles.TEXT_BOLD);

    HBox columnBar = new HBox(8, colLabel);
    columnBar.setAlignment(Pos.CENTER_LEFT);
    columnBar.setPadding(new Insets(2, 8, 6, 8));

    // One checkbox per column; all visible initially
    Set<Column> visible = EnumSet.of(Column.TIME, Column.LEVEL, Column.MESSAGE);
    for (Column col : Column.values()) {
      CheckBox cb = new CheckBox(col.getLabel());
      cb.setSelected(true);
      cb.selectedProperty()
          .addListener(
              (obs, was, isNow) -> {
                if (isNow) {
                  visible.add(col);
                } else {
                  visible.remove(col);
                }
                // EnumSet.copyOf requires a non-empty collection; fall back to noneOf when all
                // hidden
                logViewer.setVisibleColumns(
                    visible.isEmpty() ? EnumSet.noneOf(Column.class) : EnumSet.copyOf(visible));
              });
      columnBar.getChildren().add(cb);
    }

    // ── LogViewer ─────────────────────────────────────────────────────────────

    logViewer = new LogViewer(Paths.get(DEFAULT_LOG));
    VBox.setVgrow(logViewer, Priority.ALWAYS);

    // Update status bar whenever the entry list changes
    logViewer
        .getEntries()
        .addListener(
            (javafx.collections.ListChangeListener<LogViewer.LogEntry>) change -> updateStatus());

    // ── Status bar ────────────────────────────────────────────────────────────

    statusLabel = new Label();
    updateStatus();
    statusLabel.setPadding(new Insets(3, 8, 3, 8));
    statusLabel
        .getStyleClass()
        .addAll(atlantafx.base.theme.Styles.TEXT_SMALL, atlantafx.base.theme.Styles.TEXT_MUTED);

    HBox statusBar = new HBox(statusLabel);
    statusBar.setStyle(
        "-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; "
            + "-fx-border-width: 1 0 0 0;");

    // ── Layout ────────────────────────────────────────────────────────────────

    VBox root = new VBox(fileBar, new Separator(), columnBar, logViewer, statusBar);
    VBox.setVgrow(logViewer, Priority.ALWAYS);

    Scene scene = new Scene(root, 1200, 700);
    stage.setTitle("LogViewer – " + Paths.get(DEFAULT_LOG).getFileName());
    stage.setScene(scene);
    stage.setOnCloseRequest(e -> logViewer.shutdown());
    stage.show();
  }

  /** Opens a new file in the viewer and updates the title / status. */
  private void openFile(Path path) {
    logViewer.setFile(path);
    filePathField.setText(path.toString());
    ((Stage) filePathField.getScene().getWindow()).setTitle("LogViewer – " + path.getFileName());
    updateStatus();
  }

  private void updateStatus() {
    Path p = Paths.get(filePathField == null ? DEFAULT_LOG : filePathField.getText().trim());
    int count = logViewer == null ? 0 : logViewer.getEntries().size();
    statusLabel.setText("Watching: " + p + "   |   Entries: " + count);
  }

  public static void main(String[] args) {
    launch(args);
  }
}

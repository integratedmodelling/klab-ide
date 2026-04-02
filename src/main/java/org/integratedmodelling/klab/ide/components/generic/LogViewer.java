package org.integratedmodelling.klab.ide.components.generic;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * A JavaFX component that tails a log file, parsing its contents and displaying them as a
 * {@link TableView}. Supports the Spring Boot / Logback default log format:
 *
 * <pre>
 * 2025-04-10T07:54:45.493+02:00  INFO 44644 --- [main] o.i.k.s.MyClass : message text
 * </pre>
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Loads existing file content on construction.
 *   <li>Watches the file for changes via {@link WatchService} and appends new entries live.
 *   <li>Row background colour reflects the log level (TRACE / DEBUG / INFO / WARN / ERROR / FATAL).
 *   <li>Automatically scrolls to the latest entry unless the user has scrolled upward; auto-scroll
 *       resumes once the user scrolls back to the bottom.
 *   <li>Configurable column visibility via {@link #setVisibleColumns(Set)} or via a right-click
 *       context menu on any column header.
 *   <li>Handles multi-line log messages (continuation lines are appended to the previous entry).
 *   <li>Detects file rotation / truncation and reloads from the beginning.
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>
 * LogViewer viewer = new LogViewer(Paths.get("/path/to/app.log"));
 * viewer.setVisibleColumns(EnumSet.of(
 *     LogViewer.Column.TIME,
 *     LogViewer.Column.LEVEL,
 *     LogViewer.Column.LOGGER,
 *     LogViewer.Column.MESSAGE));
 * viewer.setPrefHeight(600);
 * </pre>
 *
 * <p>Call {@link #shutdown()} when the component is no longer needed to stop the background watcher
 * thread.
 */
public class LogViewer extends VBox {

  // ── Log pattern ──────────────────────────────────────────────────────────────
  //
  // Matches the Spring Boot / Logback default pattern, e.g.:
  //   2025-04-10T07:54:45.493+02:00  INFO 44644 --- [main] o.i.k.s.MyClass   : message
  //
  private static final Pattern LOG_PATTERN =
      Pattern.compile(
          "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+[+-]\\d{2}:\\d{2})\\s+"
              + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
              + "(\\d+)\\s+---\\s+\\[([^\\]]+)]\\s+(\\S+)\\s+:\\s+(.*)$");

  // ── Row CSS – uses AtlantaFX looked-up color variables so rows adapt to any theme ──
  private static final String STYLE_TRACE =
      "-fx-background-color: -color-bg-subtle;";
  private static final String STYLE_DEBUG =
      "-fx-background-color: -color-accent-subtle;";
  // INFO rows use the theme default (no override)
  private static final String STYLE_WARN =
      "-fx-background-color: -color-warning-subtle;";
  private static final String STYLE_ERROR =
      "-fx-background-color: -color-danger-subtle;";
  private static final String STYLE_FATAL =
      "-fx-background-color: -color-danger-muted;";

  // ── Public API types ──────────────────────────────────────────────────────────

  /**
   * The columns available in the viewer. Pass a {@link Set} of these values to
   * {@link #setVisibleColumns(Set)} to control which columns are displayed.
   */
  public enum Column {
    TIME("Time", 210),
    LEVEL("Level", 60),
    PID("PID", 60),
    THREAD("Thread", 150),
    LOGGER("Logger", 240),
    MESSAGE("Message", -1); // -1 → fills the remaining space

    private final String label;
    private final int prefWidth;

    Column(String label, int prefWidth) {
      this.label = label;
      this.prefWidth = prefWidth;
    }

    public String getLabel() {
      return label;
    }

    public int getPrefWidth() {
      return prefWidth;
    }
  }

  /**
   * A single parsed log entry. All fields are exposed as JavaFX {@link SimpleStringProperty}
   * instances for direct use in table cell value factories.
   */
  public static class LogEntry {
    private final SimpleStringProperty time;
    private final SimpleStringProperty level;
    private final SimpleStringProperty pid;
    private final SimpleStringProperty thread;
    private final SimpleStringProperty logger;
    private final SimpleStringProperty message;

    public LogEntry(
        String time,
        String level,
        String pid,
        String thread,
        String logger,
        String message) {
      this.time = new SimpleStringProperty(time);
      this.level = new SimpleStringProperty(level);
      this.pid = new SimpleStringProperty(pid);
      this.thread = new SimpleStringProperty(thread);
      this.logger = new SimpleStringProperty(logger);
      this.message = new SimpleStringProperty(message);
    }

    public SimpleStringProperty timeProperty() { return time; }
    public SimpleStringProperty levelProperty() { return level; }
    public SimpleStringProperty pidProperty() { return pid; }
    public SimpleStringProperty threadProperty() { return thread; }
    public SimpleStringProperty loggerProperty() { return logger; }
    public SimpleStringProperty messageProperty() { return message; }

    public String getTime() { return time.get(); }
    public String getLevel() { return level.get(); }
    public String getPid() { return pid.get(); }
    public String getThread() { return thread.get(); }
    public String getLogger() { return logger.get(); }
    public String getMessage() { return message.get(); }

    /** Appends a continuation line to this entry's message (for multi-line log events). */
    void appendMessage(String continuation) {
      message.set(message.get() + "\n" + continuation);
    }
  }

  // ── Internal state ────────────────────────────────────────────────────────────

  private Path logFile;
  private final ObservableList<LogEntry> entries = FXCollections.observableArrayList();
  private final TableView<LogEntry> tableView = new TableView<>(entries);
  private final Map<Column, TableColumn<LogEntry, String>> columnMap = new LinkedHashMap<>();

  /**
   * The most recently parsed entry on the watcher thread; used to detect continuation lines.
   * Also used (and reset) on the FX thread during initial load.
   */
  private LogEntry lastParsedEntry = null;

  /**
   * Byte offset of the start of the next unread line in the file. Points to just after the last
   * '\n' character successfully processed. Only written on the watcher thread after initial load.
   */
  private volatile long lastReadPosition = 0;

  /**
   * Whether the view should auto-scroll to the bottom when new entries arrive.
   * Starts {@code true}; set to {@code false} when the user scrolls up; restored to {@code true}
   * when the user scrolls back to the bottom.
   */
  private volatile boolean autoScroll = true;

  private ExecutorService watcherService;

  // ── Constructors ──────────────────────────────────────────────────────────────

  /**
   * Creates a new {@code LogViewer} for the specified file, showing all columns.
   *
   * @param logFile path to the log file to tail (need not exist yet)
   */
  public LogViewer(Path logFile) {
    this(logFile, EnumSet.allOf(Column.class));
  }

  /**
   * Creates a new {@code LogViewer} for the specified file with a custom initial column set.
   *
   * @param logFile        path to the log file to tail (need not exist yet)
   * @param visibleColumns the columns to display on construction
   */
  public LogViewer(Path logFile, Set<Column> visibleColumns) {
    this.logFile = logFile;

    configureTableView();
    buildColumns();
    setVisibleColumns(visibleColumns);

    // Attach scroll detection once the skin (and thus the ScrollBar nodes) exist
    tableView.skinProperty().addListener((obs, oldSkin, newSkin) -> {
      if (newSkin != null) setupScrollDetection();
    });

    VBox.setVgrow(tableView, Priority.ALWAYS);
    getChildren().add(tableView);

    loadExistingContent();
    startWatching();
  }

  // ── Public API ────────────────────────────────────────────────────────────────

  /**
   * Changes which columns are visible. May be called at any time on the FX thread.
   *
   * @param visibleColumns the set of columns to display
   */
  public void setVisibleColumns(Set<Column> visibleColumns) {
    tableView.getColumns().clear();
    for (Column col : Column.values()) {
      if (visibleColumns.contains(col)) {
        tableView.getColumns().add(columnMap.get(col));
      }
    }
  }

  /**
   * Returns the live observable list of log entries currently displayed. Intended for testing or
   * external filtering.
   */
  public ObservableList<LogEntry> getEntries() {
    return entries;
  }

  /**
   * Clears all displayed entries. Must be called on the FX thread (or via
   * {@link Platform#runLater}).
   */
  public void clear() {
    entries.clear();
    lastParsedEntry = null;
  }

  /**
   * Replaces the watched file and restarts tailing from the beginning of the new file.
   * Must be called on the FX thread.
   *
   * @param newFile path to the new log file
   */
  public void setFile(Path newFile) {
    shutdown();
    this.logFile = newFile;
    lastReadPosition = 0;
    lastParsedEntry = null;
    autoScroll = true;
    entries.clear();
    loadExistingContent();
    startWatching();
  }

  /**
   * Stops the background file-watcher thread. Call this when the component is removed from the
   * scene to avoid resource leaks.
   */
  public void shutdown() {
    if (watcherService != null) {
      watcherService.shutdownNow();
      watcherService = null;
    }
  }

  // ── TableView setup ───────────────────────────────────────────────────────────

  private void configureTableView() {
    tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    tableView.setPlaceholder(new Label("No log entries"));
    tableView.getStyleClass().add("log-viewer");

    // Colour rows by log level
    tableView.setRowFactory(tv -> new TableRow<>() {
      @Override
      protected void updateItem(LogEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        setStyle(empty || entry == null ? "" : styleForLevel(entry.getLevel()));
      }
    });
  }

  private void buildColumns() {
    for (Column col : Column.values()) {
      TableColumn<LogEntry, String> tc = new TableColumn<>(col.getLabel());
      tc.setCellValueFactory(data -> {
        LogEntry e = data.getValue();
        return switch (col) {
          case TIME    -> e.timeProperty();
          case LEVEL   -> e.levelProperty();
          case PID     -> e.pidProperty();
          case THREAD  -> e.threadProperty();
          case LOGGER  -> e.loggerProperty();
          case MESSAGE -> e.messageProperty();
        };
      });
      if (col.getPrefWidth() > 0) {
        tc.setPrefWidth(col.getPrefWidth());
        tc.setMinWidth(40);
      }
      columnMap.put(col, tc);
    }

    // Attach a right-click context menu to every column header
    for (Column col : Column.values()) {
      columnMap.get(col).setContextMenu(buildHeaderContextMenu());
    }
  }

  private ContextMenu buildHeaderContextMenu() {
    ContextMenu menu = new ContextMenu();
    for (Column col : Column.values()) {
      CheckMenuItem item = new CheckMenuItem(col.getLabel());
      item.setSelected(tableView.getColumns().contains(columnMap.get(col)));
      item.selectedProperty().addListener((obs, was, isNow) -> {
        TableColumn<LogEntry, String> tc = columnMap.get(col);
        if (isNow) {
          if (!tableView.getColumns().contains(tc)) {
            // Insert at the ordinal position among currently visible columns
            int insertAt = 0;
            for (Column c : Column.values()) {
              if (c == col) break;
              if (tableView.getColumns().contains(columnMap.get(c))) insertAt++;
            }
            tableView.getColumns().add(insertAt, tc);
          }
        } else {
          tableView.getColumns().remove(tc);
        }
        // Re-sync check states across all header menus
        refreshHeaderMenus();
      });
      menu.getItems().add(item);
    }
    return menu;
  }

  /** Replaces header context menus so their checked states match current column visibility. */
  private void refreshHeaderMenus() {
    for (Column col : Column.values()) {
      columnMap.get(col).setContextMenu(buildHeaderContextMenu());
    }
  }

  // ── Scroll detection ──────────────────────────────────────────────────────────

  private void setupScrollDetection() {
    tableView.lookupAll(".scroll-bar").forEach(node -> {
      if (node instanceof ScrollBar sb
          && sb.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
        sb.valueProperty().addListener((obs, oldVal, newVal) -> {
          double max = sb.getMax();
          double val = newVal.doubleValue();
          if (max <= sb.getMin()) {
            autoScroll = true;                       // nothing to scroll
          } else if (val < oldVal.doubleValue() - 1e-9) {
            autoScroll = false;                      // user scrolled up
          } else if (Math.abs(val - max) < 1e-9) {
            autoScroll = true;                       // user reached the bottom again
          }
        });
      }
    });
  }

  // ── Initial file load ─────────────────────────────────────────────────────────

  private void loadExistingContent() {
    if (!Files.exists(logFile)) return;
    try {
      byte[] bytes = Files.readAllBytes(logFile);
      if (bytes.length == 0) return;

      // Find where the last complete line ends so lastReadPosition stays consistent
      int lastNewline = bytes.length - 1;
      while (lastNewline >= 0 && bytes[lastNewline] != '\n') lastNewline--;
      lastReadPosition = lastNewline + 1; // byte offset just after the last '\n'

      String content = new String(bytes, 0, (int) lastReadPosition, StandardCharsets.UTF_8);
      List<LogEntry> batch = parseLines(content.split("\r?\n", -1), true);

      Platform.runLater(() -> {
        entries.addAll(batch);
        scrollToBottom();
      });
    } catch (IOException e) {
      // File unreadable – will retry once the watcher fires
    }
  }

  // ── File watching ─────────────────────────────────────────────────────────────

  private void startWatching() {
    watcherService = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "LogViewer[" + logFile.getFileName() + "]");
      t.setDaemon(true);
      return t;
    });
    watcherService.submit(this::watchLoop);
  }

  private void watchLoop() {
    Path parent = logFile.getParent();
    if (parent == null) parent = Path.of(".");

    try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
      parent.register(watcher,
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_MODIFY);

      while (!Thread.currentThread().isInterrupted()) {
        WatchKey key;
        try {
          key = watcher.poll(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
        if (key == null) continue;

        for (WatchEvent<?> event : key.pollEvents()) {
          if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
          @SuppressWarnings("unchecked")
          Path changed = ((WatchEvent<Path>) event).context();
          if (logFile.getFileName().equals(changed)) {
            readNewContent();
          }
        }
        key.reset();
      }
    } catch (IOException e) {
      // Watcher setup failed – silently stop tailing
    }
  }

  private void readNewContent() {
    try {
      long currentSize = Files.size(logFile);

      if (currentSize < lastReadPosition) {
        // File was truncated or rotated – reload from the beginning
        lastReadPosition = 0;
        lastParsedEntry = null;
        Platform.runLater(entries::clear);
      }
      if (currentSize == lastReadPosition) return;

      // Read only the new bytes appended since last position
      int newByteCount = (int) (currentSize - lastReadPosition);
      byte[] buf = new byte[newByteCount];
      try (FileInputStream fis = new FileInputStream(logFile.toFile())) {
        long remaining = lastReadPosition;
        while (remaining > 0) {
          long skipped = fis.skip(remaining);
          if (skipped <= 0) break;
          remaining -= skipped;
        }
        int totalRead = 0;
        while (totalRead < buf.length) {
          int n = fis.read(buf, totalRead, buf.length - totalRead);
          if (n == -1) break;
          totalRead += n;
        }
        buf = Arrays.copyOf(buf, totalRead);
      }

      // Process only up to (and including) the last '\n' so we never parse partial lines
      int lastNewlineInBuf = buf.length - 1;
      while (lastNewlineInBuf >= 0 && buf[lastNewlineInBuf] != '\n') lastNewlineInBuf--;
      if (lastNewlineInBuf < 0) return; // No complete line yet

      lastReadPosition += lastNewlineInBuf + 1;

      String content = new String(buf, 0, lastNewlineInBuf + 1, StandardCharsets.UTF_8);
      String[] lines = content.split("\r?\n", -1);
      List<LogEntry> batch = parseLines(lines, false);
      if (batch.isEmpty()) return;

      boolean shouldScroll = autoScroll;
      Platform.runLater(() -> {
        entries.addAll(batch);
        if (shouldScroll) scrollToBottom();
      });

    } catch (IOException e) {
      // Transient read error – will retry on the next modification event
    }
  }

  // ── Log parsing ───────────────────────────────────────────────────────────────

  /**
   * Parses an array of raw text lines into {@link LogEntry} objects. Continuation lines (lines
   * that do not match the log pattern) are appended to the most recently parsed entry's message.
   *
   * @param lines        lines to parse
   * @param resetContext {@code true} to clear the continuation-line tracking first (initial load)
   * @return list of newly created entries (continuation lines do not produce new entries)
   */
  private List<LogEntry> parseLines(String[] lines, boolean resetContext) {
    if (resetContext) lastParsedEntry = null;
    List<LogEntry> batch = new ArrayList<>();
    for (String line : lines) {
      if (line.isBlank()) continue;
      Matcher m = LOG_PATTERN.matcher(line);
      if (m.matches()) {
        LogEntry entry = new LogEntry(
            m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6));
        batch.add(entry);
        lastParsedEntry = entry;
      } else if (lastParsedEntry != null) {
        // Continuation / stack-trace line – fold into the previous entry's message
        lastParsedEntry.appendMessage(line);
      }
    }
    return batch;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  private void scrollToBottom() {
    if (!entries.isEmpty()) {
      tableView.scrollTo(entries.size() - 1);
    }
  }

  private static String styleForLevel(String level) {
    return switch (level) {
      case "TRACE" -> STYLE_TRACE;
      case "DEBUG" -> STYLE_DEBUG;
      case "WARN"  -> STYLE_WARN;
      case "ERROR" -> STYLE_ERROR;
      case "FATAL" -> STYLE_FATAL;
      default      -> ""; // INFO – use the theme default
    };
  }
}

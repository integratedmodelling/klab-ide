package org.integratedmodelling.klab.ide.components.generic;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * A JavaFX component that tails a log file, parsing its contents and displaying them as a {@link
 * TableView}. Supports the k.LAB / Logback log format:
 *
 * <pre>
 * [2026-04-05 11:51:31.384] - 11360 INFO  [main] --- o.i.k.s.MyClass: message text
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
  // Matches the k.LAB / Logback pattern, e.g.:
  //   [2026-04-05 11:51:31.384] - 11360 INFO  [main] --- o.i.k.s.MyClass: message
  //
  // Capture groups: 1=timestamp, 2=pid, 3=level, 4=thread, 5=logger, 6=message
  //
  private static final Pattern LOG_PATTERN =
      Pattern.compile(
          "^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+)\\]\\s+-\\s+(\\d+)\\s+"
              + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
              + "\\[([^\\]]+)\\]\\s+---\\s+(\\S+):\\s+(.*)$");

  // ── Row CSS – uses AtlantaFX looked-up color variables so rows adapt to any theme ──
  private static final String STYLE_TRACE = "-fx-background-color: -color-bg-subtle;";
  private static final String STYLE_DEBUG = "-fx-background-color: -color-accent-subtle;";
  // INFO rows use the theme default (no override)
  private static final String STYLE_WARN = "-fx-background-color: -color-warning-subtle;";
  private static final String STYLE_ERROR = "-fx-background-color: -color-danger-subtle;";
  private static final String STYLE_FATAL = "-fx-background-color: -color-danger-muted;";

  // ── Public API types ──────────────────────────────────────────────────────────

  /**
   * The columns available in the viewer. Pass a {@link Set} of these values to {@link
   * #setVisibleColumns(Set)} to control which columns are displayed.
   */
  public enum Column {
    TIME("Time", 180),
    LEVEL("Level", 80),
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
        String time, String level, String pid, String thread, String logger, String message) {
      this.time = new SimpleStringProperty(time);
      this.level = new SimpleStringProperty(level);
      this.pid = new SimpleStringProperty(pid);
      this.thread = new SimpleStringProperty(thread);
      this.logger = new SimpleStringProperty(logger);
      this.message = new SimpleStringProperty(message);
    }

    public SimpleStringProperty timeProperty() {
      return time;
    }

    public SimpleStringProperty levelProperty() {
      return level;
    }

    public SimpleStringProperty pidProperty() {
      return pid;
    }

    public SimpleStringProperty threadProperty() {
      return thread;
    }

    public SimpleStringProperty loggerProperty() {
      return logger;
    }

    public SimpleStringProperty messageProperty() {
      return message;
    }

    public String getTime() {
      return time.get();
    }

    public String getLevel() {
      return level.get();
    }

    public String getPid() {
      return pid.get();
    }

    public String getThread() {
      return thread.get();
    }

    public String getLogger() {
      return logger.get();
    }

    public String getMessage() {
      return message.get();
    }

    /** Appends a continuation line to this entry's message (for multi-line log events). */
    void appendMessage(String continuation) {
      message.set(message.get() + "\n" + continuation);
    }
  }

  // ── Internal state ────────────────────────────────────────────────────────────

  private Path logFile;
  private LogSource remoteSource;
  private volatile long sourceGeneration;
  private volatile LocalTail localTail;
  private final Map<String, LogEntry> remoteEntries = new LinkedHashMap<>();
  private static final int MAX_REMOTE_ROWS = 5000;
  private final ObservableList<LogEntry> entries = FXCollections.observableArrayList();
  private final TableView<LogEntry> tableView = new TableView<>(entries);
  private final Map<Column, TableColumn<LogEntry, String>> columnMap = new LinkedHashMap<>();

  /**
   * Last displayed entry; parsing and property mutations are confined to the FX thread.
   */
  private LogEntry lastParsedEntry = null;

  /**
   * Whether the view should auto-scroll to the bottom when new entries arrive. Starts {@code true};
   * set to {@code false} when the user scrolls up; restored to {@code true} when the user scrolls
   * back to the bottom.
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
   * @param logFile path to the log file to tail (need not exist yet)
   * @param visibleColumns the columns to display on construction
   */
  public LogViewer(Path logFile, Set<Column> visibleColumns) {
    this(logFile, visibleColumns, null);
  }

  /** Display a bounded remote diagnostic source; existing Path constructors retain file mode. */
  public LogViewer(LogSource source) {
    this(null, EnumSet.allOf(Column.class), java.util.Objects.requireNonNull(source));
  }

  private LogViewer(Path logFile, Set<Column> visibleColumns, LogSource source) {
    this.logFile = logFile;
    this.remoteSource = source;

    configureTableView();
    buildColumns();
    setVisibleColumns(visibleColumns);

    // Attach scroll detection once the skin (and thus the ScrollBar nodes) exist
    tableView
        .skinProperty()
        .addListener(
            (obs, oldSkin, newSkin) -> {
              if (newSkin != null) setupScrollDetection();
            });

    VBox.setVgrow(tableView, Priority.ALWAYS);
    getChildren().add(tableView);

    if (remoteSource == null) {
      startWatching();
    } else startRemotePolling();
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
   * Clears all displayed entries. Must be called on the FX thread (or via {@link
   * Platform#runLater}).
   */
  public void clear() {
    entries.clear();
    remoteEntries.clear();
    lastParsedEntry = null;
  }

  /**
   * Replaces the watched file and restarts tailing from the beginning of the new file. Must be
   * called on the FX thread.
   *
   * @param newFile path to the new log file
   */
  public void setFile(Path newFile) {
    shutdown();
    this.logFile = newFile;
    remoteSource = null;
    remoteEntries.clear();
    lastParsedEntry = null;
    autoScroll = true;
    entries.clear();
    startWatching();
  }

  /** Select or explicitly reset remote viewing; call on the FX thread. */
  public void setSource(LogSource source) {
    shutdown();
    remoteSource = java.util.Objects.requireNonNull(source);
    remoteSource.reset();
    logFile = null;
    entries.clear(); remoteEntries.clear();
    startRemotePolling();
  }

  private void startRemotePolling() {
    long generation = ++sourceGeneration;
    LogSource selected = remoteSource;
    var polling = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
      var thread = new Thread(task, "remote-admin-log-poller");
      thread.setDaemon(true); return thread;
    });
    watcherService = polling;
    polling.scheduleWithFixedDelay(() -> {
      try {
        var page = selected.poll();
        Platform.runLater(() -> {
          if (generation != sourceGeneration || selected != remoteSource) return;
          for (var record : page.records()) {
            String message = record.message() + (record.diagnostics().isEmpty() ? "" : " " + record.diagnostics())
                + (record.stackTrace().isEmpty() ? "" : "\n" + record.stackTrace())
                + (record.truncated() ? "\n[truncated]" : "")
                + (record.partial() ? "\n[partial event]" : "");
            var existing = remoteEntries.get(record.eventId());
            if (existing != null) existing.messageProperty().set(message);
            else {
              var entry = new LogEntry(record.timestamp(), record.level(), "", "", record.logger(), message);
              remoteEntries.put(record.eventId(), entry); entries.add(entry);
            }
          }
          while (remoteEntries.size() > MAX_REMOTE_ROWS) {
            var iterator = remoteEntries.entrySet().iterator();
            var oldest = iterator.next(); entries.remove(oldest.getValue()); iterator.remove();
          }
          if (autoScroll) scrollToBottom();
        });
      } catch (RuntimeException failure) {
        // No raw remote exception messages: a caller may select a different/nonconforming service.
        String diagnostic = failure instanceof org.integratedmodelling.common.utils.Utils.Http.Client.RequestFailure http
            ? "Remote logs unavailable (HTTP " + http.getStatus() + "); explicit reset required"
            : "Remote logs unavailable; explicit reset required";
        Platform.runLater(() -> {
          if (generation != sourceGeneration || selected != remoteSource) return;
          entries.add(new LogEntry("", "WARN", "", "", "remote.logs", diagnostic));
        });
        polling.shutdown();
      }
    }, 0, 1, java.util.concurrent.TimeUnit.SECONDS);
  }

  /**
   * Stops the background file-watcher thread. Call this when the component is removed from the
   * scene to avoid resource leaks.
   */
  public void shutdown() {
    sourceGeneration++;
    if (remoteSource != null) remoteSource.close();
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
    tableView.setRowFactory(
        tv ->
            new TableRow<>() {
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
      tc.setCellValueFactory(
          data -> {
            LogEntry e = data.getValue();
            return switch (col) {
              case TIME -> e.timeProperty();
              case LEVEL -> e.levelProperty();
              case PID -> e.pidProperty();
              case THREAD -> e.threadProperty();
              case LOGGER -> e.loggerProperty();
              case MESSAGE -> e.messageProperty();
            };
          });
      if (col.getPrefWidth() > 0) {
        tc.setPrefWidth(col.getPrefWidth());
        tc.setMinWidth(col.getPrefWidth());
        tc.setMaxWidth(col.getPrefWidth());
      } else {
      }
      if (col == Column.MESSAGE) {
        tc.setCellFactory(c -> new MessageCell());
      }
      columnMap.put(col, tc);
    }

    // Attach a right-click context menu to every column header
    for (Column col : Column.values()) {
      columnMap.get(col).setContextMenu(buildHeaderContextMenu());
    }
  }

  /**
   * Custom table cell for the Message column.
   *
   * <ul>
   *   <li>Displays the first line of the message with ellipsis overflow for long text.
   *   <li>A ▶/▼ toggle button appears on the left when the message has multiple lines; clicking it
   *       reveals a read-only {@link TextArea} with the full message below the summary.
   *   <li>A copy-to-clipboard button (⎘) appears on the right side only while the cursor hovers
   *       over the cell; it copies the full message text to the system clipboard.
   * </ul>
   */
  private static class MessageCell extends TableCell<LogEntry, String> {

    /** Max chars shown in the summary line before the cell is considered expandable. */
    private static final int PREVIEW_CHARS = 200;

    private final Button expandBtn = new Button("▶");
    private final Label summaryLabel = new Label();
    private final Button copyBtn = new Button("⎘");
    private final TextArea fullArea = new TextArea();
    private final HBox topRow;
    private final VBox container;
    private boolean expanded = false;

    MessageCell() {
      expandBtn.getStyleClass().addAll("button", "flat", "small");
      expandBtn.setFocusTraversable(false);
      expandBtn.setOnAction(e -> setExpanded(!expanded));

      summaryLabel.setMaxWidth(Double.MAX_VALUE);
      summaryLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
      HBox.setHgrow(summaryLabel, Priority.ALWAYS);

      // Copy button: always in layout (space reserved) but transparent until hover
      copyBtn.getStyleClass().addAll("button", "flat", "small");
      copyBtn.setFocusTraversable(false);
      copyBtn.setOpacity(0);
      copyBtn.setTooltip(new Tooltip("Copy message to clipboard"));
      copyBtn.setOnAction(
          e -> {
            String msg = getItem();
            if (msg != null) {
              ClipboardContent cc = new ClipboardContent();
              cc.putString(msg);
              Clipboard.getSystemClipboard().setContent(cc);
            }
          });

      topRow = new HBox(4, expandBtn, summaryLabel, copyBtn);
      topRow.setAlignment(Pos.CENTER_LEFT);

      fullArea.setEditable(false);
      fullArea.setWrapText(true);
      fullArea.setPrefRowCount(6);
      fullArea.setMinHeight(80);
      fullArea.setMaxWidth(Double.MAX_VALUE);
      fullArea.setVisible(false);
      fullArea.setManaged(false);

      container = new VBox(2, topRow, fullArea);
      container.setMaxWidth(Double.MAX_VALUE);
      container.setOnMouseEntered(e -> copyBtn.setOpacity(1));
      container.setOnMouseExited(e -> copyBtn.setOpacity(0));

      setText(null);
      setGraphic(container);
      setPadding(Insets.EMPTY);
    }

    @Override
    protected void updateItem(String msg, boolean empty) {
      super.updateItem(msg, empty);
      // Always collapse when the cell is recycled with new content
      if (expanded) setExpanded(false);

      if (empty || msg == null) {
        setGraphic(null);
        return;
      }

      boolean hasMore = msg.contains("\n") || msg.length() > PREVIEW_CHARS;

      // Show only the first line in the summary; the Label clips the rest visually
      int nl = msg.indexOf('\n');
      summaryLabel.setText(nl >= 0 ? msg.substring(0, nl) : msg);
      fullArea.setText(msg);

      expandBtn.setVisible(hasMore);
      expandBtn.setManaged(hasMore);

      setGraphic(container);
    }

    private void setExpanded(boolean expand) {
      expanded = expand;
      expandBtn.setText(expand ? "▼" : "▶");
      fullArea.setVisible(expand);
      fullArea.setManaged(expand);
    }
  }

  private ContextMenu buildHeaderContextMenu() {
    ContextMenu menu = new ContextMenu();
    for (Column col : Column.values()) {
      CheckMenuItem item = new CheckMenuItem(col.getLabel());
      item.setSelected(tableView.getColumns().contains(columnMap.get(col)));
      item.selectedProperty()
          .addListener(
              (obs, was, isNow) -> {
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
    tableView
        .lookupAll(".scroll-bar")
        .forEach(
            node -> {
              if (node instanceof ScrollBar sb
                  && sb.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                sb.valueProperty()
                    .addListener(
                        (obs, oldVal, newVal) -> {
                          double max = sb.getMax();
                          double val = newVal.doubleValue();
                          if (max <= sb.getMin()) {
                            autoScroll = true; // nothing to scroll
                          } else if (val < oldVal.doubleValue() - 1e-9) {
                            autoScroll = false; // user scrolled up
                          } else if (Math.abs(val - max) < 1e-9) {
                            autoScroll = true; // user reached the bottom again
                          }
                        });
              }
            });
  }

  // ── Initial file load ─────────────────────────────────────────────────────────

  private static final Pattern SERVICE_LOG_PATTERN = Pattern.compile(
      "^\\[?(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+)\\]?\\s+"
          + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+\\[([^\\]]+)\\]\\s+"
          + "(\\S+?)(?::\\s+|\\s+-\\s+)(.*)$");

  /** One immutable file selection and worker-owned cursor; no state leaks across setFile. */
  private static final class LocalTail {
    final Path file;
    final long generation;
    long position;
    Object identity;
    java.nio.file.attribute.FileTime modified;
    byte[] prefix = new byte[0], tail = new byte[0];
    boolean initialized, replacement;
    LocalTail(Path file, long generation) { this.file = file; this.generation = generation; }
  }

  private void startWatching() {
    var selected = new LocalTail(logFile, sourceGeneration);
    localTail = selected;
    watcherService = Executors.newSingleThreadExecutor(task -> {
      var thread = new Thread(task, "LogViewer[" + selected.file.getFileName() + "]");
      thread.setDaemon(true); return thread;
    });
    watcherService.submit(() -> watchLoop(selected));
  }

  private void watchLoop(LocalTail selected) {
    WatchService watcher = null;
    try {
      try {
        watcher = FileSystems.getDefault().newWatchService();
        selected.file.toAbsolutePath().getParent().register(watcher,
            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY);
      } catch (IOException unavailable) {
        if (watcher != null) watcher.close();
        watcher = null;
      }
      // Register before the initial read. Periodic reconciliation covers missed events and creation.
      while (!Thread.currentThread().isInterrupted() && selected.generation == sourceGeneration) {
        readLocalContent(selected);
        if (watcher == null) { Thread.sleep(500); continue; }
        WatchKey key = watcher.poll(500, TimeUnit.MILLISECONDS);
        if (key == null) continue;
        synchronized (selected) {
          for (var event : key.pollEvents()) {
            if (event.kind() != StandardWatchEventKinds.OVERFLOW
                && selected.file.getFileName().equals(event.context())
                && (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                    || event.kind() == StandardWatchEventKinds.ENTRY_DELETE)) selected.replacement = true;
          }
        }
        if (!key.reset()) { watcher.close(); watcher = null; }
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (IOException unavailable) {
      // Setup failure normally falls back to periodic reconciliation.
    } finally {
      if (watcher != null) try { watcher.close(); } catch (IOException ignored) {}
    }
  }

  private void readNewContent() {
    var selected = localTail;
    if (selected != null) readLocalContent(selected);
  }

  private static byte[] readRange(java.nio.channels.FileChannel channel, long offset, int length)
      throws IOException {
    var buffer = java.nio.ByteBuffer.allocate(length);
    while (buffer.hasRemaining()) {
      int count = channel.read(buffer, offset + buffer.position());
      if (count <= 0) break;
    }
    return Arrays.copyOf(buffer.array(), buffer.position());
  }

  private void readLocalContent(LocalTail selected) {
    synchronized (selected) {
      if (selected.generation != sourceGeneration) return;
      try (var channel = java.nio.channels.FileChannel.open(selected.file, StandardOpenOption.READ)) {
        var attrs = Files.readAttributes(selected.file, java.nio.file.attribute.BasicFileAttributes.class);
        Object identity = java.util.List.of(String.valueOf(attrs.fileKey()), attrs.creationTime());
        long size = channel.size();
        boolean reset = !selected.initialized || selected.replacement
            || !java.util.Objects.equals(selected.identity, identity) || size < selected.position;
        if (!reset && selected.position > 0) {
          reset = (size == selected.position && !attrs.lastModifiedTime().equals(selected.modified))
              || !Arrays.equals(selected.prefix, readRange(channel, 0, selected.prefix.length))
              || !Arrays.equals(selected.tail,
                  readRange(channel, selected.position - selected.tail.length, selected.tail.length));
        }
        long offset = reset ? 0 : selected.position;
        byte[] bytes = readRange(channel, offset, Math.toIntExact(size - offset));
        int complete = bytes.length;
        while (complete > 0 && bytes[complete - 1] != '\n') complete--;
        if (complete == 0 && !reset) return;
        long position = offset + complete;
        byte[] prefix = readRange(channel, 0, (int)Math.min(position, 4096));
        byte[] tail = readRange(channel, Math.max(0, position - 4096), (int)Math.min(position, 4096));
        var after = Files.readAttributes(selected.file, java.nio.file.attribute.BasicFileAttributes.class);
        if (!identity.equals(java.util.List.of(String.valueOf(after.fileKey()), after.creationTime()))) return;
        selected.position = position; selected.identity = identity;
        selected.modified = attrs.lastModifiedTime(); selected.prefix = prefix; selected.tail = tail;
        selected.initialized = true; selected.replacement = false;
        String content = new String(bytes, 0, complete, StandardCharsets.UTF_8);
        boolean clear = reset;
        Platform.runLater(() -> {
          if (selected.generation != sourceGeneration || remoteSource != null) return;
          if (clear) { entries.clear(); lastParsedEntry = null; }
          entries.addAll(parseLines(content.split("\\r?\\n", -1), clear));
          tableView.refresh(); // Continuation-only changes must update expanded cells too.
          if (autoScroll) scrollToBottom();
        });
      } catch (IOException unavailable) {
        // A missing/temporarily unreadable file is retried on the next poll.
      }
    }
  }

  /** Invoked only on the FX thread, including continuation-line property changes. */
  private List<LogEntry> parseLines(String[] lines, boolean resetContext) {
    if (resetContext) lastParsedEntry = null;
    List<LogEntry> batch = new ArrayList<>();
    for (String line : lines) {
      if (line.isBlank()) continue;
      Matcher m = LOG_PATTERN.matcher(line);
      if (m.matches()) {
        LogEntry entry =
            new LogEntry(m.group(1), m.group(3), m.group(2), m.group(4), m.group(5), m.group(6));
        batch.add(entry);
        lastParsedEntry = entry;
      } else {
        Matcher current = SERVICE_LOG_PATTERN.matcher(line);
        if (current.matches()) {
          var entry = new LogEntry(current.group(1), current.group(2), "", current.group(3),
              current.group(4), current.group(5));
          batch.add(entry); lastParsedEntry = entry;
        } else if (lastParsedEntry != null) lastParsedEntry.appendMessage(line);
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
      case "WARN" -> STYLE_WARN;
      case "ERROR" -> STYLE_ERROR;
      case "FATAL" -> STYLE_FATAL;
      default -> ""; // INFO – use the theme default
    };
  }
}

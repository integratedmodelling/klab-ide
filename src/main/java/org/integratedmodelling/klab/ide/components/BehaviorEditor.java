package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.integratedmodelling.klab.modeler.model.NavigableKActorsBehavior;
import org.integratedmodelling.klabeditor.MonacoEditorView;
import org.integratedmodelling.klabeditor.lsp.KlabLspService;
import org.integratedmodelling.klabeditor.lsp.LspDocumentSession;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

/** Editor for one standalone {@code .kactor} file. */
public class BehaviorEditor extends EditorPage<NavigableKActorsBehavior, Object> {

  private record AgentGroup(String label) {}

  private final Path file;
  private final Consumer<Path> savedCallback;
  private final Map<String, Boolean> runningAgents = new HashMap<>();
  private final Map<String, Boolean> associatedAgents = new HashMap<>();
  private final Map<Node, LspDocumentSession> lspSessions = new IdentityHashMap<>();
  private NavigableKActorsBehavior behavior;
  private IDEContextScope contextScope;
  private TreeView<Object> treeView;
  private MonacoEditorView monacoEditor;
  private Label statusLabel;
  private String documentUri;

  public BehaviorEditor(Path file, KActorsBehavior asset, Consumer<Path> savedCallback) {
    super(new NavigableKActorsBehavior(asset, null));
    this.file = file.toAbsolutePath().normalize();
    this.savedCallback = savedCallback;
    this.behavior = getEditedAsset();
  }

  public Path getFile() {
    return file;
  }

  @Override
  protected void showContent() {
    super.showContent();
    Platform.runLater(() -> edit(behavior));
  }

  @Override
  protected void onVisualize(boolean visibleAfterCall) {
    KlabIDEController.instance().setFocalEditor(this, visibleAfterCall);
  }

  @Override
  protected Node createEditor(Object asset) {
    if (!(asset instanceof NavigableKActorsBehavior)) return null;

    documentUri = file.toUri().toString();
    String languageId = behavior.getLanguage().languageId();
    String theme = Theme.CURRENT_THEME.isDark() ? "vs-dark" : "vs";
    monacoEditor = new MonacoEditorView(documentUri, this::save);
    monacoEditor.loadEditor(behavior.getSourceCode(), languageId, theme);

    var lsp = KlabLspService.getInstance();
    if (lsp.ensureInitialized(
        KlabIDEController.instance().getLanguageServer(), KlabIDEController.instance().user())) {
      var session =
          new LspDocumentSession(monacoEditor, languageId, behavior.getSourceCode());

      VBox editor = new VBox(createEditorToolbar(), monacoEditor, createStatusBar());
      VBox.setVgrow(monacoEditor, Priority.ALWAYS);
      lspSessions.put(editor, session);
      return editor;
    }

    VBox editor = new VBox(createEditorToolbar(), monacoEditor, createStatusBar());
    VBox.setVgrow(monacoEditor, Priority.ALWAYS);
    return editor;
  }

  @Override
  protected void disposeEditor(Object asset, Node editor) {
    var session = lspSessions.remove(editor);
    if (session != null) {
      session.close();
    }
  }

  private Node createEditorToolbar() {
    var type = new Label(behavior.getBehaviorType().name());
    type.setGraphic(new IconLabel(Theme.BEHAVIOR_ICON, 16, Theme.FOREGROUND_COLOR));
    type.setTooltip(new Tooltip(file.toString()));
    var location = new Label(file.getFileName().toString());
    location.setTooltip(new Tooltip(file.toString()));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var debug = icon(CarbonIcons.DEBUG, "Debug behavior", false);
    var run = icon(Material2MZ.PLAY_ARROW, "Run behavior", false);
    var stop = icon(Material2MZ.STOP, "Stop behavior", false);
    var publish = icon(MaterialDesign.MDI_CLOUD_UPLOAD, "Publish to an open workspace", false);
    var toolbar = new HBox(8, type, location, spacer, debug, run, stop, publish);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.setPadding(new Insets(5, 8, 5, 8));
    toolbar.getStyleClass().add(Styles.DENSE);
    return toolbar;
  }

  private IconButton icon(org.kordamp.ikonli.Ikon icon, String tooltip, boolean enabled) {
    return IconButton.of(icon, 18, Theme.FOREGROUND_COLOR, Color.GRAY, () -> true)
        .tooltip(tooltip)
        .enabled(enabled);
  }

  private Node createStatusBar() {
    statusLabel = new Label("Stopped", new IconLabel(Material2MZ.STOP, 12, Color.GRAY));
    statusLabel.setDisable(true);
    statusLabel.setTooltip(new Tooltip("Behavior execution is not active"));
    var bar = new HBox(statusLabel);
    bar.setAlignment(Pos.CENTER_RIGHT);
    bar.setPadding(new Insets(3, 8, 3, 8));
    bar.setStyle("-fx-background-color: -color-neutral-muted;");
    return bar;
  }

  private void save(String contents) {
    try {
      Files.writeString(file, contents, StandardCharsets.UTF_8);
      behavior = new NavigableKActorsBehavior(LocalBehavior.parse(file, contents), null);
      if (treeView != null) treeView.setRoot(createTreeRoot());
      if (savedCallback != null) savedCallback.accept(file);
    } catch (IOException e) {
      KlabIDEController.instance().handleNotification(Notification.error(e));
    }
  }

  @Override
  protected void onSingleClickItemSelection(Object value) {
    if (value instanceof KActorsAction action && monacoEditor != null) {
      monacoEditor.setCursorPosition(action.getOffsetInDocument());
    }
  }

  @Override
  protected void onDoubleClickItemSelection(Object value) {
    onSingleClickItemSelection(value);
  }

  @Override
  protected TreeView<Object> createContentTree() {
    treeView = new TreeView<>(createTreeRoot());
    treeView.setShowRoot(false);
    treeView.setPrefWidth(340);
    treeView.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    treeView.setCellFactory(ignored -> new BehaviorTreeCell());
    return treeView;
  }

  private TreeItem<Object> createTreeRoot() {
    var root = new TreeItem<Object>();
    var behaviorItem = new TreeItem<Object>(behavior);
    behaviorItem.setExpanded(true);
    for (var action : behavior.getStatements()) behaviorItem.getChildren().add(new TreeItem<>(action));
    root.getChildren().add(behaviorItem);

    var agents = new TreeItem<Object>(new AgentGroup("Agents"));
    agents.setExpanded(true);
    if (contextScope != null) {
      for (var observation : contextScope.getObservations()) {
        if (observation.getObservable().is(SemanticType.AGENT)) {
          agents.getChildren().add(new TreeItem<>(observation));
        }
      }
    }
    root.getChildren().add(agents);
    return root;
  }

  @Override
  protected Node createBrowsingContent(TreeView<Object> tree) {
    VBox.setVgrow(tree, Priority.ALWAYS);
    var metadata = reserved("Interaction", "Message-sending UI");
    var debugger = reserved("Debugger", "Debugger UI");
    return new VBox(tree, new Separator(), metadata, debugger);
  }

  private Node reserved(String title, String description) {
    var titleLabel = new Label(title);
    titleLabel.getStyleClass().add(Styles.TITLE_4);
    var descriptionLabel = new Label(description);
    descriptionLabel.setDisable(true);
    var box = new VBox(3, titleLabel, descriptionLabel);
    box.setPadding(new Insets(7));
    box.setMinHeight(58);
    return box;
  }

  private final class BehaviorTreeCell extends TreeCell<Object> {
    @Override
    protected void updateItem(Object item, boolean empty) {
      super.updateItem(item, empty);
      setText(null);
      setGraphic(null);
      setContextMenu(null);
      if (empty || item == null) return;

      if (item instanceof NavigableKActorsBehavior b) {
        setText(b.getUrn());
        setGraphic(new IconLabel(Theme.BEHAVIOR_ICON, 15, Theme.FOREGROUND_COLOR));
      } else if (item instanceof KActorsAction action) {
        setText(action.getUrn());
        setGraphic(new IconLabel(Theme.ACTION_ICON, 15, Theme.FOREGROUND_COLOR));
      } else if (item instanceof AgentGroup group) {
        setText(group.label());
        setGraphic(new IconLabel(Material2MZ.PEOPLE, 15, Theme.FOREGROUND_COLOR));
      } else if (item instanceof Observation observation) {
        configureAgent(observation);
      }
    }

    private void configureAgent(Observation observation) {
      String key = observation.getUrn();
      boolean associated = associatedAgents.getOrDefault(key, false);
      boolean running = runningAgents.getOrDefault(key, false);
      setText(observation.getName() == null ? observation.getUrn() : observation.getName());
      var dot =
          new IconLabel(
              Material2AL.FIBER_MANUAL_RECORD,
              11,
              associated && running ? Color.GREEN : Color.RED);
      dot.setTooltip(
          new Tooltip(
              !associated ? "No behavior associated" : running ? "Running; click to stop" : "Stopped; click to start"));
      dot.setOnMouseClicked(
          event -> {
            if (associated) {
              runningAgents.put(key, !running);
              updateItem(observation, false);
              event.consume();
            }
          });
      setGraphic(dot);

      var menu = new ContextMenu();
      var apply = new MenuItem("Apply behavior", new IconLabel(Theme.BEHAVIOR_ICON, 14, Color.GREEN));
      apply.setDisable(associated);
      apply.setOnAction(
          event -> {
            associatedAgents.put(key, true);
            runningAgents.put(key, true);
            updateItem(observation, false);
          });
      menu.getItems().add(apply);
      if (associated) {
        var toggle =
            new MenuItem(
                running ? "Stop behavior" : "Start behavior",
                new IconLabel(running ? Material2MZ.STOP : Material2MZ.PLAY_ARROW, 14, running ? Color.RED : Color.GREEN));
        toggle.setOnAction(
            event -> {
              runningAgents.put(key, !running);
              updateItem(observation, false);
            });
        menu.getItems().add(toggle);
      }
      setContextMenu(menu);
    }
  }

  @Override
  public void setDigitalTwin(IDEContextScope scope, boolean focus) {
    super.setDigitalTwin(scope, focus);
    contextScope = scope;
    if (treeView != null) treeView.setRoot(createTreeRoot());
  }

  @Override
  public boolean isAffectedBy(IDEContextScope scope) {
    return contextScope == scope;
  }

  @Override
  public void closeDigitalTwin(IDEContextScope scope) {
    if (contextScope == scope) setDigitalTwin(null, true);
  }

  @Override
  public void unsetDigitalTwin(IDEContextScope scope) {
    if (contextScope == scope) setDigitalTwin(null, true);
  }
}

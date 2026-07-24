package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
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
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

/** Editor for one standalone {@code .kactor} file. */
public class BehaviorEditor extends EditorPage<NavigableKActorsBehavior, Object> {

  private static final String JAVA_CODE_EDITOR_KEY = "java-code";
  private static final String AGENT_CONSOLE_EDITOR_KEY = "agent-console";

  private IconButton debug;
  private IconButton compile;
  private IconButton run;
  private IconButton stop;
  private IconButton publish;
  private IconLabel typeLabel;
  private IconButton sourceCode;

  private record AgentGroup(String label) {}

  private final Path file;
  private final Consumer<Path> savedCallback;
  private final Consumer<Ikon> behaviorIconChangedCallback;
  private final Consumer<Agent> debugAgentAvailableCallback;
  private final Consumer<Agent> debugTargetRequestedCallback;
  private final Map<String, Boolean> runningAgents = new HashMap<>();
  private final Map<String, Boolean> associatedAgents = new HashMap<>();
  private final Map<Node, LspDocumentSession> lspSessions = new IdentityHashMap<>();
  private NavigableKActorsBehavior behavior;
  private IDEContextScope contextScope;
  private TreeView<Object> treeView;
  private Object sourceEditorAsset;
  private MonacoEditorView monacoEditor;
  private MonacoEditorView javaCodeEditor;
  private Tab javaCodeTab;
  private String displayedJavaCode;
  private Label statusLabel;
  private String documentUri;
  private boolean stale = false;
  private boolean compilationSuccessful;
  private final Set<Agent> agents = Collections.synchronizedSet(new LinkedHashSet<>());
  private final Set<Agent> debugAgents = Collections.synchronizedSet(new LinkedHashSet<>());
  private Agent currentDebugTarget;
  private AgentConsoleView agentConsole;

  public BehaviorEditor(
      Path file,
      KActorsBehavior asset,
      Consumer<Path> savedCallback,
      Consumer<Ikon> behaviorIconChangedCallback,
      Consumer<Agent> debugAgentAvailableCallback,
      Consumer<Agent> debugTargetRequestedCallback) {
    super(new NavigableKActorsBehavior(asset, null));
    this.file = file.toAbsolutePath().normalize();
    this.savedCallback = savedCallback;
    this.behaviorIconChangedCallback = behaviorIconChangedCallback;
    this.debugAgentAvailableCallback = debugAgentAvailableCallback;
    this.debugTargetRequestedCallback = debugTargetRequestedCallback;
    this.behavior = getEditedAsset();
  }

  public Path getFile() {
    return file;
  }

  @Override
  protected void showContent() {
    super.showContent();
    Platform.runLater(
        () -> {
          edit(behavior);
          if (getLocalRuntime().isPresent()) {
            doCompile();
          }
        });
    updateStatus();
  }

  @Override
  protected void onVisualize(boolean visibleAfterCall) {
    KlabIDEController.instance().setFocalEditor(this, visibleAfterCall);
  }

  @Override
  protected Node createEditor(Object asset) {
    if (!(asset instanceof NavigableKActorsBehavior)) return null;

    sourceEditorAsset = asset;
    documentUri = file.toUri().toString();
    String languageId = behavior.getLanguage().languageId();
    String theme = Theme.CURRENT_THEME.isDark() ? "vs-dark" : "vs";
    monacoEditor = new MonacoEditorView(documentUri, this::save);
    monacoEditor.runAfterEditorRendered(
        () -> monacoEditor.markNotifications(behavior.getNotifications(), false));
    monacoEditor.loadEditor(behavior.getSourceCode(), languageId, theme);
    var lsp = KlabLspService.getInstance();
    if (lsp.ensureInitialized(
        KlabIDEController.instance().getLanguageServer(), KlabIDEController.instance().user())) {
      var session = new LspDocumentSession(monacoEditor, languageId, behavior.getSourceCode());

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

    this.typeLabel = new IconLabel(Theme.getIcon(behavior), 20, Color.GREY);
    typeLabel.setTooltip(new Tooltip(file.toString()));
    var location = new Label(file.getFileName().toString(), null);
    location.setTooltip(new Tooltip(file.toString()));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    this.debug = icon(CarbonIcons.DEBUG, "Debug behavior", false, false, this::doDebug);
    this.compile =
        icon(
            CarbonIcons.CHECKMARK_OUTLINE_WARNING,
            "Compile and check for errors",
            false,
            true,
            this::doCompile);
    this.sourceCode =
        icon(
            MaterialDesign.MDI_LANGUAGE_JAVASCRIPT, // TODO wrong language
            "Display Java-compiled source code",
            false,
            true,
            this::toggleCompile);

    // enable auto-compilation if we have a local runtime
    if (getLocalRuntime().isPresent()) {
      this.compile.setSelected(true);
    }

    this.run =
        icon(
            Material2MZ.PLAY_ARROW,
            "Compile the behavior and run a new agent",
            false,
            false,
            this::doRun);
    this.stop = icon(Material2MZ.STOP, "Stop all running agents", false, false, this::doStop);
    this.publish =
        icon(
            MaterialDesign.MDI_CLOUD_UPLOAD,
            "Publish to a local workspace",
            false,
            false,
            this::doPublish);
    var toolbar =
        new HBox(
            8,
            typeLabel,
            location,
            spacer,
            publish,
            new Separator(Orientation.VERTICAL),
            compile,
            sourceCode,
            new Separator(Orientation.VERTICAL),
            debug,
            run,
            stop);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.setPadding(new Insets(5, 8, 5, 8));
    toolbar.getStyleClass().add(Styles.DENSE);
    return toolbar;
  }

  private Boolean toggleCompile() {
    if (sourceCode.isToggled()) {
      return doCompile();
    }
    closeAuxiliaryEditor(JAVA_CODE_EDITOR_KEY);
    return true;
  }

  /**
   * TODO just toggle (and compile if on); should be on when service is available; link action to
   * save; handle Java source code if requested. Must turn the behavior icon green/yellow/red when
   * checked.
   *
   * @return
   */
  private boolean doCompile() {
    return compileBehavior(false, true) != null;
  }

  private Agent compileBehavior(boolean debugging, boolean testing) {

    var localRuntime = getLocalRuntime();

    if (localRuntime.isEmpty() || this.behavior == null) {
      compilationSuccessful = false;
      updateStatus();
      return null;
    }

    var options = EnumSet.noneOf(RuntimeAgent.CompilationOptions.class);
    if (testing) {
      options.add(RuntimeAgent.CompilationOptions.DO_NOT_COMPILE_JAVA);
    }

    if (sourceCode.isToggled()) {
      options.add(RuntimeAgent.CompilationOptions.INCLUDE_JAVA_CODE);
    }
    if (debugging) {
      options.add(RuntimeAgent.CompilationOptions.COMPILE_FOR_DEBUGGING);
    }

    // FIXME submit this to a single-threaded executor!
    var agent =
        localRuntime
            .get()
            .createAgent(
                this.behavior.getDelegate(),
                "Al Caprone",
                options,
                KlabIDEController.instance().user());

    if (agent != null) {
      monacoEditor.markNotifications(agent.getNotifications(), true);
      updateBehaviorIcon(agent.getNotifications());
      if (sourceCode.isToggled() && agent instanceof AgentImpl agent1) {
        showJavaCode(agent1.getJavaCode());
      }
      updateStatus();
      return agent;
    }
    compilationSuccessful = false;
    updateStatus();
    return null;
  }

  private void updateBehaviorIcon(Collection<Notification> notifications) {
    refreshBehaviorIconType();

    var style = Styles.SUCCESS;
    var color = Color.GREEN;
    if (notifications != null) {
      for (var notification : notifications) {
        if (notification.getLevel().severity >= Notification.Level.Error.severity) {
          style = Styles.DANGER;
          color = Color.RED;
          break;
        }
        if (notification.getLevel().severity >= Notification.Level.Warning.severity) {
          style = Styles.WARNING;
          color = Color.GOLDENROD;
        }
      }
    }
    typeLabel.getStyleClass().removeAll(Styles.DANGER, Styles.WARNING, Styles.SUCCESS);
    typeLabel.getStyleClass().add(style);
    typeLabel.setTextFill(color);
    compilationSuccessful = Styles.SUCCESS.equals(style);
  }

  private void refreshBehaviorIconType() {
    var icon = Theme.getIcon(behavior);
    typeLabel.setGraphic(null);
    typeLabel.setIcon(icon, 20);
    if (sourceEditorAsset != null) {
      setEditorGraphic(sourceEditorAsset, Theme.getGraphics(behavior));
    }
    refreshBehaviorTreeItem();
    if (behaviorIconChangedCallback != null) {
      behaviorIconChangedCallback.accept(icon);
    }
  }

  private void refreshBehaviorTreeItem() {
    if (treeView == null || treeView.getRoot() == null) {
      return;
    }
    treeView.getRoot().getChildren().stream()
        .filter(item -> item.getValue() instanceof NavigableKActorsBehavior)
        .findFirst()
        .ifPresent(
            item -> {
              item.setValue(behavior);
              treeView.refresh();
            });
  }

  private Optional<RuntimeService> getLocalRuntime() {
    return KlabIDEController.instance().user().getServices(RuntimeService.class).stream()
        .filter(KlabService::isLocal)
        .findFirst();
  }

  private Optional<ResourcesService> getLocalResourcesService() {
    return KlabIDEController.instance().user().getServices(ResourcesService.class).stream()
        .filter(KlabService::isLocal)
        .findFirst();
  }

  private void showJavaCode(String javaCode) {
    if (javaCode == null || javaCode.isBlank()) {
      return;
    }
    var updated = javaCodeEditor != null && !Objects.equals(displayedJavaCode, javaCode);
    if (javaCodeEditor == null) {
      var javaDocumentUri = documentUri + ".java";
      var theme = Theme.CURRENT_THEME.isDark() ? "vs-dark" : "vs";
      javaCodeEditor = new MonacoEditorView(javaDocumentUri, ignored -> {});
      javaCodeEditor.loadEditor(javaCode, "java", theme);
    } else if (updated) {
      javaCodeEditor.setText(javaCode);
    }
    displayedJavaCode = javaCode;

    var tab = showAuxiliaryEditor(JAVA_CODE_EDITOR_KEY, "Java code", javaCodeEditor);
    if (tab != javaCodeTab) {
      javaCodeTab = tab;
      tab.selectedProperty()
          .addListener(
              (observable, wasSelected, selected) -> {
                if (selected) {
                  clearJavaCodeUpdateCue();
                }
              });
    }
    if (updated && !tab.isSelected()) {
      tab.setStyle("-fx-font-weight: bold;");
      var updateIndicator = new IconLabel(Material2AL.FIBER_MANUAL_RECORD, 9, Color.DODGERBLUE);
      Tooltip.install(updateIndicator, new Tooltip("Java code updated"));
      tab.setGraphic(updateIndicator);
    }
  }

  private void clearJavaCodeUpdateCue() {
    if (javaCodeTab != null) {
      javaCodeTab.setStyle("");
      javaCodeTab.setGraphic(null);
    }
  }

  private boolean doRun() {
    return launchAgent(false, false);
  }

  private boolean doDebug() {
    return launchAgent(true, false);
  }

  private boolean launchAgent(boolean debugging, boolean testing) {
    if (!compilationSuccessful || getLocalRuntime().isEmpty()) {
      return false;
    }

    var agent = compileBehavior(debugging, testing);
    if (agent == null || !compilationSuccessful || !agent.isViable()) {
      return reportLaunchFailure(agent, debugging, null);
    }
    try {
      if (!agent.isAlive() && !agent.start()) {
        return reportLaunchFailure(agent, debugging, null);
      }
    } catch (Throwable throwable) {
      return reportLaunchFailure(agent, debugging, throwable);
    }
    if (agent.isViable() && agent.isAlive()) {
      agents.add(agent);
      if (debugging) {
        debugAgents.add(agent);
        if (debugAgentAvailableCallback != null) {
          debugAgentAvailableCallback.accept(agent);
        } else if (currentDebugTarget == null) {
          setCurrentDebugTarget(agent);
        }
      }
      refreshAgentStates();
      return true;
    }
    return reportLaunchFailure(agent, debugging, null);
  }

  private boolean reportLaunchFailure(Agent agent, boolean debugging, Throwable cause) {
    Notification notification = null;
    if (agent != null && agent.getNotifications() != null) {
      notification =
          agent.getNotifications().stream()
              .filter(
                  candidate -> candidate.getLevel().severity >= Notification.Level.Error.severity)
              .findFirst()
              .orElse(null);
    }
    if (notification == null) {
      var action = debugging ? "debug" : "run";
      notification =
          cause == null
              ? Notification.error("Unable to " + action + " behavior: agent failed to start")
              : Notification.error(
                  "Unable to " + action + " behavior: agent failed to start", cause);
    }
    KlabIDEController.instance().handleNotifications(List.of(notification));
    refreshAgentStates();
    return false;
  }

  private boolean doStop() {
    var stopped = true;
    for (var agent : agentSnapshot()) {
      if (agent.isAlive()) {
        stopped &= agent.stop();
      }
    }
    refreshAgentStates();
    return stopped;
  }

  private boolean doPublish() {
    var localResources = getLocalResourcesService();
    if (!compilationSuccessful || localResources.isEmpty()) {
      return false;
    }

    var service = localResources.get();
    var selectedProject = showProjectSelectionDialog(service);
    if (selectedProject.isEmpty()) {
      return false;
    }
    publishBehavior(service, selectedProject.get());
    return true;
  }

  private Optional<Project> showProjectSelectionDialog(ResourcesService service) {
    var user = KlabIDEController.instance().user();
    var root = new TreeItem<Object>();
    for (var workspaceName : service.capabilities(user).getWorkspaceNames()) {
      var workspace = service.retrieveWorkspace(workspaceName, user);
      if (workspace == null) {
        continue;
      }
      var workspaceItem = new TreeItem<Object>(workspace);
      workspaceItem.setExpanded(true);
      for (var project : workspace.getProjects()) {
        workspaceItem.getChildren().add(new TreeItem<>(project));
      }
      root.getChildren().add(workspaceItem);
    }

    var projects = new TreeView<>(root);
    projects.setShowRoot(false);
    projects.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    projects.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    projects.setPrefSize(440, 320);
    projects.setCellFactory(
        ignored ->
            new TreeCell<>() {
              @Override
              protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
                if (empty || item == null) {
                  return;
                }
                if (item instanceof Workspace workspace) {
                  setText(workspace.getUrn());
                  setGraphic(new IconLabel(Theme.WORKSPACE_ICON, 16, Theme.FOREGROUND_COLOR));
                } else if (item instanceof Project project) {
                  setText(project.getUrn());
                  setGraphic(new IconLabel(Theme.PROJECT_ICON, 16, Theme.FOREGROUND_COLOR));
                }
              }
            });

    var publishButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    var dialog = new Dialog<Project>();
    dialog.setTitle("Publish behavior");
    dialog.setHeaderText("Select the local project that will receive this behavior");
    dialog.getDialogPane().setContent(projects);
    dialog.getDialogPane().getButtonTypes().addAll(publishButton, ButtonType.CANCEL);

    var ok = (Button) dialog.getDialogPane().lookupButton(publishButton);
    ok.setDisable(true);
    projects
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, oldItem, selectedItem) ->
                ok.setDisable(
                    selectedItem == null || !(selectedItem.getValue() instanceof Project)));
    dialog.setResultConverter(
        button ->
            button == publishButton
                    && projects.getSelectionModel().getSelectedItem() != null
                    && projects.getSelectionModel().getSelectedItem().getValue()
                        instanceof Project project
                ? project
                : null);
    if (getScene() != null) {
      dialog.initOwner(getScene().getWindow());
    }
    return dialog.showAndWait();
  }

  /**
   * Callback invoked after the project picker closes with a confirmed selection. Publication will
   * be implemented here.
   */
  private void publishBehavior(ResourcesService service, Project project) {
    // TODO publish the current behavior to project.getUrn() through service.
  }

  private IconButton icon(
      org.kordamp.ikonli.Ikon icon,
      String tooltip,
      boolean enabled,
      boolean toggle,
      Callable<Boolean> action) {
    return toggle
        ? IconButton.toggle(icon, 18, Theme.FOREGROUND_COLOR, Color.GRAY, action)
            .tooltip(tooltip)
            .enabled(enabled)
        : IconButton.of(icon, 18, Theme.FOREGROUND_COLOR, Color.GRAY, action)
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

  /**
   * Revise the UI based on the current behavior status, running agents, linked scopes/observations
   * and agent state. Called after save and after any remote agent event.
   */
  private void updateStatus() {
    Platform.runLater(
        () -> {
          var localRuntime = getLocalRuntime();

          if (localRuntime.isEmpty() || this.behavior == null) {
            // everything is disabled
            this.debug.enabled(false);
            this.compile.enabled(false);
            this.run.enabled(false);
            this.stop.enabled(false);
            updateAgentActionButtons(sourceIsValid());
          } else {
            refreshBehaviorIconType();
            var warnings = 0;
            var errors = 0;
            for (var notification : behavior.getNotifications()) {
              if (notification.getLevel().severity >= Notification.Level.Error.severity) {
                errors++;
              } else if (notification.getLevel().severity >= Notification.Level.Warning.severity) {
                warnings++;
              }
            }
            monacoEditor.markNotifications(behavior.getNotifications(), true);

            // compile and run depend on errors
            if (errors == 0 && !this.stale) {
              this.compile.enabled(true);
              this.sourceCode.enabled(true);
            }
            updateAgentActionButtons(errors == 0 && !this.stale);
          }

          // Set the stale/clean behavior status
          // Check the main behavior icon and tooltip:
          //   - use proper icon and tooltip for type (which may have changed)
          //   - use color depending on annotations - red (errors), yellow (warnings), green
          // (OK/info)
          //   - rewrite label based on URN
          // Activate and select the buttons for the current status:
          //   - if behavior, can always span another: use "Spawn" icon and tooltip, color depends
          // on
          //     already having live agents of this type
          //   - if session-bound, set based on the agent reference
          //   - setup notification pane with indicators
          //   - activate/deactivate messaging UI

        });
  }

  private void save(String contents) {
    try {
      Files.writeString(file, contents, StandardCharsets.UTF_8);
      var parsed =
          KlabIDEController.instance()
              .user()
              .getService(ResourcesService.class)
              .readBehavior(file.toUri().toURL(), KlabIDEController.instance().user());

      this.stale = false;
      if (parsed == null) {
        // syntax errors cause this. Do not submit and return. FIXME At this point the
        //  loaded behavior has diverged from the source code: we should either null it
        //  or record an obsolete state that should prevent compilation.
        this.stale = true;
        this.compilationSuccessful = false;
        updateStatus();
        return;
      }

      this.behavior = new NavigableKActorsBehavior(parsed, null);
      this.compilationSuccessful = false;
      for (var notification : behavior.getNotifications()) {
        // TODO send to editor to show. Needs a notification method that only consumes those with
        //  lexical context
        Logging.INSTANCE.notifications(notification);
        if (notification.getLevel().severity >= Notification.Level.Error.severity) {
          this.stale = true;
        }
      }
      if (this.compile.isToggled()) {
        doCompile();
      }
      if (treeView != null) treeView.setRoot(createTreeRoot());
      if (savedCallback != null) savedCallback.accept(file);

    } catch (IOException e) {
      KlabIDEController.instance().handleNotification(Notification.error("Error song behavior", e));
      this.stale = true;
    }

    updateStatus();
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
    for (var action : behavior.getStatements())
      behaviorItem.getChildren().add(new TreeItem<>(action));
    root.getChildren().add(behaviorItem);

    var agents = new TreeItem<Object>(new AgentGroup("Agents"));
    agents.setExpanded(true);
    populateAgentItems(agents);
    root.getChildren().add(agents);
    return root;
  }

  private void populateAgentItems(TreeItem<Object> agentGroup) {
    for (var agent : agentSnapshot()) {
      agentGroup.getChildren().add(new TreeItem<>(agent));
    }
    if (contextScope != null) {
      for (var observation : contextScope.getObservations()) {
        if (observation.getObservable().is(SemanticType.AGENT)) {
          agentGroup.getChildren().add(new TreeItem<>(observation));
        }
      }
    }
  }

  private List<Agent> agentSnapshot() {
    synchronized (agents) {
      return List.copyOf(agents);
    }
  }

  public Set<Agent> getDebugAgents() {
    synchronized (debugAgents) {
      return Set.copyOf(debugAgents);
    }
  }

  /** Update the current debug target when coordinated by the owning view. */
  public void setCurrentDebugTarget(Agent agent) {
    currentDebugTarget = debugAgents.contains(agent) ? agent : null;
    if (currentDebugTarget == null) {
      if (agentConsole != null) {
        agentConsole.setAgent(null);
      }
      closeAuxiliaryEditor(AGENT_CONSOLE_EDITOR_KEY);
    } else {
      if (agentConsole == null) {
        agentConsole = new AgentConsoleView();
      }
      agentConsole.setAgent(currentDebugTarget);
      var tab =
          showAuxiliaryEditor(
              AGENT_CONSOLE_EDITOR_KEY, "Console — " + currentDebugTarget.getName(), agentConsole);
      tab.setOnCloseRequest(
          event -> {
            if (agentConsole != null) {
              agentConsole.setAgent(null);
            }
          });
    }
    refreshAgentStates();
  }

  @Override
  public void close() {
    if (agentConsole != null) {
      agentConsole.close();
      agentConsole = null;
    }
    super.close();
  }

  private void requestDebugTarget(Agent agent) {
    if (debugTargetRequestedCallback != null) {
      debugTargetRequestedCallback.accept(agent);
    } else {
      setCurrentDebugTarget(agent);
    }
  }

  private boolean hasAliveAgents() {
    return agentSnapshot().stream().anyMatch(Agent::isAlive);
  }

  /**
   * Refresh agent state rendered in the tree and toolbar. Runtime event handlers can call this when
   * an {@link Agent} reports a lifecycle change.
   */
  public void refreshAgentStates() {
    Platform.runLater(
        () -> {
          if (treeView != null && treeView.getRoot() != null) {
            treeView.getRoot().getChildren().stream()
                .filter(item -> item.getValue() instanceof AgentGroup)
                .findFirst()
                .ifPresent(
                    group -> {
                      group.getChildren().clear();
                      populateAgentItems(group);
                    });
            treeView.refresh();
          }
          updateAgentActionButtons(sourceIsValid());
        });
  }

  private boolean sourceIsValid() {
    return behavior != null
        && !stale
        && behavior.getNotifications().stream()
            .noneMatch(
                notification ->
                    notification.getLevel().severity >= Notification.Level.Error.severity);
  }

  private void updateAgentActionButtons(boolean sourceIsValid) {
    var canRun = sourceIsValid && compilationSuccessful && getLocalRuntime().isPresent();
    run.enabled(canRun);
    run.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
    if (canRun) {
      run.getStyleClass().add(Styles.SUCCESS);
      run.setTextFill(Color.GREEN);
    } else {
      run.setTextFill(Color.GRAY);
    }

    debug.enabled(canRun);
    debug.getStyleClass().removeAll(Styles.SUCCESS, Styles.ACCENT);
    if (canRun) {
      debug.getStyleClass().add(Styles.SUCCESS);
      debug.setTextFill(Color.GREEN);
    } else {
      debug.setTextFill(Color.GRAY);
    }

    var canStop = getLocalRuntime().isPresent() && hasAliveAgents();
    stop.enabled(canStop);
    stop.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
    if (canStop) {
      stop.getStyleClass().add(Styles.DANGER);
      stop.setTextFill(Color.DARKRED);
    } else {
      stop.setTextFill(Color.GRAY);
    }

    if (statusLabel != null) {
      var running = agentSnapshot().stream().filter(Agent::isAlive).count();
      statusLabel.setText(running == 0 ? "Stopped" : running + " agent(s) running");
    }

    publish.enabled(
        sourceIsValid && compilationSuccessful && getLocalResourcesService().isPresent());
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
        setGraphic(Theme.getGraphics(b.getDelegate()));
      } else if (item instanceof KActorsAction action) {
        setText(action.getUrn());
        setGraphic(new IconLabel(Theme.ACTION_ICON, 15, Theme.FOREGROUND_COLOR));
      } else if (item instanceof AgentGroup group) {
        setText(group.label());
        setGraphic(new IconLabel(Material2MZ.PEOPLE, 15, Theme.FOREGROUND_COLOR));
      } else if (item instanceof Agent agent) {
        configureRuntimeAgent(agent);
      } else if (item instanceof Observation observation) {
        configureAgent(observation);
      }
    }

    private void configureRuntimeAgent(Agent agent) {
      var viable = agent.isViable();
      var alive = agent.isAlive();
      var name = agent.getName();
      if (name == null || name.isBlank()) {
        name = agent.getUrn();
      }

      var behaviorIcon = new IconLabel(Theme.getIcon(behavior), 15, Theme.FOREGROUND_COLOR);
      var nameLabel = new Label(name == null ? "Agent" : name);
      var spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      var stateDot =
          new IconLabel(
              Material2AL.FIBER_MANUAL_RECORD,
              8,
              viable && alive ? Color.GREEN : viable ? Color.GRAY : Color.DARKRED);
      stateDot.setTooltip(
          new Tooltip(
              viable && alive
                  ? "Viable and running"
                  : viable ? "Viable and stopped" : "Agent is not viable"));
      var row = new HBox(6, behaviorIcon, nameLabel, spacer);
      if (debugAgents.contains(agent)) {
        var activeDebugTarget = currentDebugTarget == agent;
        var debugIcon =
            activeDebugTarget
                ? new IconLabel(CarbonIcons.DEBUG, 11, "-color-accent-fg")
                : new IconLabel(CarbonIcons.DEBUG, 11, Color.GRAY);
        if (activeDebugTarget) {
          debugIcon.getStyleClass().add(Styles.ACCENT);
        } else {
          debugIcon.setCursor(Cursor.HAND);
          debugIcon.setOnMouseClicked(
              event -> {
                requestDebugTarget(agent);
                event.consume();
              });
        }
        debugIcon.setTooltip(
            new Tooltip(
                activeDebugTarget
                    ? "Active debug target"
                    : "Debugging available; click to make active"));
        row.getChildren().add(debugIcon);
      }
      row.getChildren().add(stateDot);
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMaxWidth(Double.MAX_VALUE);
      row.setPrefWidth(Math.max(0, treeView.getWidth() - 42));
      setGraphic(row);

      var menu = new ContextMenu();
      if (alive) {
        var stopAgent =
            new MenuItem("Stop instance", new IconLabel(Material2MZ.STOP, 14, Color.DARKRED));
        stopAgent.setOnAction(
            event -> {
              agent.stop();
              refreshAgentStates();
            });
        menu.getItems().add(stopAgent);
      } else {
        var startAgent =
            new MenuItem("Start instance", new IconLabel(Material2MZ.PLAY_ARROW, 14, Color.GREEN));
        startAgent.setDisable(!viable);
        startAgent.setOnAction(
            event -> {
              agent.start();
              refreshAgentStates();
            });
        menu.getItems().add(startAgent);
      }
      setContextMenu(menu);
    }

    private void configureAgent(Observation observation) {
      String key = observation.getUrn();
      boolean associated = associatedAgents.getOrDefault(key, false);
      boolean running = runningAgents.getOrDefault(key, false);
      setText(observation.getName() == null ? observation.getUrn() : observation.getName());
      var dot =
          new IconLabel(
              Material2AL.FIBER_MANUAL_RECORD, 11, associated && running ? Color.GREEN : Color.RED);
      dot.setTooltip(
          new Tooltip(
              !associated
                  ? "No behavior associated"
                  : running ? "Running; click to stop" : "Stopped; click to start"));
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
      var apply =
          new MenuItem("Apply behavior", new IconLabel(Theme.BEHAVIOR_ICON, 14, Color.GREEN));
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
                new IconLabel(
                    running ? Material2MZ.STOP : Material2MZ.PLAY_ARROW,
                    14,
                    running ? Color.RED : Color.GREEN));
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

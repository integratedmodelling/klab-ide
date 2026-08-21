package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
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
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;

/** Editor for one standalone {@code .kactor} file. */
public class BehaviorEditor extends EditorPage<NavigableKActorsBehavior, Object> {

  private static final String JAVA_CODE_EDITOR_KEY = "java-code";
  private static final String AGENT_CONSOLE_EDITOR_KEY_PREFIX = "agent-console:";
  private static final String TEST_RESULTS_EDITOR_KEY_PREFIX = "test-results:";

  private IconButton debug;
  private IconButton compile;
  private IconButton run;
  private IconButton stop;
  private IconButton publish;
  private IconLabel typeLabel;
  private IconButton sourceCode;
  private int nAgents;

  private record AgentGroup(String label) {}

  private final Path file;
  private final Consumer<Path> savedCallback;
  private final Consumer<Ikon> behaviorIconChangedCallback;
  private final Consumer<Agent> debugAgentAvailableCallback;
  private final Consumer<Agent> debugTargetRequestedCallback;
  private final Consumer<Agent> agentStoppedCallback;
  private final Object sourceEditorAsset;
  private final ManagedBehaviorMirrors mirrors;
  private ManagedBehaviorMirrors.Origin managedOrigin;
  private final Map<String, Boolean> runningAgents = new HashMap<>();
  private final Map<String, Boolean> associatedAgents = new HashMap<>();
  private final Map<Node, LspDocumentSession> lspSessions = new IdentityHashMap<>();
  private NavigableKActorsBehavior behavior;
  private IDEContextScope contextScope;
  private TreeView<Object> treeView;
  private SplitPane browsingSplitPane;
  private AgentDocumentationView agentDocumentationView;
  private IconButton agentDocumentationToggle;
  private MonacoEditorView monacoEditor;
  private MonacoEditorView javaCodeEditor;
  private Tab javaCodeTab;
  private String displayedJavaCode;
  private IconLabel notificationStatusDot;
  private Label notificationSummaryLabel;
  private Label statusLabel;
  private Popup notificationPopup;
  private List<Notification> currentNotifications = List.of();
  private String documentUri;
  private boolean stale = false;
  private boolean compilationSuccessful;
  private final Set<Agent> agents = Collections.synchronizedSet(new LinkedHashSet<>());
  private final Set<Agent> debugAgents = Collections.synchronizedSet(new LinkedHashSet<>());
  private final Map<String, AgentConsoleView> agentConsoles = new LinkedHashMap<>();
  private final Map<String, TestCaseResultsView> testCaseResults = new ConcurrentHashMap<>();
  private final Map<String, AutoCloseable> testCaseSubscriptions = new ConcurrentHashMap<>();
  private final Map<String, PauseTransition> testCaseStartTimeouts = new ConcurrentHashMap<>();
  private final Timeline agentStatusRefresh =
      new Timeline(new KeyFrame(Duration.seconds(1), event -> removeStoppedAgents()));
  private AgentDebuggerView debuggerView;
  private Agent currentDebugTarget;

  public BehaviorEditor(
      Path file,
      KActorsBehavior asset,
      Consumer<Path> savedCallback,
      Consumer<Ikon> behaviorIconChangedCallback,
      Consumer<Agent> debugAgentAvailableCallback,
      Consumer<Agent> debugTargetRequestedCallback,
      Consumer<Agent> agentStoppedCallback,
      ManagedBehaviorMirrors mirrors,
      ManagedBehaviorMirrors.Origin managedOrigin) {
    super(asset == null ? null : new NavigableKActorsBehavior(asset, null));
    this.file = file.toAbsolutePath().normalize();
    this.sourceEditorAsset = this.file.getFileName();
    this.savedCallback = savedCallback;
    this.behaviorIconChangedCallback = behaviorIconChangedCallback;
    this.debugAgentAvailableCallback = debugAgentAvailableCallback;
    this.debugTargetRequestedCallback = debugTargetRequestedCallback;
    this.agentStoppedCallback = agentStoppedCallback;
    this.mirrors = mirrors;
    this.managedOrigin = managedOrigin;
    this.behavior = getEditedAsset();
    this.currentNotifications =
        notificationSnapshot(behavior == null ? null : behavior.getNotifications());
    this.stale = behavior == null;
  }

  public Path getFile() {
    return file;
  }

  public KActorsBehavior.Type getBehaviorType() {
    return behavior == null ? null : behavior.getBehaviorType();
  }

  boolean hasUnsavedSourceChanges() {
    return monacoEditor != null && monacoEditor.isDirty();
  }

  @Override
  protected void showContent() {
    super.showContent();
    Platform.runLater(
        () -> {
          edit(sourceEditorAsset);
          updateSourceEditorGraphic();
          if (behavior != null && getLocalRuntime().isPresent()) {
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
    if (!Objects.equals(asset, sourceEditorAsset)) return null;

    documentUri = file.toUri().toString();
    String source = readSource();
    String languageId = KlabLanguage.K_ACTORS.languageId();
    String theme = Theme.CURRENT_THEME.isDark() ? "vs-dark" : "vs";
    var lsp = KlabLspService.getInstance();
    boolean lspAvailable =
        lsp.ensureInitialized(
            KlabIDEController.instance().getLanguageServer(), KlabIDEController.instance().user());

    monacoEditor = new MonacoEditorView(documentUri, this::save);
    monacoEditor.runAfterEditorRendered(
        () -> monacoEditor.markNotifications(currentNotifications, false));
    monacoEditor.loadEditor(source, languageId, theme);
    if (lspAvailable) {
      var session = new LspDocumentSession(monacoEditor, languageId, source);

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

    this.typeLabel =
        new IconLabel(
            behavior == null ? Theme.APPLICATION_VIEW_ICON : Theme.getIcon(behavior),
            20,
            "-color-fg-muted");
    typeLabel.setTooltip(new Tooltip(file.toString()));
    var location =
        new Label(
            managedOrigin == null
                ? file.getFileName().toString()
                : managedOrigin.projectUrn() + " / " + managedOrigin.behaviorUrn(),
            null);
    location.setTooltip(
        new Tooltip(
            managedOrigin == null
                ? file.toString()
                : "Managed mirror: " + file + System.lineSeparator() + managedOrigin.serviceId()));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    this.debug =
        icon(
            CarbonIcons.DEBUG,
            "Compile and run a new agent in debug mode",
            false,
            false,
            this::doDebug);
    this.compile =
        icon(
            CarbonIcons.CHECKMARK_OUTLINE_WARNING,
            "Automatically compile and check for logical errors after saving",
            false,
            true,
            this::doCompile);
    this.sourceCode =
        icon(
            MaterialDesignL.LANGUAGE_JAVA,
            "Display Java source code after compilation",
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
            managedOrigin == null ? MaterialDesign.MDI_CLOUD_UPLOAD : MaterialDesign.MDI_CLOUD_SYNC,
            managedOrigin == null
                ? "Publish to a local workspace"
                : "Publish changes to " + managedOrigin.projectUrn(),
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
    return compileBehavior(debugging, testing, false);
  }

  private Agent compileBehavior(boolean debugging, boolean testing, boolean deferStart) {

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
    if (deferStart) {
      options.add(RuntimeAgent.CompilationOptions.DO_NOT_START);
    }

    // FIXME submit this to a single-threaded executor!
    var agent =
        localRuntime
            .get()
            .createAgent(
                this.behavior.getDelegate(),
                chooseNextName(this.behavior, testing),
                options,
                KlabIDEController.instance().user());

    if (agent != null) {
      var markerNotifications =
          mergeMarkerNotifications(agent.getNotifications(), behavior.getNotifications());
      monacoEditor.runAfterEditorRendered(
          () -> {
            monacoEditor.markNotifications(markerNotifications, true);
          });
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

  private String chooseNextName(KActorsBehavior behavior, boolean testing) {
    return Utils.Strings.capitalize(behavior.getUrn() + (testing ? "" : (" " + (++nAgents))));
  }

  private void updateBehaviorIcon(Collection<Notification> notifications) {
    refreshBehaviorIconType();

    var style = notificationStyle(notifications);
    var color = notificationColor(style);
    typeLabel.getStyleClass().removeAll(Styles.DANGER, Styles.WARNING, Styles.SUCCESS);
    typeLabel.getStyleClass().add(style);
    typeLabel.setStyle("-fx-text-fill: " + notificationCssColor(style) + ";");
    setCurrentNotifications(notifications, style, color);
    compilationSuccessful = Styles.SUCCESS.equals(style);
  }

  private String notificationStyle(Collection<Notification> notifications) {
    var style = Styles.SUCCESS;
    if (notifications != null) {
      for (var notification : notifications) {
        if (notification.getLevel().severity >= Notification.Level.Error.severity) {
          return Styles.DANGER;
        }
        if (notification.getLevel().severity >= Notification.Level.Warning.severity) {
          style = Styles.WARNING;
        }
      }
    }
    return style;
  }

  private Color notificationColor(String style) {
    if (Styles.DANGER.equals(style)) {
      return Color.RED;
    }
    if (Styles.WARNING.equals(style)) {
      return Color.GOLDENROD;
    }
    return Color.GREEN;
  }

  private String notificationCssColor(String style) {
    if (Styles.DANGER.equals(style)) {
      return "-color-danger-fg";
    }
    if (Styles.WARNING.equals(style)) {
      return "-color-warning-fg";
    }
    return "-color-success-fg";
  }

  private void refreshBehaviorIconType() {
    if (behavior == null) {
      updateSourceEditorGraphic();
      return;
    }
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

  private String readSource() {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      KlabIDEController.instance()
          .handleNotification(Notification.error("Error reading behavior " + file, e));
      return "";
    }
  }

  private void updateSourceEditorGraphic() {
    var icon = behavior == null ? Theme.APPLICATION_VIEW_ICON : Theme.getIcon(behavior);
    var color = behavior == null ? "-color-danger-fg" : "-color-fg-muted";
    if (typeLabel != null) {
      typeLabel.setGraphic(null);
      typeLabel.setIcon(icon, 20);
      typeLabel.getStyleClass().removeAll(Styles.DANGER, Styles.WARNING, Styles.SUCCESS);
      typeLabel.setStyle("-fx-text-fill: " + color + ";");
      if (behavior == null) {
        typeLabel.getStyleClass().add(Styles.DANGER);
      }
    }
    setEditorGraphic(sourceEditorAsset, new IconLabel(icon, 16, color));
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

  private Optional<ResourcesService> getManagedResourcesService() {
    if (managedOrigin == null) return Optional.empty();
    return KlabIDEController.instance().user().getServices(ResourcesService.class).stream()
        .filter(service -> managedOrigin.serviceId().equals(service.serviceId()))
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

    boolean testCase = behavior.getBehaviorType() == KActorsBehavior.Type.UNITTEST;
    boolean finiteBehavior =
        testCase || behavior.getBehaviorType() == KActorsBehavior.Type.SCRIPT;
    var agent = compileBehavior(debugging, testing, finiteBehavior);
    if (agent == null || !compilationSuccessful || !agent.isViable()) {
      return reportLaunchFailure(agent, debugging, null);
    }
    if (testCase && !registerTestCase(agent)) {
      return reportLaunchFailure(agent, debugging, null);
    }
    if (finiteBehavior) {
      // A finite agent may complete before start() returns. Attach every observer first so console,
      // test and debugger messages cannot be lost between the start request and UI registration.
      showAgentConsole(agent);
      if (debugging) {
        registerDebugSession(agent);
      }
      if (!agent.start()) {
        closeTestCaseSubscription(agent.getUrn());
        removeAgentConsole(agent);
        if (debugging) {
          discardDebugSession(agent);
        }
        return reportLaunchFailure(agent, debugging, null);
      }
    }
    // Ordinary agents are started by RuntimeService. Finite agents are started above, after every
    // observer is attached. A stopped handle here is therefore a finite agent that completed.
    if (!agent.isAlive()) {
      disconnectAgent(agent);
      refreshAgentStates();
      return true;
    }
    if (debugging && !finiteBehavior) {
      registerDebugSession(agent);
    }
    if (agent.isViable() && agent.isAlive()) {
      agents.add(agent);
      agentStatusRefresh.setCycleCount(Timeline.INDEFINITE);
      agentStatusRefresh.play();
      if (!finiteBehavior) {
        showAgentConsole(agent);
      }
      refreshAgentStates();
      return true;
    }
    if (debugging) {
      agentStopped(agent);
    }
    return reportLaunchFailure(agent, debugging, null);
  }

  private boolean registerTestCase(Agent agent) {
    if (!(agent instanceof AgentImpl clientAgent) || agent.getUrn() == null) {
      KlabIDEController.instance()
          .handleNotification(
              Notification.error("The runtime did not return a message-capable test agent"));
      return false;
    }
    var view = new TestCaseResultsView();
    testCaseResults.put(agent.getUrn(), view);
    var key = TEST_RESULTS_EDITOR_KEY_PREFIX + agent.getUrn();
    var title =
        agent.getName() == null || agent.getName().isBlank() ? "Test results" : agent.getName();
    var tab = showAuxiliaryEditor(key, "Tests — " + title, view);
    if (!clientAgent.isMessagingConnected()) {
      var detail =
          "The runtime could not establish the test agent communication channel. "
              + "Restart the local runtime after rebuilding klab-services.";
      view.fail(title, detail);
      KlabIDEController.instance().handleNotification(Notification.error(detail));
      return false;
    }
    if (!clientAgent.isStartDeferred()) {
      var detail =
          "The runtime executed the test before the results listener was connected. "
              + "Restart the local runtime so it uses the deferred test-start protocol.";
      view.fail(title, detail);
      KlabIDEController.instance().handleNotification(Notification.error(detail));
      return false;
    }
    var subscription = clientAgent.addMessageListener(message -> handleTestMessage(agent, message));
    testCaseSubscriptions.put(agent.getUrn(), subscription);
    var startTimeout = new PauseTransition(Duration.seconds(5));
    startTimeout.setOnFinished(
        event -> {
          if (view.getDomainObject() == null) {
            var detail =
                "The runtime accepted the test start request but sent no lifecycle messages.";
            view.fail(title, detail);
            clientAgent.setAlive(false);
            agentStopped(agent);
            KlabIDEController.instance().handleNotification(Notification.error(detail));
          }
          testCaseStartTimeouts.remove(agent.getUrn());
        });
    testCaseStartTimeouts.put(agent.getUrn(), startTimeout);
    startTimeout.play();
    tab.setOnCloseRequest(
        event -> {
          testCaseResults.remove(agent.getUrn());
          closeTestCaseSubscription(agent.getUrn());
        });
    return true;
  }

  private void handleTestMessage(Agent agent, Message message) {
    if (message == null || message.getMessageType() != Message.MessageType.CustomAgentMessage) {
      return;
    }
    var customMessage = message.getPayload(RuntimeAgent.CustomMessage.class);
    if (customMessage == null || customMessage.type() == null) {
      return;
    }
    var type = testMessageType(customMessage.type().getValue());
    if (type == null || !(customMessage.payload() instanceof DomainObject payload)) {
      return;
    }
    var view = testCaseResults.get(agent.getUrn());
    var startTimeout = testCaseStartTimeouts.remove(agent.getUrn());
    if (startTimeout != null) {
      Platform.runLater(startTimeout::stop);
    }
    if (view != null) {
      view.accept(type, payload);
    }
    if (type == RuntimeAgent.TestMessageType.TESTCASE_FINISHED) {
      closeTestCaseSubscription(agent.getUrn());
    }
  }

  static RuntimeAgent.TestMessageType testMessageType(String messageClass) {
    if (messageClass == null) {
      return null;
    }
    return Arrays.stream(RuntimeAgent.TestMessageType.values())
        .filter(type -> type.messageClass().equals(messageClass))
        .findFirst()
        .orElse(null);
  }

  private void closeTestCaseSubscription(String agentUrn) {
    var startTimeout = testCaseStartTimeouts.remove(agentUrn);
    if (startTimeout != null) {
      startTimeout.stop();
    }
    var subscription = testCaseSubscriptions.remove(agentUrn);
    if (subscription != null) {
      try {
        subscription.close();
      } catch (Exception failure) {
        Logging.INSTANCE.warn("Cannot detach test-case listener: " + failure.getMessage());
      }
    }
  }

  private void registerDebugSession(Agent agent) {
    debugAgents.add(agent);
    if (debuggerView != null) {
      debuggerView.registerAgent(agent);
    }
    if (debugAgentAvailableCallback != null) {
      debugAgentAvailableCallback.accept(agent);
    } else {
      setCurrentDebugTarget(agent);
    }
  }

  private void discardDebugSession(Agent agent) {
    debugAgents.remove(agent);
    if (debuggerView != null) {
      debuggerView.unregisterAgent(agent);
    }
    if (currentDebugTarget == agent) {
      currentDebugTarget = null;
    }
    if (agentStoppedCallback != null) {
      agentStoppedCallback.accept(agent);
    }
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
      stopped &= stopAndRemoveAgent(agent);
    }
    return stopped;
  }

  private boolean doPublish() {
    if (managedOrigin != null) {
      return updateManagedBehavior();
    }
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

  private boolean updateManagedBehavior() {
    var managedService = getManagedResourcesService();
    if (!compilationSuccessful || behavior == null || managedService.isEmpty()) return false;
    if (!(behavior.getDelegate() instanceof KActorsBehaviorImpl parsedBehavior)) return false;

    var update = new KActorsBehaviorImpl();
    update.setUrn(managedOrigin.behaviorUrn());
    update.setBehaviorType(parsedBehavior.getBehaviorType());
    update.setProjectName(managedOrigin.projectUrn());
    update.setSourceCode(parsedBehavior.getSourceCode());
    var results =
        managedService
            .get()
            .submit(
                update,
                ResourcesService.SubmissionMode.CREATE_OR_UPDATE,
                KlabIDEController.instance().user());
    var accepted = KlabIDEController.instance().handleResultSets(results);
    if (accepted) {
      try {
        managedOrigin =
            mirrors.markSynchronized(
                file, parsedBehavior.getUrn(), Files.readString(file, StandardCharsets.UTF_8));
        if (savedCallback != null) savedCallback.accept(file);
      } catch (IOException e) {
        KlabIDEController.instance()
            .handleNotification(
                Notification.warning(
                    "Behavior was updated but its local mirror metadata could not be saved", e));
      }
    }
    return accepted;
  }

  private Optional<Project> showProjectSelectionDialog(ResourcesService service) {
    var user = KlabIDEController.instance().user();
    var root = new TreeItem<Object>();
    for (var workspaceName : service.capabilities(user).getWorkspaceNames()) {
      var workspace = service.retrieve(workspaceName, Workspace.class, user);
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
                  setGraphic(new IconLabel(Theme.WORKSPACE_ICON, 16, "-color-fg-default"));
                } else if (item instanceof Project project) {
                  setText(project.getUrn());
                  setGraphic(new IconLabel(Theme.PROJECT_ICON, 16, "-color-fg-default"));
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
    if (behavior.getDelegate() instanceof KActorsBehaviorImpl behavior1) {
      // override whatever project we were in, or set it for a new project
      behavior1.setProjectName(project.getUrn());
      var results =
          service.submit(
              behavior1,
              ResourcesService.SubmissionMode.CREATE_OR_UPDATE,
              KlabIDEController.instance().user());
      KlabIDEController.instance().handleResultSets(results);
    }
  }

  private IconButton icon(
      org.kordamp.ikonli.Ikon icon,
      String tooltip,
      boolean enabled,
      boolean toggle,
      Callable<Boolean> action) {
    return toggle
        ? IconButton.toggle(icon, 18, "-color-fg-default", "-color-fg-muted", action)
            .tooltip(tooltip)
            .enabled(enabled)
        : IconButton.of(icon, 18, "-color-fg-default", "-color-fg-muted", action)
            .tooltip(tooltip)
            .enabled(enabled);
  }

  private Node createStatusBar() {
    notificationStatusDot =
        new IconLabel(Material2AL.FIBER_MANUAL_RECORD, 9, Theme.FOREGROUND_COLOR);
    notificationSummaryLabel = new Label();
    notificationSummaryLabel.setCursor(Cursor.HAND);
    notificationSummaryLabel.setStyle("-fx-font-size: 10px;");
    notificationSummaryLabel.setOnMouseClicked(event -> toggleNotificationPopup());
    var notificationStatus = new HBox(5, notificationStatusDot, notificationSummaryLabel);
    notificationStatus.setAlignment(Pos.CENTER_LEFT);

    statusLabel = new Label("Stopped", new IconLabel(Material2MZ.STOP, 12, Color.GRAY));
    statusLabel.setDisable(true);
    statusLabel.setTooltip(new Tooltip("Behavior execution is not active"));
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var bar = new HBox(notificationStatus, spacer, statusLabel);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(3, 8, 3, 8));
    bar.setStyle("-fx-background-color: -color-neutral-muted;");
    updateNotificationStatus(currentNotifications, null, Color.GRAY);
    return bar;
  }

  private List<Notification> notificationSnapshot(Collection<Notification> notifications) {
    return notifications == null ? List.of() : List.copyOf(notifications);
  }

  private List<Notification> mergeMarkerNotifications(
      Collection<Notification> first, Collection<Notification> second) {
    var merged = new ArrayList<Notification>();
    var keys = new HashSet<String>();
    for (var notification : notificationSnapshot(first)) {
      if (keys.add(markerKey(notification))) {
        merged.add(notification);
      }
    }
    for (var notification : notificationSnapshot(second)) {
      if (keys.add(markerKey(notification))) {
        merged.add(notification);
      }
    }
    return merged;
  }

  private String markerKey(Notification notification) {
    var context = notification.getLexicalContext();
    if (context == null) {
      return "no-context:" + System.identityHashCode(notification);
    }
    return context.getOffsetInDocument()
        + ":"
        + context.getLength()
        + ":"
        + notification.getLevel()
        + ":"
        + notification.getMessage();
  }

  private void setCurrentNotifications(
      Collection<Notification> notifications, String style, Color color) {
    currentNotifications = notificationSnapshot(notifications);
    updateNotificationStatus(currentNotifications, style, color);
    if (notificationPopup != null && notificationPopup.isShowing()) {
      notificationPopup.hide();
    }
  }

  private void updateNotificationStatus(
      Collection<Notification> notifications, String style, Color color) {
    if (notificationStatusDot == null || notificationSummaryLabel == null) {
      return;
    }
    long errors = 0;
    long warnings = 0;
    long info = 0;
    for (var notification : notificationSnapshot(notifications)) {
      if (notification.getLevel().severity >= Notification.Level.Error.severity) {
        errors++;
      } else if (notification.getLevel().severity >= Notification.Level.Warning.severity) {
        warnings++;
      } else {
        info++;
      }
    }
    notificationSummaryLabel.setText(
        errors
            + (errors == 1 ? " error, " : " errors, ")
            + warnings
            + (warnings == 1 ? " warning, " : " warnings, ")
            + info
            + " info");
    notificationStatusDot.getStyleClass().removeAll(Styles.DANGER, Styles.WARNING, Styles.SUCCESS);
    if (style != null) {
      notificationStatusDot.getStyleClass().add(style);
    }
    notificationStatusDot.setTextFill(color);
  }

  private void toggleNotificationPopup() {
    if (notificationPopup != null && notificationPopup.isShowing()) {
      notificationPopup.hide();
      return;
    }
    if (notificationSummaryLabel == null || notificationSummaryLabel.getScene() == null) {
      return;
    }

    var messages = new VBox(4);
    messages.setPadding(new Insets(6));
    if (currentNotifications.isEmpty()) {
      var empty = new Label("No notifications");
      empty.setStyle("-fx-font-size: 10px; -fx-padding: 6 8;");
      messages.getChildren().add(empty);
    } else {
      for (var notification : currentNotifications) {
        messages.getChildren().add(createNotificationMessage(notification));
      }
    }

    var scroll = new ScrollPane(messages);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setPrefViewportWidth(460);
    scroll.setPrefViewportHeight(Math.min(240, Math.max(34, currentNotifications.size() * 42)));
    scroll.setMaxHeight(250);
    scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

    var popupContent = new VBox(scroll);
    popupContent.setStyle(
        "-fx-background-color: -color-bg-default;"
            + " -fx-border-color: -color-border-default;"
            + " -fx-border-radius: 4; -fx-background-radius: 4;");
    notificationPopup = new Popup();
    notificationPopup.setAutoHide(true);
    notificationPopup.setHideOnEscape(true);
    notificationPopup.setConsumeAutoHidingEvents(false);
    notificationPopup.getContent().add(popupContent);

    Bounds bounds =
        notificationSummaryLabel.localToScreen(notificationSummaryLabel.getBoundsInLocal());
    if (bounds == null) {
      return;
    }
    notificationPopup.show(notificationSummaryLabel, bounds.getMinX(), bounds.getMinY());
    notificationPopup.setY(bounds.getMinY() - notificationPopup.getHeight() - 4);
  }

  private Node createNotificationMessage(Notification notification) {
    var message = new Label(notification.getMessage());
    message.setWrapText(true);
    message.setMaxWidth(Double.MAX_VALUE);
    message.setMinHeight(Region.USE_PREF_SIZE);
    message
        .getStyleClass()
        .add(
            switch (notification.getLevel()) {
              case Error, SystemError -> Styles.DANGER;
              case Warning -> Styles.WARNING;
              case Debug, Info -> Styles.ACCENT;
            });
    var background =
        switch (notification.getLevel()) {
          case Error, SystemError -> "-color-danger-subtle";
          case Warning -> "-color-warning-subtle";
          case Debug, Info -> "-color-accent-subtle";
        };
    message.setStyle(
        "-fx-font-size: 10px; -fx-padding: 6 8; -fx-background-radius: 4;"
            + " -fx-background-color: "
            + background
            + ";");
    return message;
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
            this.sourceCode.enabled(false);
            this.run.enabled(false);
            this.stop.enabled(false);
            updateSourceEditorGraphic();
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
            // compile and run depend on errors
            var sourceIsValid = errors == 0 && !this.stale;
            this.compile.enabled(sourceIsValid);
            this.sourceCode.enabled(sourceIsValid);
            updateAgentActionButtons(sourceIsValid);
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
    var statusUpdateQueued = false;
    try {
      Files.writeString(file, contents, StandardCharsets.UTF_8);
      var parsed =
          KlabIDEController.instance()
              .user()
              .getService(ResourcesService.class)
              .parseAsset(
                  file.toUri().toURL(), KActorsBehavior.class, KlabIDEController.instance().user());

      this.stale = false;
      if (parsed == null) {
        this.behavior = null;
        setEditedAsset(null);
        this.stale = true;
        this.compilationSuccessful = false;
        resetCompilationVisualStatus(List.of());
        updateSourceEditorGraphic();
        if (treeView != null) treeView.setRoot(createTreeRoot());
        if (savedCallback != null) savedCallback.accept(file);
        updateStatus();
        return;
      }

      this.behavior = new NavigableKActorsBehavior(parsed, null);
      setEditedAsset(this.behavior);
      monacoEditor.markNotifications(behavior.getNotifications(), true);
      resetCompilationVisualStatus(behavior.getNotifications());
      for (var notification : behavior.getNotifications()) {
        // TODO send to editor to show. Needs a notification method that only consumes those with
        //  lexical context
        Logging.INSTANCE.notifications(notification);
        if (notification.getLevel().severity >= Notification.Level.Error.severity) {
          this.stale = true;
        }
      }
      var compiled = this.compile.isToggled();
      if (compiled) {
        statusUpdateQueued = true;
        doCompile();
      }
      if (treeView != null) treeView.setRoot(createTreeRoot());
      if (savedCallback != null) savedCallback.accept(file);

    } catch (IOException e) {
      KlabIDEController.instance().handleNotification(Notification.error("Error song behavior", e));
      this.stale = true;
    }

    if (!statusUpdateQueued) {
      updateStatus();
    }
  }

  @Override
  protected void onSingleClickItemSelection(Object value) {
    if (value instanceof KActorsAction action && monacoEditor != null) {
      monacoEditor.setCursorPosition(action.getOffsetInDocument());
    } else if (value instanceof Agent agent) {
      if (debugAgents.contains(agent)) {
        requestDebugTarget(agent);
      }
      showAgentConsole(agent);
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

  @Override
  protected Node createTopMenu() {
    agentDocumentationToggle =
        IconButton.toggle(
                Material2AL.DESCRIPTION,
                16,
                "-color-accent-fg",
                "-color-fg-muted",
                this::toggleAgentDocumentation)
            .tooltip("Show agent and verb documentation");
    var toolbar = new HBox(agentDocumentationToggle);
    toolbar.setAlignment(Pos.CENTER_RIGHT);
    toolbar.setPadding(new Insets(3, 6, 3, 6));
    toolbar.getStyleClass().add(Styles.DENSE);
    return toolbar;
  }

  private Boolean toggleAgentDocumentation() {
    if (browsingSplitPane == null || agentDocumentationView == null) {
      return false;
    }
    if (agentDocumentationToggle.isToggled()) {
      if (!browsingSplitPane.getItems().contains(agentDocumentationView)) {
        browsingSplitPane.getItems().add(agentDocumentationView);
        browsingSplitPane.setDividerPositions(0.58);
      }
    } else {
      browsingSplitPane.getItems().remove(agentDocumentationView);
    }
    return true;
  }

  private TreeItem<Object> createTreeRoot() {
    var root = new TreeItem<Object>();
    if (behavior != null) {
      var behaviorItem = new TreeItem<Object>(behavior);
      behaviorItem.setExpanded(true);
      for (var action : behavior.getStatements())
        behaviorItem.getChildren().add(new TreeItem<>(action));
      root.getChildren().add(behaviorItem);
    }

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
    if (debuggerView != null) {
      debuggerView.focusAgent(currentDebugTarget);
    }
    if (currentDebugTarget != null) {
      showAgentConsole(currentDebugTarget);
    }
    refreshAgentStates();
  }

  @Override
  public void close() {
    if (notificationPopup != null) {
      notificationPopup.hide();
      notificationPopup = null;
    }
    agentConsoles.values().forEach(AgentConsoleView::close);
    agentConsoles.clear();
    for (var agentUrn : List.copyOf(testCaseSubscriptions.keySet())) {
      closeTestCaseSubscription(agentUrn);
    }
    testCaseResults.clear();
    if (debuggerView != null) {
      debuggerView.close();
    }
    var connectedAgents = Collections.newSetFromMap(new IdentityHashMap<Agent, Boolean>());
    connectedAgents.addAll(agentSnapshot());
    connectedAgents.addAll(getDebugAgents());
    for (var agent : connectedAgents) {
      try {
        if (agent.isAlive()) {
          agent.stop();
        }
      } catch (Throwable ignored) {
        // The editor is closing; transport teardown below remains mandatory.
      }
      disconnectAgent(agent);
    }
    agentStatusRefresh.stop();
    super.close();
  }

  private void resetCompilationVisualStatus(Collection<Notification> notifications) {
    compilationSuccessful = false;
    if (typeLabel != null) {
      typeLabel.getStyleClass().removeAll(Styles.DANGER, Styles.WARNING, Styles.SUCCESS);
      typeLabel.setTextFill(Color.GRAY);
    }
    setCurrentNotifications(notifications, null, Color.GRAY);
  }

  private void requestDebugTarget(Agent agent) {
    if (debugTargetRequestedCallback != null) {
      debugTargetRequestedCallback.accept(agent);
    } else {
      setCurrentDebugTarget(agent);
    }
  }

  private void showAgentConsole(Agent agent) {
    if (agent == null || agent.getUrn() == null) {
      return;
    }
    var key = AGENT_CONSOLE_EDITOR_KEY_PREFIX + agent.getUrn();
    var console =
        agentConsoles.computeIfAbsent(
            agent.getUrn(), ignored -> new AgentConsoleView(agent, this::agentStopped));
    var name = agent.getName();
    var tab =
        showAuxiliaryEditor(
            key, "Console — " + (name == null || name.isBlank() ? agent.getUrn() : name), console);
    if (tab.getTabPane() != null) {
      tab.getTabPane().getSelectionModel().select(tab);
    }
    tab.setOnCloseRequest(
        event -> {
          var removed = agentConsoles.remove(agent.getUrn());
          if (removed != null) {
            removed.close();
          }
        });
    console.focusInput();
  }

  private boolean stopAndRemoveAgent(Agent agent) {
    try {
      if (!agent.isAlive()) {
        agentStopped(agent);
        return true;
      }
      // stop() acknowledges publication, not service-side termination. Keep the handle connected
      // until AgentStopped updates isAlive(), then the status refresh removes and disconnects it.
      return agent.stop();
    } catch (Throwable failure) {
      KlabIDEController.instance()
          .handleNotification(
              Notification.error("Unable to stop agent " + agent.getUrn(), failure));
      return false;
    }
  }

  private void removeAgentConsole(Agent agent) {
    if (agent == null || agent.getUrn() == null) {
      return;
    }
    var console = agentConsoles.remove(agent.getUrn());
    if (console != null) {
      console.close();
    }
    closeAuxiliaryEditor(AGENT_CONSOLE_EDITOR_KEY_PREFIX + agent.getUrn());
  }

  private boolean keepsAgentConsoleAfterStop() {
    return behavior != null
        && (behavior.getBehaviorType() == KActorsBehavior.Type.SCRIPT
            || behavior.getBehaviorType() == KActorsBehavior.Type.UNITTEST);
  }

  private boolean keepsDebugTranscriptAfterStop() {
    return keepsAgentConsoleAfterStop();
  }

  private void agentStopped(Agent agent) {
    if (!keepsAgentConsoleAfterStop()) {
      removeAgentConsole(agent);
    }
    boolean removed;
    synchronized (agents) {
      removed = agents.remove(agent);
    }
    boolean debugRemoved = false;
    if (!keepsDebugTranscriptAfterStop()) {
      synchronized (debugAgents) {
        debugRemoved = debugAgents.remove(agent);
      }
    }
    if (debugRemoved && debuggerView != null) {
      debuggerView.unregisterAgent(agent);
    }
    if (debugRemoved && currentDebugTarget == agent) {
      currentDebugTarget = null;
    }
    if (debugRemoved && agentStoppedCallback != null) {
      agentStoppedCallback.accept(agent);
    }
    if (removed || debugRemoved) {
      disconnectAgent(agent);
    }
    if (removed || debugRemoved) {
      refreshAgentStates();
    }
    if (agents.isEmpty()) {
      agentStatusRefresh.stop();
    }
  }

  private void disconnectAgent(Agent agent) {
    if (agent instanceof AgentImpl clientAgent) {
      clientAgent.disconnect();
    }
  }

  private void removeStoppedAgents() {
    for (var agent : agentSnapshot()) {
      try {
        if (!agent.isAlive()) {
          agentStopped(agent);
        }
      } catch (Throwable failure) {
        agentStopped(agent);
      }
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
        sourceIsValid
            && compilationSuccessful
            && (managedOrigin == null
                ? getLocalResourcesService().isPresent()
                : getManagedResourcesService().isPresent()));
  }

  void refreshManagedBehavior(KActorsBehavior updatedBehavior, boolean sourceChanged) {
    if (managedOrigin == null || updatedBehavior == null) return;
    this.behavior = new NavigableKActorsBehavior(updatedBehavior, null);
    setEditedAsset(this.behavior);
    this.currentNotifications = notificationSnapshot(updatedBehavior.getNotifications());
    if (sourceChanged && monacoEditor != null) {
      monacoEditor.setText(Objects.requireNonNullElse(updatedBehavior.getSourceCode(), ""));
    }
    if (monacoEditor != null) {
      monacoEditor.markNotifications(updatedBehavior.getNotifications(), true);
    }
    resetCompilationVisualStatus(updatedBehavior.getNotifications());
    updateSourceEditorGraphic();
    if (treeView != null) treeView.setRoot(createTreeRoot());
    updateStatus();
  }

  @Override
  protected Node createBrowsingContent(TreeView<Object> tree) {
    VBox.setVgrow(tree, Priority.ALWAYS);
    debuggerView = new AgentDebuggerView(this::agentStopped);
    var separator = new Separator();
    separator.visibleProperty().bind(debuggerView.visibleProperty());
    separator.managedProperty().bind(debuggerView.managedProperty());
    var behaviorBrowser = new VBox(tree, separator, debuggerView);
    agentDocumentationView = new AgentDocumentationView();
    agentDocumentationView.setMinHeight(120);
    agentDocumentationView.setPrefHeight(280);
    browsingSplitPane = new SplitPane(behaviorBrowser);
    browsingSplitPane.setOrientation(Orientation.VERTICAL);
    return browsingSplitPane;
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
        setGraphic(new IconLabel(Theme.ACTION_ICON, 15, "-color-fg-default"));
      } else if (item instanceof AgentGroup group) {
        setText(group.label());
        setGraphic(new IconLabel(Material2MZ.PEOPLE, 15, "-color-fg-default"));
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
      Logging.INSTANCE.info("name is " + name);
      if (name == null || name.isBlank()) {
        name = agent.getUrn();
      }

      var behaviorIcon =
          new IconLabel(
              behavior == null ? Theme.APPLICATION_VIEW_ICON : Theme.getIcon(behavior),
              15,
              "-color-fg-default");
      var nameLabel = new Label(name == null ? "Agent" : name);
      var spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      var stateDot =
          new IconLabel(
              Material2AL.FIBER_MANUAL_RECORD,
              8,
              viable && alive
                  ? "-color-success-fg"
                  : viable ? "-color-fg-muted" : "-color-danger-fg");
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
                : new IconLabel(CarbonIcons.DEBUG, 11, "-color-fg-muted");
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
        stopAgent.setOnAction(event -> stopAndRemoveAgent(agent));
        menu.getItems().add(stopAgent);
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
}

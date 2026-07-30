package org.integratedmodelling.klab.ide;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.integratedmodelling.common.commandline.KlabCommandLine;
import org.integratedmodelling.common.configuration.CommonConfiguration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.cli.CLI;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.*;
import org.integratedmodelling.klab.api.view.modeler.Modeler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.views.RuntimeView;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.RuntimeViewController;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;
import org.integratedmodelling.klab.api.view.modeler.visualization.Visualization;
import org.integratedmodelling.klab.ide.api.DigitalTwinReactor;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.components.*;
import org.integratedmodelling.klab.ide.components.cards.AssetViewComponent;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.integratedmodelling.klab.ide.utils.NodeUtils;
import org.integratedmodelling.klab.modeler.ModelerImpl;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

public class KlabIDEController implements UIView, ServicesView, RuntimeView, Modeler {

  private static Modeler modeler;

  private View currentView;
  private UserScope user;
  private boolean inspectorIsOn;
  private Set<View> neverSeen = EnumSet.of(View.RESOURCES, View.WORKSPACES, View.DIGITAL_TWINS);
  private static KlabIDEController _this;
  private final Map<String, IDEContextScope> contextMap = new ConcurrentHashMap<>();
  private Queue<DigitalTwinReactor> digitalTwinReactors = new ConcurrentLinkedQueue<>();
  private AtomicReference<Engine.Status> engineStatus = new AtomicReference<>();
  private Label infoLabel;
  private Label errorLabel;
  private Label warningLabel;
  private Label messageLabel;
  private Button toggleDigitalTwinButton;
  private AtomicInteger infoCount = new AtomicInteger(0);
  private AtomicInteger errorCount = new AtomicInteger(0);
  private AtomicInteger warningCount = new AtomicInteger(0);
  private PauseTransition currentPause;
  private IDEContextScope focalScope;
  private HBox digitalTwinBox;
  private Button digitalTwinButton;
  //  private Label digitalTwinLabel;
  private EditorPage<?, ?> currentEditorPage; // keep this to interact with the DT
  private Button dtResetButton;
  private Button dtSwitchButton;
  private MenuButton digitalTwinSwitcher;
  private IconLabel dbIcon;
  private IconLabel messIcon;
  private IconLabel langIcon;
  private final KlabCommandLine cli =
      new ModelerCommandLine(() -> focalScope == null ? modeler().engine().getOwner() : focalScope);
  private boolean knowledgeInitialized;
  private final AtomicBoolean localServiceActionRunning = new AtomicBoolean(false);

  private Map<KlabService, KlabService.ServiceStatus> serviceStatus = new ConcurrentHashMap<>() {};
  private ModalPane modalPane;
  private boolean modalEscHandlerInstalled;
  private final EventHandler<KeyEvent> escHandler =
      event -> {
        if (event.getCode() == KeyCode.ESCAPE) {
          removeModalOverlay();
          event.consume();
        }
      };

  public CLI getCLI() {
    return cli;
  }

  public <T, A> void digitalTwinPanelShown(
      EditorPage<A, T> atEditorPage, DigitalTwinControlPanel digitalTwinControlPanel) {
    digitalTwinPanelStateChanged(atEditorPage);
    //    digitalTwinLabel.getSelectionModel().select(digitalTwinControlPanel.getScope());
  }

  public <T, A> void digitalTwinPanelHidden(
      EditorPage<A, T> atEditorPage, DigitalTwinControlPanel digitalTwinControlPanel) {
    digitalTwinPanelStateChanged(atEditorPage);
    //      digitalTwinLabel.setText(digitalTwinControlPanel.getScope().getName());
  }

  public void digitalTwinPanelStateChanged(EditorPage<?, ?> editorPage) {
    refreshDigitalTwinControls();
  }

  public void setFocalEditor(EditorPage<?, ?> editorPage, boolean visible) {
    if (visible) {
      Logging.INSTANCE.info("Setting focal editor to " + editorPage.getEditedAsset());
      if (isSelectedEditorInCurrentView(editorPage)) {
        this.currentEditorPage = editorPage;
      }
    } else {
      Logging.INSTANCE.info("Removing focal editor for " + editorPage.getEditedAsset());
      if (this.currentEditorPage == editorPage) {
        this.currentEditorPage = getSelectedEditorInCurrentView();
      }
    }
    refreshDigitalTwinControls();
  }

  public LocalInstance getInstance(KlabService service) {
    if (Utils.URLs.isLocalHost(service.getUrl())) {
      var instance = engine().getServiceInstance(service.status().getServiceType());
      if (instance != null && instance.getStatus() == LocalInstance.Status.RUNNING) {
        return instance;
      }
    }
    return null;
  }

  /** The "circled" (current) view in the main area. */
  public enum View {
    NOTEBOOK,
    RESOURCES,
    WORKSPACES,
    DIGITAL_TWINS,
    APPLICATIONS,
    WORLDVIEW
  }

  @FXML BorderPane rootPane;
  @FXML VBox notificationArea;
  @FXML Button homeButton;
  @FXML Button workspacesButton;
  @FXML Button digitalTwinsButton;
  @FXML Button downloadButton;
  @FXML Button startButton;
  @FXML Button reasonerButton;
  @FXML Button resourcesButton;
  @FXML Button resolverButton;
  @FXML Button runtimeButton;
  @FXML Button settingsButton;
  @FXML Button inspectorButton;
  @FXML Button profileButton;
  @FXML Button resourcesManagerButton;
  @FXML Button sessionsButton;
  @FXML Button worldviewButton;
  @FXML HBox statusBar;
  @FXML NotebookViewer notebook;
  @FXML Pane mainArea;
  @FXML Pane inspectorArea;
  @FXML ImageView logo;
  @FXML HBox otherServices;

  private Button toggleRightSideButton;
  private final AtomicBoolean notificationsVisible = new AtomicBoolean(false);
  private Queue<Notification> notifications;
  private ServicesViewController servicesController;
  private RuntimeViewController runtimeController;
  private final Map<View, Button> viewButtons = new HashMap<>();

  private WorkspaceView workspaceView;
  private ResourcesView resourcesView;
  private DigitalTwinView digitalTwinView;
  private InspectorView inspectorView;
  private Stage detachedInspectorStage;
  private boolean inspectorDocked = true;
  private AgentView applicationView;
  private OntologyView ontologyView;

  public KlabIDEController() {
    _this = this;
  }

  public void setFocalScope(IDEContextScope focalScope, boolean isLocal) {
    if (!Platform.isFxApplicationThread()) {
      Platform.runLater(() -> setFocalScope(focalScope, isLocal));
      return;
    }
    if (focalScope == null && this.focalScope != null) {
      digitalTwinView.deselectDigitalTwin(this.focalScope);
      synchronized (this.digitalTwinReactors) {
        for (var reactor : this.digitalTwinReactors) {
          reactor.unsetDigitalTwin(this.focalScope);
        }
      }
    }
    this.focalScope = focalScope;
    synchronized (this.digitalTwinReactors) {
      for (var reactor : this.digitalTwinReactors) {
        reactor.setDigitalTwin(focalScope, focalScope != null);
      }
    }
    updateDigitalTwinChoices();
  }

  private void updateDigitalTwinChoices() {

    Logging.INSTANCE.info(
        "Setting focal scope to "
            + (focalScope == null ? "No digital twin" : focalScope.getName()));

    Platform.runLater(
        () -> {
          var contexts = new ArrayList<>(contextMap.values());
          contexts.sort(Comparator.comparing(c -> (focalScope != null && c == focalScope) ? 0 : 1));
          this.digitalTwinSwitcher.getItems().clear();
          this.digitalTwinSwitcher
              .getItems()
              .addAll(
                  contexts.stream()
                      .filter(c -> c != focalScope)
                      .map(
                          c -> {
                            // TODO use icon colors to reflect DT ownership/service
                            var item =
                                new MenuItem(
                                    c.getName(),
                                    new IconLabel(
                                        Theme.DIGITAL_TWINS_ICON, 16, Theme.FOREGROUND_COLOR));
                            item.setOnAction(actionEvent -> setFocalScope(c, false));
                            return item;
                          })
                      .toList());

          digitalTwinSwitcher.setText(
              focalScope == null ? "No digital twin" : focalScope.getName());
          dtResetButton.setDisable(focalScope == null);
          dtSwitchButton.setDisable(focalScope == null);
          digitalTwinSwitcher.setTooltip(
              new Tooltip(focalScope == null ? "No digital twin" : focalScope.getName())); // TODO
          digitalTwinSwitcher.setStyle("fx-font-weight: bold; -fx-text-fill: -fx-accent-color;");
          if (focalScope != null) {
            digitalTwinView.showDigitalTwin(focalScope);
          }
          refreshDigitalTwinControls();
        });
  }

  private void refreshDigitalTwinControls() {
    if (digitalTwinButton == null || dtResetButton == null || dtSwitchButton == null) {
      return;
    }

    Runnable update =
        () -> {
          currentEditorPage = getSelectedEditorInCurrentView();
          var panelReady =
              currentEditorPage != null && currentEditorPage.hasDigitalTwinControlPanel();
          var panelShown = panelReady && currentEditorPage.isDigitalTwinControlPanelShown();
          var hasFocalScope = focalScope != null;

          digitalTwinButton.setDisable(!hasFocalScope || !panelReady);
          digitalTwinButton.setGraphic(
              new IconLabel(
                  panelShown
                      ? FontAwesomeSolid.ARROW_CIRCLE_DOWN
                      : FontAwesomeSolid.ARROW_CIRCLE_UP,
                  14,
                  Color.DARKGREEN));
          digitalTwinButton.setTooltip(
              new Tooltip(
                  panelShown
                      ? "Hide Digital Twin Control Panel"
                      : "Show Digital Twin Control Panel"));

          dtResetButton.setDisable(!hasFocalScope);
          dtResetButton.setGraphic(new IconLabel(FontAwesomeSolid.TIMES_CIRCLE, 14, Color.DARKRED));
          dtSwitchButton.setDisable(!hasFocalScope);
        };

    if (Platform.isFxApplicationThread()) {
      update.run();
    } else {
      Platform.runLater(update);
    }
  }

  private EditorPage<?, ?> getSelectedEditorInCurrentView() {
    var currentUI = getViewNode(currentView);
    return currentUI instanceof BrowsablePage<?, ?> browsablePage
        ? browsablePage.getSelectedEditor()
        : null;
  }

  private boolean isSelectedEditorInCurrentView(EditorPage<?, ?> editorPage) {
    return editorPage != null && editorPage == getSelectedEditorInCurrentView();
  }

  private Node getViewNode(View view) {
    if (view == null) {
      return null;
    }
    return switch (view) {
      case NOTEBOOK -> notebook;
      case RESOURCES -> resourcesView;
      case DIGITAL_TWINS -> digitalTwinView;
      case WORKSPACES -> workspaceView;
      case APPLICATIONS -> applicationView;
      case WORLDVIEW -> ontologyView;
    };
  }

  public IDEContextScope getFocalScope() {
    return focalScope;
  }

  /**
   * These are the persistent reactor views, such as editors. They remove themselves and manage
   * their sub-viewers directly through IDEContextScope.
   *
   * @param reactor
   */
  public void registerDigitalTwinReactor(DigitalTwinReactor reactor) {
    this.digitalTwinReactors.add(reactor);
  }

  /**
   * These are the persistent reactor views, such as editors. They remove themselves and manage
   * their sub-viewers directly through IDEContextScope.
   *
   * @param reactor
   */
  public void unregisterDigitalTwinReactor(DigitalTwinReactor reactor) {
    this.digitalTwinReactors.remove(reactor);
  }

  /**
   * The language server instance. I/O will be bound to the editor's for LSP support.
   *
   * @return
   */
  public LocalInstance getLanguageServer() {
    return engine().getServiceInstance(KlabService.Type.LANGUAGE_SERVER);
  }

  /**
   * Request a scope peer, creating one if needed. This is called by {@link DigitalTwinViewer}s,
   * which pass themselves to get registered for event propagation.
   *
   * @param scope
   * @param viewer
   * @return
   */
  public synchronized IDEContextScope requireDigitalTwinPeer(
      ContextScope scope, DigitalTwinViewer viewer) {
    if (scope == null) {
      return null;
    }
    IDEContextScope ret = null;
    if (scope instanceof IDEContextScope ideContextScope) {
      ret = ideContextScope;
    } else if (scope instanceof ClientContextScope clientContextScope) {
      ret =
          contextMap.computeIfAbsent(scope.getId(), id -> new IDEContextScope(clientContextScope));
    } else {
      throw new IllegalArgumentException("Only ClientContextScope is supported.");
    }
    if (viewer != null) {
      ret.addViewer(viewer);
    }
    return ret;
  }

  public IDEContextScope getDigitalTwinPeer(String id) {
    return contextMap.get(id);
  }

  public void setDigitalTwinPeer(String id, IDEContextScope peer) {
    contextMap.put(id, peer);
  }

  public void removeDigitalTwinPeer(String id) {
    contextMap.remove(id);
  }

  public static KlabIDEController instance() {
    return _this;
  }

  private static Modeler modeler() {
    return modeler;
  }

  /**
   * Return whatever scope we have available, more specific first, or null.
   *
   * @return
   */
  public static UserScope scope() {
    if (_this != null) {
      return _this.getFocalScope() == null ? _this.user() : _this.getFocalScope();
    }
    return null;
  }

  private void createModeler() {

    modeler = new ModelerImpl(this);

    this.servicesController = modeler.viewController(ServicesViewController.class);
    this.runtimeController = modeler.viewController(RuntimeViewController.class);

    this.servicesController.registerView(this);
    this.runtimeController.registerView(this);

    digitalTwinView = new DigitalTwinView();
    workspaceView = new WorkspaceView();
    resourcesView = new ResourcesView();
    inspectorView = new InspectorView();
    inspectorView.setDockingActions(this::undockInspector, this::dockInspector);
    applicationView = new AgentView();
    ontologyView = new OntologyView();

    Logging.INSTANCE.info("Modeler initialized");
  }

  public <T extends BrowsablePage<?, ?>> T getView(View view, Class<T> cls) {
    return (T)
        switch (view) {
          case NOTEBOOK -> notebook;
          case RESOURCES -> resourcesView;
          case DIGITAL_TWINS -> digitalTwinView;
          case WORKSPACES -> workspaceView;
          case APPLICATIONS -> applicationView;
          case WORLDVIEW -> ontologyView;
          default -> throw new IllegalStateException("Unexpected value: " + view);
        };
  }

  @Override
  public void alert(Notification notification) {
    var alert =
        new Alert(
            switch (notification.getLevel()) {
              case Debug, Info -> Alert.AlertType.INFORMATION;
              case Notification.Level.Warning -> Alert.AlertType.WARNING;
              case Error, SystemError -> Alert.AlertType.ERROR;
            });
    alert.setTitle("Notification");
    alert.setHeaderText("Alert");
    alert.setContentText(notification.getMessage());
    alert.initOwner(KlabIDEApplication.scene().getWindow());
    alert.initStyle(StageStyle.DECORATED);
    alert.showAndWait();
  }

  @Override
  public boolean confirm(Notification notification) {
    return false;
  }

  @Override
  public void log(Notification notification) {}

  @Override
  public void cleanWorkspace() {}

  @Override
  public ResourceSet processAlerts(ResourceSet resourceSet) {
    for (var notification : resourceSet.getNotifications()) {
      handleNotification(notification);
    }
    return resourceSet;
  }

  public void selectView(View view) {
    this.currentView = view;
    for (var v : viewButtons.keySet()) {
      var button = viewButtons.get(v);
      if (v == view) {
        button.getStyleClass().removeAll(Styles.FLAT);
        button.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.BUTTON_OUTLINED);
      } else {
        button.getStyleClass().removeAll(Styles.BUTTON_OUTLINED);
        button.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
      }
    }

    var ui = getViewNode(view);

    currentEditorPage = getSelectedEditorInCurrentView();
    refreshDigitalTwinControls();

    // switch the main area to the requested view.
    Platform.runLater(
        () -> {
          mainArea.getChildren().removeIf(child -> child != modalPane);
          mainArea.getChildren().add(0, ui);
          if (modalPane != null) {
            ensureModalPaneAttached();
          }
          currentEditorPage = getSelectedEditorInCurrentView();
          refreshDigitalTwinControls();
          // If it's a browser and it's empty with no tabs open, open the browser
          if (ui instanceof BrowsablePage<?, ?> browsablePage && browsablePage.isEmpty()) {
            // FIXME not working - the browser won't show in every view but the UI will hang.
            //            browsablePage.showBrowser();
          }
        });
  }

  /** Open a standalone k.Actors file in the Sessions page from another IDE action. */
  public void openBehaviorFile(Path file) {
    if (file == null || applicationView == null) return;
    Platform.runLater(
        () -> {
          selectView(View.APPLICATIONS);
          applicationView.openFile(file);
        });
  }

  /** Convenience overload for JavaFX and legacy callers that work with {@link File}. */
  public void openBehaviorFile(File file) {
    if (file != null) openBehaviorFile(file.toPath());
  }

  public View selectedView() {
    return this.currentView;
  }

  @FXML
  protected void initialize() {

    // this is needed to manage the software stack API
    Klab.INSTANCE.setConfiguration(new CommonConfiguration());

    homeButton.setGraphic(
        new IconLabel(Material2AL.HOME, 24, Theme.CURRENT_THEME.getDefaultTextColor()));
    workspacesButton.setGraphic(new IconLabel(Theme.WORKSPACES_ICON, 24, Color.GREY));
    resourcesManagerButton.setGraphic(new IconLabel(Theme.RESOURCES_ICON, 24, Color.GREY));
    digitalTwinsButton.setGraphic(new IconLabel(Theme.DIGITAL_TWINS_ICON, 24, Color.GREY));
    sessionsButton.setGraphic(new IconLabel(Theme.APPLICATION_VIEW_ICON, 24, Color.GREY));
    worldviewButton.setGraphic(new IconLabel(Theme.WORLDVIEW_ICON, 24, Color.GREY));
    downloadButton.setGraphic(new IconLabel(Material2AL.GET_APP, 16, Color.GREY));
    startButton.setGraphic(new IconLabel(BootstrapIcons.POWER, 16, Color.GREY));
    reasonerButton.setGraphic(new IconLabel(Theme.LOCAL_SERVICE_ICON, 16, Color.GREY));
    resourcesButton.setGraphic(new IconLabel(Theme.LOCAL_SERVICE_ICON, 16, Color.GREY));
    resolverButton.setGraphic(new IconLabel(Theme.LOCAL_SERVICE_ICON, 16, Color.GREY));
    runtimeButton.setGraphic(new IconLabel(Theme.LOCAL_SERVICE_ICON, 16, Color.GREY));
    settingsButton.setGraphic(
        new IconLabel(FontAwesomeSolid.COG, 24, Theme.CURRENT_THEME.getDefaultTextColor()));
    inspectorButton.setGraphic(
        new IconLabel(Theme.INSPECTOR_ICON, 24, Theme.CURRENT_THEME.getDefaultTextColor()));
    updateInspectorButton();
    profileButton.setGraphic(new IconLabel(FontAwesomeSolid.USER_CIRCLE, 32, Color.GREY));

    viewButtons.put(View.NOTEBOOK, homeButton);
    viewButtons.put(View.DIGITAL_TWINS, digitalTwinsButton);
    viewButtons.put(View.RESOURCES, resourcesManagerButton);
    viewButtons.put(View.APPLICATIONS, sessionsButton);
    viewButtons.put(View.WORKSPACES, workspacesButton);
    viewButtons.put(View.WORLDVIEW, worldviewButton);

    notificationArea = new VBox();
    notificationArea.setMinWidth(280);
    BorderPane.setMargin(notificationArea, new Insets(0));
    notificationArea.setStyle("-fx-background-color: -color-neutral-muted;");

    inspectorButton.setOnMouseClicked(
        event -> {
          toggleInspector();
        });

    startButton.setOnMouseClicked(mouseEvent -> handleStartButtonPress());

    downloadButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.toggle(AssetViewComponent.Type.Distribution);
          selectView(View.NOTEBOOK);
        });
    logo.setOnMouseClicked(
        mouseEvent -> {
          notebook.toggle(AssetViewComponent.Type.About);
          //          selectView(View.NOTEBOOK);
        });
    profileButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.toggle(AssetViewComponent.Type.UserInfo);
          selectView(View.NOTEBOOK);
        });
    settingsButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.toggle(AssetViewComponent.Type.Settings);
          selectView(View.NOTEBOOK);
        });
    reasonerButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.toggle(AssetViewComponent.Type.ReasonerService);
          selectView(View.NOTEBOOK);
        });
    resourcesButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.toggle(AssetViewComponent.Type.ResourcesService);
          selectView(View.NOTEBOOK);
        });
    resolverButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.toggle(AssetViewComponent.Type.ResolverService);
          selectView(View.NOTEBOOK);
        });
    runtimeButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.toggle(AssetViewComponent.Type.RuntimeService);
          selectView(View.NOTEBOOK);
        });

    for (var key : viewButtons.keySet()) {
      viewButtons.get(key).setOnMouseClicked(mouseEvent -> selectView(key));
    }

    setStatusBar();
    ensureModalPaneAttached();

    this.dbIcon = new IconLabel(MaterialDesign.MDI_DATABASE, 11, Color.DARKGRAY);
    this.langIcon = new IconLabel(CarbonIcons.LANGUAGE, 11, Color.DARKGRAY);
    this.messIcon = new IconLabel(Evaicons.MESSAGE_SQUARE_OUTLINE, 11, Color.DARKGRAY);

    Platform.runLater(
        () -> {
          createModeler();
          modeler.boot();
          this.user = modeler.authenticate();

          // must call explicitly because the callback won't be used before boot.
          notifyUser(this.user.getUser());
          notifications =
              Queues.synchronizedQueue(
                  EvictingQueue.create(
                      modeler
                          .engine()
                          .getSettings()
                          .get(Setting.NOTIFICATIONS_CACHED, Integer.class)));

          //          if (settings.getStartServicesOnStartup().getValue()) {
          //            // TODO
          //            //      Thread.ofPlatform().start(this::toggleLocalServices);
          //          }
          initializeSoftwareStack();
        });
  }

  private void setStatusBar() {

    toggleRightSideButton = new Button();
    toggleRightSideButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    toggleRightSideButton.setOnAction(e -> toggleNotificationPanel());
    toggleRightSideButton.setGraphic(
        new IconLabel(Material2MZ.NAVIGATE_BEFORE, 24, Theme.CURRENT_THEME.getDefaultTextColor()));

    // This will contain the current DT name and statistics
    digitalTwinBox = new HBox(0);
    digitalTwinBox.setAlignment(Pos.CENTER_LEFT);
    this.digitalTwinSwitcher = new MenuButton();
    this.digitalTwinSwitcher.getStyleClass().addAll(Styles.FLAT);
    digitalTwinSwitcher.setPrefWidth(205);

    digitalTwinButton = new Button();
    digitalTwinButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    digitalTwinButton.setTooltip(new Tooltip("Show Digital Twin Control Panel"));
    digitalTwinButton.setDisable(true);
    digitalTwinButton.setGraphic(
        new IconLabel(FontAwesomeSolid.ARROW_CIRCLE_UP, 14, Color.DARKGREEN));
    digitalTwinButton.setOnAction(e -> toggleDigitalTwinControlPanel());

    dtResetButton = new Button();
    dtResetButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    dtResetButton.setTooltip(new Tooltip("Show Digital Twin Control Panel"));
    dtResetButton.setDisable(true);
    dtResetButton.setGraphic(new IconLabel(FontAwesomeSolid.TIMES_CIRCLE, 14, Color.DARKRED));
    dtResetButton.setOnAction(e -> resetCurrentDigitalTwin());

    dtSwitchButton = new Button();
    dtSwitchButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    dtSwitchButton.setTooltip(new Tooltip("Show Digital Twin Control Panel"));
    dtSwitchButton.setDisable(true);
    dtSwitchButton.setGraphic(new IconLabel(Theme.DIGITAL_TWINS_ICON, 16, Color.GREY));
    dtSwitchButton.setOnAction(
        e -> {
          if (getFocalScope() != null) {
            KlabIDEController.instance()
                .getView(KlabIDEController.View.DIGITAL_TWINS, DigitalTwinView.class)
                .showDigitalTwin(getFocalScope());
            KlabIDEController.instance().selectView(KlabIDEController.View.DIGITAL_TWINS);
          }
        });

    digitalTwinBox
        .getChildren()
        .addAll(
            new Separator(Orientation.VERTICAL),
            dtSwitchButton,
            digitalTwinSwitcher,
            digitalTwinButton,
            dtResetButton,
            new Separator(Orientation.VERTICAL));

    this.infoLabel =
        new Label(null, new IconLabel(Material2AL.FIBER_MANUAL_RECORD, 16, Color.BLUE));
    this.errorLabel =
        new Label(null, new IconLabel(Material2AL.FIBER_MANUAL_RECORD, 16, Color.RED));
    this.warningLabel =
        new Label(null, new IconLabel(Material2AL.FIBER_MANUAL_RECORD, 16, Color.ORANGE));
    this.messageLabel = new Label();
    HBox.setHgrow(messageLabel, Priority.ALWAYS);
    this.warningLabel.setTooltip(new Tooltip("No unread warnings."));
    this.errorLabel.setTooltip(new Tooltip("No unread errors."));
    this.infoLabel.setTooltip(new Tooltip("No unread notifications."));

    statusBar
        .getChildren()
        .addAll(
            messageLabel,
            digitalTwinBox,
            infoLabel,
            warningLabel,
            errorLabel,
            toggleRightSideButton);
  }

  private void resetCurrentDigitalTwin() {
    setFocalScope(null, false);
    if (currentEditorPage instanceof DigitalTwinEditor editor) {
      editor.close();
      currentEditorPage = null;
    }
  }

  public void showInModalOverlay(Node node) {

    Platform.runLater(
        () -> {
          ensureModalPaneAttached();
          if (!modalEscHandlerInstalled && mainArea.getScene() != null) {
            mainArea.getScene().addEventFilter(KeyEvent.KEY_PRESSED, escHandler);
            modalEscHandlerInstalled = true;
          }

          mainArea.applyCss();
          modalPane.applyCss();
          mainArea.layout();
          modalPane.layout();
          modalPane.show(node);
          modalPane.toFront();
          modalPane.requestFocus();
        });
  }

  private void ensureModalPaneAttached() {
    if (modalPane == null) {
      modalPane = new ModalPane();
      modalPane.setAlignment(Pos.CENTER);
      modalPane.setPickOnBounds(true);
      modalPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      modalPane.prefWidthProperty().bind(mainArea.widthProperty());
      modalPane.prefHeightProperty().bind(mainArea.heightProperty());
      if (mainArea instanceof StackPane) {
        StackPane.setAlignment(modalPane, Pos.CENTER);
      }
    }
    if (!mainArea.getChildren().contains(modalPane)) {
      mainArea.getChildren().add(modalPane);
    }
    modalPane.toFront();
  }

  public void removeModalOverlay() {
    Platform.runLater(
        () -> {
          if (modalPane != null) {
            if (modalEscHandlerInstalled && mainArea.getScene() != null) {
              mainArea.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, escHandler);
            }
            modalEscHandlerInstalled = false;
            modalPane.hide();
          }
        });
  }

  private void toggleDigitalTwinControlPanel() {
    currentEditorPage = getSelectedEditorInCurrentView();
    if (currentEditorPage != null) {
      currentEditorPage.toggleDigitalTwinControlPanel();
    }
    refreshDigitalTwinControls();
  }

  private void handleStartButtonPress() {

    var condition =
        engineStatus.get() == null
            ? Engine.Status.EngineCondition.INOPERATIVE
            : engineStatus.get().getCondition();

    if (condition == Engine.Status.EngineCondition.TRANSITIONING
        || !localServiceActionRunning.compareAndSet(false, true)) {
      Toolkit.getDefaultToolkit().beep();
      return;
    }

    setButton(
        startButton,
        BootstrapIcons.CLOCK,
        16,
        Color.DARKGOLDENROD,
        "Local services are starting or stopping. Wait until status changes.");
    setStartButtonDisabled(true);

    Thread.ofVirtual()
        .name("klab-local-services-action")
        .start(
            () -> {
              try {
                switch (condition) {
                  case INOPERATIVE, ACTIVE_REMOTE_ONLY ->
                      KlabIDEController.modeler().engine().startLocalServices();
                  case ACTIVE_LOCAL_ONLY, ACTIVE_LOCAL_AND_REMOTE ->
                      KlabIDEController.modeler().engine().stopLocalServices();
                  case TRANSITIONING -> {
                    // Already handled before starting the worker.
                  }
                }
              } catch (Throwable throwable) {
                Logging.INSTANCE.error(throwable);
              } finally {
                var currentStatus = engineStatus.get();
                if (currentStatus == null
                    || currentStatus.getCondition()
                        != Engine.Status.EngineCondition.TRANSITIONING) {
                  localServiceActionRunning.set(false);
                }
                if (currentStatus != null) {
                  engineStatusChanged(currentStatus);
                } else {
                  setStartButtonDisabled(false);
                }
              }
            });
  }

  private void setStartButtonDisabled(boolean disabled) {
    Platform.runLater(() -> startButton.setDisable(disabled));
  }

  public boolean handleNotification(Notification notification) {
    var ret = false;
    if (notification.getLevel().severity > 2) {
      ret = true;
    }

    notifications.add(notification);

    Platform.runLater(
        () -> {
          if (notificationsVisible.get()) {
            // add component on top
            redrawNotificationBox();
          } else {
            // notify unread status
            switch (notification.getLevel()) {
              case Debug, Info -> {
                infoLabel.setGraphic(
                    new IconLabel(Material2MZ.NOTIFICATIONS_ACTIVE, 16, Color.BLUE));
                infoLabel.setTooltip(new Tooltip("There are new notifications."));
              }
              case Warning -> {
                warningLabel.setGraphic(
                    new IconLabel(Material2MZ.NOTIFICATIONS_ACTIVE, 16, Color.ORANGE));
                warningLabel.setTooltip(new Tooltip("There are new warnings."));
              }
              case Error, SystemError -> {
                errorLabel.setGraphic(
                    new IconLabel(Material2MZ.NOTIFICATIONS_ACTIVE, 16, Color.RED));
                errorLabel.setTooltip(new Tooltip("There are new errors."));
              }
            }
          }

          if (currentPause != null) {
            currentPause.stop();
          }
          messageLabel.setText(notification.getMessage());
          messageLabel.setGraphic(
              switch (notification.getLevel()) {
                case Debug, Info ->
                    new IconLabel(
                        Notification.Outcome.Success == notification.getOutcome()
                            ? Material2AL.CHECK_CIRCLE
                            : Material2AL.INFO,
                        16,
                        Notification.Outcome.Success == notification.getOutcome()
                            ? Color.GREEN
                            : Color.BLUE);
                case Warning -> new IconLabel(Material2MZ.WARNING, 16, Color.ORANGE);
                case Error, SystemError -> new IconLabel(Material2AL.ERROR, 16, Color.RED);
              });
          currentPause = new PauseTransition(Duration.seconds(5));
          currentPause.setOnFinished(
              event -> {
                messageLabel.setText("");
                messageLabel.setGraphic(null);
              });
          currentPause.play();
        });
    return ret;
  }

  private void redrawNotificationBox() {
    Platform.runLater(
        () -> {

          // reset icons to "all read"
          infoLabel.setGraphic(new IconLabel(Material2AL.FIBER_MANUAL_RECORD, 16, Color.BLUE));
          warningLabel.setGraphic(new IconLabel(Material2AL.FIBER_MANUAL_RECORD, 16, Color.ORANGE));
          errorLabel.setGraphic(new IconLabel(Material2AL.FIBER_MANUAL_RECORD, 16, Color.RED));
          infoLabel.setTooltip(new Tooltip("No unread notifications."));
          warningLabel.setTooltip(new Tooltip("No unread warnings."));
          errorLabel.setTooltip(new Tooltip("No unread errors."));

          notificationArea.getChildren().clear();

          var notificationOrder = new ArrayList<>(notifications);
          Collections.reverse(notificationOrder);
          for (var notification : notificationOrder) {
            notificationArea.getChildren().add(makeNotificationPanel(notification));
          }
        });
  }

  private Node makeNotificationPanel(Notification notification) {
    var text = Utils.Strings.justifyLeft(notification.getMessage(), 40);
    var date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(ZonedDateTime.now());
    var ret =
        new atlantafx.base.controls.Message(
            switch (notification.getLevel()) {
                  case Debug, Info -> "Information";
                  case Warning -> "Warning";
                  case Error, SystemError -> "Error";
                }
                + " "
                + date,
            text,
            switch (notification.getLevel()) {
              case Debug, Info ->
                  new IconLabel(
                      Notification.Outcome.Success == notification.getOutcome()
                          ? Material2AL.CHECK_CIRCLE
                          : Material2AL.INFO,
                      24,
                      Notification.Outcome.Success == notification.getOutcome()
                          ? Color.GREEN
                          : Color.BLUE);
              case Warning -> new IconLabel(Material2MZ.WARNING, 24, Color.ORANGE);
              case Error, SystemError -> new IconLabel(Material2AL.ERROR, 24, Color.RED);
            });
    ret.getStyleClass()
        .addAll(
            switch (notification.getLevel()) {
              case Debug, Info ->
                  List.of(
                      Notification.Outcome.Success == notification.getOutcome()
                          ? Styles.SUCCESS
                          : Styles.ACCENT);
              case Warning -> List.of(Styles.WARNING);
              case Error, SystemError -> List.of(Styles.DANGER);
            });

    ret.setOnClose(
        e -> {
          notifications.remove(notification);
          if (notifications.isEmpty()) {
            toggleNotificationPanel();
          } else {
            redrawNotificationBox();
          }
        });

    return ret;
  }

  /**
   * Receive a set of notifications and handle them through the UI; return true if any of them was
   * an error.
   *
   * @param notifications
   * @return
   */
  public boolean handleNotifications(List<Notification> notifications) {

    int errorCount = 0;
    for (var notification : notifications) {
      if (handleNotification(notification)) {
        errorCount++;
      }
    }
    return errorCount > 0;
  }

  private void toggleInspector() {
    if (inspectorIsOn) {
      hideInspector();
    } else {
      showInspector();
    }
  }

  public InspectorView showInspector() {
    Platform.runLater(
        () -> {
          if (inspectorDocked) {
            inspectorView.setDocked(true);
            inspectorArea.getChildren().setAll(inspectorView);
            NodeUtils.toggleVisibility(inspectorArea, true);
          } else {
            ensureDetachedInspectorStage();
            detachedInspectorStage.show();
            detachedInspectorStage.toFront();
            NodeUtils.toggleVisibility(inspectorArea, false);
          }
          inspectorIsOn = true;
          KlabIDEApplication.instance().setInspectorShown(true);
          updateInspectorButton();
        });
    return inspectorView;
  }

  public void hideInspector() {
    Platform.runLater(
        () -> {
          if (inspectorDocked) {
            inspectorArea.getChildren().clear();
            NodeUtils.toggleVisibility(inspectorArea, false);
          } else if (detachedInspectorStage != null) {
            detachedInspectorStage.hide();
          }
          inspectorIsOn = false;
          KlabIDEApplication.instance().setInspectorShown(false);
          updateInspectorButton();
        });
  }

  public void undockInspector() {
    Platform.runLater(
        () -> {
          if (!inspectorDocked) {
            showInspector();
            return;
          }
          inspectorDocked = false;
          inspectorView.setDocked(false);
          inspectorArea.getChildren().remove(inspectorView);
          NodeUtils.toggleVisibility(inspectorArea, false);
          ensureDetachedInspectorStage();
          detachedInspectorStage.show();
          detachedInspectorStage.toFront();
          inspectorIsOn = true;
          KlabIDEApplication.instance().setInspectorShown(true);
          updateInspectorButton();
        });
  }

  public void dockInspector() {
    Platform.runLater(
        () -> {
          boolean shouldShow =
              inspectorIsOn || (detachedInspectorStage != null && detachedInspectorStage.isShowing());
          if (detachedInspectorStage != null) {
            if (detachedInspectorStage.getScene() != null
                && detachedInspectorStage.getScene().getRoot() instanceof Pane pane) {
              pane.getChildren().remove(inspectorView);
            }
            detachedInspectorStage.hide();
          }
          inspectorDocked = true;
          inspectorView.setDocked(true);
          if (shouldShow) {
            inspectorArea.getChildren().setAll(inspectorView);
            NodeUtils.toggleVisibility(inspectorArea, true);
            inspectorIsOn = true;
          } else {
            inspectorArea.getChildren().clear();
            NodeUtils.toggleVisibility(inspectorArea, false);
            inspectorIsOn = false;
          }
          KlabIDEApplication.instance().setInspectorShown(inspectorIsOn);
          updateInspectorButton();
        });
  }

  private void ensureDetachedInspectorStage() {
    if (detachedInspectorStage == null) {
      StackPane root = new StackPane();
      root.getStyleClass().add("inspector-window-root");
      Scene scene = new Scene(root, 640, 380);
      if (KlabIDEApplication.scene() != null) {
        scene.getStylesheets().setAll(KlabIDEApplication.scene().getStylesheets());
      }
      detachedInspectorStage = new Stage(StageStyle.DECORATED);
      detachedInspectorStage.setTitle("k.LAB Inspector");
      if (KlabIDEApplication.primaryStage() != null) {
        detachedInspectorStage.initOwner(KlabIDEApplication.primaryStage());
      }
      detachedInspectorStage.setScene(scene);
      detachedInspectorStage.setOnCloseRequest(
          event -> {
            event.consume();
            hideInspector();
          });
    }

    if (detachedInspectorStage.getScene().getRoot() instanceof Pane pane
        && !pane.getChildren().contains(inspectorView)) {
      pane.getChildren().setAll(inspectorView);
    }
  }

  private void updateInspectorButton() {
    setButton(
        inspectorButton,
        Theme.INSPECTOR_ICON,
        24,
        inspectorIsOn ? Color.GOLDENROD : Theme.CURRENT_THEME.getDefaultTextColor(),
        inspectorIsOn
            ? (inspectorDocked ? "Click to hide the knowledge inspector" : "Click to hide the detached knowledge inspector")
            : (inspectorDocked ? "Click to show the knowledge inspector" : "Click to show the detached knowledge inspector"));
  }

  public boolean isInspectorShown() {
    return inspectorIsOn;
  }

  public InspectorView getInspector() {
    return inspectorView;
  }

  public void toggleNotificationPanel() {
    notificationsVisible.set(!notificationsVisible.get());
    if (notificationsVisible.get()) {
      redrawNotificationBox();
      rootPane.setRight(notificationArea);
      setButton(
          toggleRightSideButton,
          Material2MZ.NAVIGATE_NEXT,
          24,
          Color.GREY,
          "Hide recent notifications");
    } else {
      rootPane.setRight(null);
      setButton(
          toggleRightSideButton,
          Material2MZ.NAVIGATE_BEFORE,
          24,
          Color.GREY,
          "Show recent notifications");
    }
  }

  @Override
  public void notifyServiceStatus(KlabService service, KlabService.ServiceStatus status) {
    this.serviceStatus.put(service, status);
  }

  @Override
  public void engineStatusChanged(Engine.Status status) {

    engineStatus.set(status);

    switch (status.getCondition()) {
      case TRANSITIONING -> {
        localServiceActionRunning.set(true);
        setStartButtonDisabled(true);
        setButton(
            startButton,
            BootstrapIcons.CLOCK,
            16,
            Color.DARKGOLDENROD,
            "Local services are starting or stopping. Wait until status changes.");
      }
      case INOPERATIVE, ACTIVE_REMOTE_ONLY -> {
        localServiceActionRunning.set(false);
        var hasValidSoftwareStack = engine().hasValidSoftwareStack();
        setStartButtonDisabled(false);
        if (hasValidSoftwareStack) {
          setButton(
              startButton,
              BootstrapIcons.POWER,
              16,
              Color.DARKGREEN,
              "Local services are not running. Click to start them.");
        } else {
          setButton(
              startButton,
              BootstrapIcons.POWER,
              16,
              Color.GREY,
              "No distribution is available. Please download one.");
        }
      }
      case ACTIVE_LOCAL_ONLY, ACTIVE_LOCAL_AND_REMOTE -> {
        localServiceActionRunning.set(false);
        setStartButtonDisabled(false);
        setButton(
            startButton,
            BootstrapIcons.STOP,
            16,
            Color.DARKRED,
            "Local services are running. Click to stop them.");
        if (notifications != null) {
          handleNotification(
              Notification.info("Local services ready for use", Notification.Outcome.Success));
        }
      }
    }

    for (var serviceType : KlabService.Type.operationCritical()) {

      String serviceName = serviceType.name().toLowerCase();
      var provision = status.getServicesProvision().get(serviceType);
      if (provision == null) { // at init
        provision = Engine.Status.ServiceProvision.INOPERATIVE;
      }
      var button =
          switch (serviceType) {
            case REASONER -> reasonerButton;
            case RESOURCES -> resourcesButton;
            case RESOLVER -> resolverButton;
            case RUNTIME -> runtimeButton;
            default -> throw new KlabInternalErrorException("?"); // can't happen
          };

      var color =
          provision == Engine.Status.ServiceProvision.INOPERATIVE
              ? Color.GREY
              : switch (serviceType) {
                case REASONER ->
                    provision.isOperational()
                        ? Theme.REASONER_COLOR_ACTIVE
                        : Theme.REASONER_COLOR_MUTED;
                case RESOURCES ->
                    provision.isOperational()
                        ? Theme.RESOURCES_COLOR_ACTIVE
                        : Theme.RESOURCES_COLOR_MUTED;
                case RESOLVER ->
                    provision.isOperational()
                        ? Theme.RESOLVER_COLOR_ACTIVE
                        : Theme.RESOLVER_COLOR_MUTED;
                case RUNTIME ->
                    provision.isOperational()
                        ? Theme.RUNTIME_COLOR_ACTIVE
                        : Theme.RUNTIME_COLOR_MUTED;
                default -> throw new KlabInternalErrorException("?"); // can't happen
              };

      var icon =
          switch (provision) {
            case INOPERATIVE, LOCAL_INOP_SINGLE, LOCAL_SINGLE -> Theme.LOCAL_SERVICE_ICON;
            case REMOTE_SINGLE, LOCAL_INOP_REMOTE_SINGLE -> Theme.REMOTE_SERVICE_ICON_ONE;
            case REMOTE_MULTI, LOCAL_INOP_REMOTE_MULTI -> Theme.REMOTE_SERVICE_ICON_MANY;
            case LOCAL_REMOTE_SINGLE, LOCAL_REMOTE_MULTI -> Theme.LOCAL_AND_REMOTE_SERVICE_ICON;
          };

      if (serviceType == KlabService.Type.RUNTIME) {
        if (provision.isOperational()) {
          setButton(
              digitalTwinsButton,
              Theme.DIGITAL_TWINS_ICON,
              24,
              Color.DARKGREEN,
              digitalTwinsButton.getTooltip().getText());
        } else {
          setButton(
              digitalTwinsButton,
              Theme.DIGITAL_TWINS_ICON,
              24,
              Color.GREY,
              digitalTwinsButton.getTooltip().getText());
        }
      } else if (serviceType == KlabService.Type.REASONER) {
        if (provision.isOperational()) {
          setButton(
              worldviewButton,
              Theme.WORLDVIEW_ICON,
              24,
              Color.DARKGREEN,
              worldviewButton.getTooltip().getText());
        } else {
          setButton(
              worldviewButton,
              Theme.WORLDVIEW_ICON,
              24,
              Color.GREY,
              worldviewButton.getTooltip().getText());
        }
      } else if (serviceType == KlabService.Type.RESOURCES) {
        if (provision.isOperational()) {
          setButton(
              workspacesButton,
              Theme.WORKSPACES_ICON,
              24,
              Color.DARKGREEN,
              workspacesButton.getTooltip().getText());
          setButton(
              resourcesManagerButton,
              Theme.RESOURCES_ICON,
              24,
              Color.DARKGREEN,
              resourcesManagerButton.getTooltip().getText());
          setButton(
              sessionsButton,
              Theme.APPLICATION_VIEW_ICON,
              24,
              Color.DARKGREEN,
              sessionsButton.getTooltip().getText());
        } else {
          setButton(
              workspacesButton,
              Theme.WORKSPACES_ICON,
              24,
              Color.GREY,
              workspacesButton.getTooltip().getText());
          setButton(
              resourcesManagerButton,
              Theme.RESOURCES_ICON,
              24,
              Color.GREY,
              resourcesManagerButton.getTooltip().getText());
          setButton(
              sessionsButton,
              Theme.APPLICATION_VIEW_ICON,
              24,
              Color.GREY,
              sessionsButton.getTooltip().getText());
        }
      }

      this.dbIcon.set(
          Theme.DATABASE_ICON,
          11,
          status.getActiveAuxiliaryServices().contains(Distribution.Product.Type.DATABASE_SERVER)
              ? Color.LIGHTGREEN
              : Color.DARKGRAY);
      this.langIcon.set(
          Theme.LANGUAGE_SERVER_ICON,
          11,
          status.getActiveAuxiliaryServices().contains(Distribution.Product.Type.LANGUAGE_SERVER)
              ? Color.LIGHTGREEN
              : Color.DARKGRAY);
      this.messIcon.set(
          Theme.MESSAGING_ICON,
          11,
          status.getActiveAuxiliaryServices().contains(Distribution.Product.Type.AMQP_BROKER)
              ? Color.LIGHTGREEN
              : Color.DARKGRAY);

      var tooltip = serviceName; // FIXME use meaningful tooltip based on provision

      setButton(button, icon, 16, color, tooltip);
    }

    if (!knowledgeInitialized
        && (status.getCondition() == Engine.Status.EngineCondition.ACTIVE_LOCAL_AND_REMOTE
            || status.getCondition() == Engine.Status.EngineCondition.ACTIVE_LOCAL_ONLY)) {
      knowledgeInitialized = true;
      if (engine() instanceof EngineImpl engine) {
        // ensure the next worldview request gets the local version
        engine.resetWorldview();
      }
    }
  }

  @Override
  public void show() {}

  @Override
  public void hide() {}

  @Override
  public void enable() {}

  @Override
  public void disable() {}

  @Override
  public boolean isShown() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void notifyNewDigitalTwin(ContextScope scope, RuntimeService service) {
    var peer = requireDigitalTwinPeer(scope, null);
    setFocalScope(peer, Utils.URLs.isLocalHost(scope.getUrl()));
  }

  @Override
  public void notifyDigitalTwinModified(DigitalTwin digitalTwin, Message change) {
    Logging.INSTANCE.info("Digital twin changed: " + change);
    var peer = findDigitalTwinPeer(digitalTwin);
    if (peer != null) {
      peer.acceptDigitalTwinEvent(change);
    }
  }

  @Override
  public void notifyObservationSubmission(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Observation submitted: " + observation);
    routeToDigitalTwin(contextScope, peer -> peer.notifySubmissionStarted(observation));
  }

  void unregisterDigitalTwin(IDEContextScope ideContextScope) {
    if (focalScope != null && focalScope.getId().equals(ideContextScope.getId())) {
      focalScope = null;
      //      modeler().setCurrentContext(null);
    }
    for (var viewer : digitalTwinReactors) {
      if (viewer.isAffectedBy(ideContextScope)) {
        viewer.closeDigitalTwin(ideContextScope);
      }
    }
    contextMap.remove(ideContextScope.getId());
  }

  private void routeToDigitalTwin(
      ContextScope contextScope, Consumer<IDEContextScope> notification) {
    var peer = requireDigitalTwinPeer(contextScope, null);
    if (peer != null) {
      notification.accept(peer);
    }
  }

  private synchronized IDEContextScope findDigitalTwinPeer(DigitalTwin digitalTwin) {
    if (digitalTwin == null) {
      return null;
    }
    for (var peer : contextMap.values()) {
      if (peer.getDigitalTwin() == digitalTwin) {
        return peer;
      }
    }
    return null;
  }

  @Override
  public void notifyObservationSubmissionAborted(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Observation submission aborted: " + observation);
    routeToDigitalTwin(contextScope, peer -> peer.notifySubmissionAborted(observation));
  }

  @Override
  public void notifyObservationSubmissionFinished(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Observation submission finished: " + observation);
    routeToDigitalTwin(contextScope, peer -> peer.notifySubmissionFinished(observation));
  }

  @Override
  public void notifyContextObservationResolved(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Context observation resolved: " + observation);
    routeToDigitalTwin(contextScope, peer -> peer.notifyContextChanged(observation));
  }

  @Override
  public void notifyObserverResolved(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Observer resolved: " + observation);
    routeToDigitalTwin(contextScope, peer -> peer.notifyObserverChanged(observation));
  }

  //  @Override
  public void notifyUser(UserIdentity identity) {

    if (identity.isAnonymous()) {
      setButton(
          profileButton,
          FontAwesomeSolid.USER_CIRCLE,
          32,
          Color.DARKRED,
          "Anonymous user. Please obtain a certificate.");
    } else if (identity.isAuthenticated()) {
      setButton(
          profileButton,
          FontAwesomeSolid.USER_CIRCLE,
          32,
          Color.DARKGREEN,
          "User " + identity.getUsername() + " logged in");
    } else {
      setButton(
          profileButton,
          FontAwesomeSolid.USER_CIRCLE,
          32,
          Color.DARKGOLDENROD,
          "Authentication failed for user " + identity.getUsername());
    }

    /*
    TODO set the service icons to the color and icon for the services currently available after authentication.
     They can be local or remote, should have different icons and service-dependent colors.
     */

  }

  /**
   * TODO use this from the distribution view. Switching should only be available with engines
   * stopped.
   *
   * @param tag
   */
  public void switchDistributionTag(Stack.Tag tag) {
    if (engineStatus.get().isOperational()) {
      Toolkit.getDefaultToolkit().beep();
    } else {
      if (engine() instanceof EngineImpl engine) {
        engine.setDistributionTag(tag);
        Platform.runLater(this::initializeSoftwareStack);
      }
    }
  }

  public void initializeSoftwareStack() {

    if (!otherServices.getChildren().contains(dbIcon)) {
      otherServices.getChildren().addAll(dbIcon, langIcon, messIcon);
    }

    Ikon icon = BootstrapIcons.DOWNLOAD;
    var color = Color.GREEN;
    var tooltip = "No k.LAB distribution is available";
    var startColor = Color.GREEN;
    var startTooltip = "Local services are not available";
    var distributionTag = engine().getDistributionTag();
    var available = false;
    if (distributionTag.version() == Version.HEAD) {

      icon = BootstrapIcons.LAPTOP;
      tooltip = "Using locally available source k.LAB distribution";
      startTooltip = "Start local k.LAB services";
      startButton.setDisable(false);

    } else {

      var status = engine().getSoftwareStack().status(engine().getDistributionTag());

      if (status == Stack.Status.ABSENT) {
        color = Color.RED;
        tooltip = "No distribution available. Click to download";
        startButton.setDisable(true);
      } else if (status.downloadSize() == 0 && status.totalContentSize() > 0) {
        startTooltip = "Start local k.LAB services";
        icon = BootstrapIcons.CHECK;
        startButton.setDisable(false);
      } else {
        color = Color.GOLDENROD;
        tooltip = "Updated k.LAB distribution available. Click to update";
        startTooltip = "Start out-of-date local services";
        startButton.setDisable(false);
      }
    }
    setButton(startButton, Material2MZ.POWER_SETTINGS_NEW, 16, startColor, startTooltip);
    setButton(downloadButton, icon, 16, color, tooltip);

    if (engine().getSettings().get(Setting.START_LSP_SERVER_ON_STARTUP, Boolean.class)) {
      if (engine().startAuxiliaryServices(KlabService.Type.LANGUAGE_SERVER)) {
        handleNotification(
            Notification.info("Language server started", Notification.Outcome.Success));
        langIcon.set(Theme.LANGUAGE_SERVER_ICON, 11, Color.LIGHTGREEN);
      } else {
        handleNotification(
            Notification.warning("Language server not available", Notification.Outcome.Failure));
        langIcon.set(Theme.LANGUAGE_SERVER_ICON, 11, Color.RED);
      }
    } else {
      handleNotification(Notification.info("Language server was disabled in settings"));
    }
  }

  public static void setButton(Button button, Ikon icon, int size, Color color, String tooltip) {
    var iconControl = button.getGraphic();
    if (iconControl instanceof IconLabel fontIcon) {
      Platform.runLater(
          () -> {
            fontIcon.set(icon, size, color);
            var ttp = new Tooltip(tooltip);
            ttp.setShowDelay(Duration.millis(200));
            button.setTooltip(ttp);
          });
    }
  }

  @Visualization(
      geometry = "S2", // means all other dims have size 1
      artifactTypes = Artifact.Type.OBSERVATION,
      provides = "text/html",
      requires = "image/tiff;application=geotiff")
  public URL visualizeRasterAsHtml(File tiffFile, Observation observation) {

    var templateUrl = this.getClass().getResource("templates/geotiff.jte");
    var html =
        Utils.Templates.renderJTEHtml(
            templateUrl, Map.of("url", Utils.Files.getFileName(tiffFile)));
    File output =
        new File(
            tiffFile.getParentFile() + File.separator + "geotiff_" + observation.getId() + ".html");

    Utils.Files.writeStringToFile(html, output);
    return modeler().publishLocally(output, "modeler", tiffFile);
  }

  /* --------------------------------------------------------------------------------------------------
   * Delegate methods
   * --------------------------------------------------------------------------------------------------
   */

  @Override
  public UserScope authenticate() {
    return modeler.authenticate();
  }

  @Override
  public CompletableFuture<Observation> observe(ContextScope scope, Object asset, boolean adding) {
    // call the original after adapting the scope; then notify the UI of whatever happened
    return modeler
        .observe(
            scope instanceof IDEContextScope ideContextScope ? ideContextScope.delegate : scope,
            asset,
            adding)
        .exceptionally(
            t -> {
              handleNotification(Notification.error("Observation failed: ", t));
              return Observation.EMPTY_OBSERVATION;
            })
        .thenApply(
            o -> {
              if (o.isEmpty()) {
                if (o.getNotifications().isEmpty()) {
                  handleNotification(
                      Notification.error(
                          "Observation "
                              + (o.getObservable() == null
                                  ? ""
                                  : ("of " + o.getObservable().getUrn()))
                              + " failed"));
                }
                for (var notification : o.getNotifications()) {
                  handleNotification(notification);
                }
              } else if (o.getObservable().is(SemanticType.SUBJECT)
                  && !o.getObservable().getSemantics().isCollective()
                  && scope instanceof IDEContextScope) {
                // set as context to avoid pain
                // FIXME check - this does not do anything? Also, do we really want it?
                scope.within(o);
              }

              return o;
            });
  }

  @Override
  public <T> T visualize(
      KlabAsset asset,
      Scheduler.Event event,
      String mediaType,
      ContextScope contextScope,
      Map<String, Object> visualizationOptions,
      Class<T> outputType) {
    return modeler.visualize(
        asset, event, mediaType, contextScope, visualizationOptions, outputType);
  }

  @Override
  public synchronized ContextScope createDefaultContext() {
    var context = modeler.createDefaultContext();
    if (context == null) {
      alert(Notification.error("Failed to create default context: is the local runtime running?"));
      return null;
    }
    var ret = new IDEContextScope((ClientContextScope) context);
    contextMap.put(ret.getId(), ret);
    setFocalScope(ret, true);
    return ret;
  }

  @Override
  public URL publishLocally(File inputFile, String workspace, File... additionalFiles) {
    return modeler.publishLocally(inputFile, workspace, additionalFiles);
  }

  @Override
  public boolean shutdown(boolean shutdownLocalServices) {
    return modeler == null || modeler.shutdown(shutdownLocalServices);
  }

  @Override
  public void importProject(
      ResourcesService service,
      String workspaceName,
      String projectUrl,
      boolean overwriteExisting) {
    modeler.importProject(service, workspaceName, projectUrl, overwriteExisting);
  }

  @Override
  public void deleteProject(ResourcesService service, String projectUrl) {
    modeler.deleteProject(service, projectUrl);
  }

  @Override
  public void deleteAsset(ResourcesService service, NavigableAsset asset) {
    modeler.deleteAsset(service, asset);
  }

  @Override
  public void manageProject(
      ResourcesService service,
      String projectId,
      RepositoryState.Operation operation,
      String... arguments) {
    modeler.manageProject(service, projectId, operation, arguments);
  }

  @Override
  public void editProperties(ResourcesService service, String projectId) {
    modeler.editProperties(service, projectId);
  }

  @Override
  public boolean createProject(ResourcesService service, String projectName, String workspaceName) {
    return modeler.createProject(service, projectName, workspaceName);
  }

  @Override
  public boolean createDocument(
      ResourcesService service,
      String projectName,
      String documentUrn,
      ProjectStorage.ResourceType documentType) {
    return modeler.createDocument(service, projectName, documentUrn, documentType);
  }

  @Override
  public boolean updateDocument(
      ResourcesService service,
      String projectName,
      String documentUrn,
      ProjectStorage.ResourceType documentType,
      String updatedContent) {
    return modeler.updateDocument(service, projectName, documentUrn, documentType, updatedContent);
  }

  @Override
  public UIView getUI() {
    return this;
  }

  @Override
  public UserScope user() {
    return modeler.user();
  }

  @Override
  public Engine engine() {
    return modeler.engine();
  }

  @Override
  public void boot() {
    modeler.boot();
  }

  @Override
  public void dispatch(UIReactor sender, UIEvent event, Object... payload) {
    modeler.dispatch(sender, event, payload);
  }

  @Override
  public void registerViewController(Object reactor) {
    modeler.registerViewController(reactor);
  }

  @Override
  public void registerPanelControllerClass(Class<? extends PanelController<?, ?>> cls) {
    modeler.registerPanelControllerClass(cls);
  }

  @Override
  public void closePanel(PanelController<?, ?> controller) {
    modeler.closePanel(controller);
  }

  @Override
  public <T extends ViewController<?>> T viewController(Class<T> controllerClass) {
    return modeler.viewController(controllerClass);
  }

  @Override
  public <P, T extends PanelView<P>> T openPanel(Class<T> panelType, P payload) {
    return modeler.openPanel(panelType, payload);
  }

  @Override
  public <T extends PanelController<?, ?>> Collection<T> getOpenPanels(
      Class<T> panelControllerClass) {
    return modeler.getOpenPanels(panelControllerClass);
  }

  @Override
  public void unregister(UIReactor reactor) {
    modeler.unregister(reactor);
  }

  @Override
  public void switchWorkbenchService(
      UIReactor requestingReactor, KlabService.ServiceCapabilities service) {
    modeler.switchWorkbenchService(requestingReactor, service);
  }

  @Override
  public void switchWorkbench(UIReactor requestingReactor, NavigableContainer container) {
    modeler.switchWorkbench(requestingReactor, container);
  }

  @Override
  public void configureWorkbench(
      UIReactor requestingReactor, NavigableDocument document, boolean shown) {
    modeler.configureWorkbench(requestingReactor, document, shown);
  }

  @Override
  public void storeView(Object... changedElements) {
    modeler.storeView(changedElements);
  }

  @Override
  public <P, T extends PanelController<P, ?>> T getPanelController(
      P payload, Class<T> panelControllerClass) {
    return modeler.getPanelController(payload, panelControllerClass);
  }

  @Override
  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentials(
      KlabService.Type serviceType, String serviceId) {
    return modeler.getCredentials(serviceType, serviceId);
  }

  @Override
  public ExternalAuthenticationCredentials.CredentialInfo setCredentials(
      String host,
      ExternalAuthenticationCredentials credentials,
      KlabService.Type serviceType,
      String serviceId) {
    return modeler.setCredentials(host, credentials, serviceType, serviceId);
  }

  public IDEContextScope requireDefaultContext() {
    if (focalScope == null) {
      var context = createDefaultContext();
      if (context == null) {
        return null;
      }
      focalScope = requireDigitalTwinPeer(context, null);
    }

    return focalScope;
  }

  @Override
  public UIController getController() {
    return modeler.getController();
  }
}

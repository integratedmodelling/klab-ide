package org.integratedmodelling.klab.ide;

import atlantafx.base.theme.Styles;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UIView;
import org.integratedmodelling.klab.api.view.modeler.Modeler;
import org.integratedmodelling.klab.api.view.modeler.views.RuntimeView;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.RuntimeViewController;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.components.*;
import org.integratedmodelling.klab.ide.model.DigitalTwinPeer;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.integratedmodelling.klab.ide.utils.NodeUtils;
import org.integratedmodelling.klab.modeler.ModelerImpl;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

public class KlabIDEController implements UIView, ServicesView, RuntimeView {

  private static Modeler modeler;
  private View currentView;
  private UserScope user;
  private Map<KlabService.Type, KlabService.ServiceCapabilities> capabilities = new HashMap<>();
  private boolean inspectorIsOn;
  private Set<View> neverSeen = EnumSet.of(View.RESOURCES, View.WORKSPACES, View.DIGITAL_TWINS);
  private static KlabIDEController _this;
  private Map<String, DigitalTwinPeer> digitalTwinPeerMap = new HashMap<>();
  private AtomicReference<Engine.Status> engineStatus = new AtomicReference<>();

  /** The "circled" (current) view in the main area. */
  public enum View {
    NOTEBOOK,
    RESOURCES,
    WORKSPACES,
    DIGITAL_TWINS,
    APPLICATIONS,
    WORLDVIEW
  }

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

  @FXML NotebookView notebook;
  @FXML Pane mainArea;
  @FXML Pane inspectorArea;

  private ServicesViewController servicesController;
  private RuntimeViewController runtimeController;
  private Distribution distribution;
  private Map<View, Button> viewButtons = new HashMap<>();
  //  private AtomicBoolean engineStarted = new AtomicBoolean(false);
  //  private AtomicBoolean engineTransitioning = new AtomicBoolean(false);

  private WorkspaceView workspaceView;
  private ResourcesView resourcesView;
  private DigitalTwinView digitalTwinView;
  private InspectorView inspectorView;
  private SessionView applicationView;
  private OntologyView ontologyView;

  public KlabIDEController() {
    _this = this;
  }

  public DigitalTwinPeer requireDigitalTwinPeer(ContextScope scope) {
    return digitalTwinPeerMap.computeIfAbsent(scope.getId(), id -> new DigitalTwinPeer(scope));
  }

  public static void setCurrentContext(ContextScope scope) {
    modeler().setCurrentContext(scope);
    for (var peer : _this.digitalTwinPeerMap.values()) {
        peer.focus(scope);
    }
  }

  public DigitalTwinPeer getDigitalTwinPeer(String id) {
    return digitalTwinPeerMap.get(id);
  }

  public void setDigitalTwinPeer(String id, DigitalTwinPeer peer) {
    digitalTwinPeerMap.put(id, peer);
  }

  public void removeDigitalTwinPeer(String id) {
    digitalTwinPeerMap.remove(id);
  }

  public static KlabIDEController instance() {
    return _this;
  }

  public static Modeler modeler() {
    return modeler;
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
    applicationView = new SessionView();
    ontologyView = new OntologyView();

    Logging.INSTANCE.info("Modeler initialized");
  }

  public <T extends BrowsablePage> T getView(View view, Class<T> cls) {
    return (T)
        switch (view) {
          //      case NOTEBOOK -> notebook;
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
    return null;
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

    var ui =
        switch (view) {
          case NOTEBOOK -> notebook;
          case RESOURCES -> resourcesView;
          case DIGITAL_TWINS -> digitalTwinView;
          case WORKSPACES -> workspaceView;
          case APPLICATIONS -> applicationView;
          case WORLDVIEW -> ontologyView;
        };

    // If it's a browser and it hasn't been seen yet, open the browser
    if (neverSeen.remove(view) && ui instanceof BrowsablePage<?,?> browsablePage) {
      //      browsablePage.showBrowser();
    }

    // switch the main area to the requested view.
    Platform.runLater(
        () -> {
          mainArea.getChildren().remove(0, mainArea.getChildren().size());
          mainArea.getChildren().add(ui);
        });
  }

  public View selectedView() {
    return this.currentView;
  }

  @FXML
  protected void initialize() {

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
    profileButton.setGraphic(new IconLabel(FontAwesomeSolid.USER_CIRCLE, 32, Color.GREY));

    viewButtons.put(View.NOTEBOOK, homeButton);
    viewButtons.put(View.DIGITAL_TWINS, digitalTwinsButton);
    viewButtons.put(View.RESOURCES, resourcesManagerButton);
    viewButtons.put(View.APPLICATIONS, sessionsButton);
    viewButtons.put(View.WORKSPACES, workspacesButton);
    viewButtons.put(View.WORLDVIEW, worldviewButton);

    inspectorButton.setOnMouseClicked(
        event -> {
          toggleInspector();
        });

    startButton.setOnMouseClicked(mouseEvent -> handleStartButtonPress());

    downloadButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.focus(Components.Type.Distribution);
          selectView(View.NOTEBOOK);
        });
    profileButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.focus(Components.Type.UserInfo);
          selectView(View.NOTEBOOK);
        });
    settingsButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.focus(Components.Type.Settings);
          selectView(View.NOTEBOOK);
        });
    reasonerButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.focus(Components.Type.ServiceInfo, KlabService.Type.REASONER);
          selectView(View.NOTEBOOK);
        });
    resourcesButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.focus(Components.Type.ServiceInfo, KlabService.Type.RESOURCES);
          selectView(View.NOTEBOOK);
        });
    resolverButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.focus(Components.Type.ServiceInfo, KlabService.Type.RESOLVER);
          selectView(View.NOTEBOOK);
        });
    runtimeButton.setOnMouseClicked(
        mouseEvent -> {
          notebook.focus(Components.Type.ServiceInfo, KlabService.Type.RUNTIME);
          selectView(View.NOTEBOOK);
        });

    for (var key : viewButtons.keySet()) {
      viewButtons.get(key).setOnMouseClicked(mouseEvent -> selectView(key));
    }

    Platform.runLater(
        () -> {
          createModeler();
          modeler.boot();
          this.user = modeler.authenticate();

          // must call explicitly because the callback won't be used before boot.
          notifyUser(this.user.getUser());
          notifyDistribution(modeler().getDistribution());

          //          if (settings.getStartServicesOnStartup().getValue()) {
          //            // TODO
          //            //      Thread.ofPlatform().start(this::toggleLocalServices);
          //          }
        });
  }

  private void handleStartButtonPress() {

    var condition =
        engineStatus.get() == null
            ? Engine.Status.EngineCondition.INOPERATIVE
            : engineStatus.get().getCondition();

    switch (condition) {
      case INOPERATIVE, ACTIVE_REMOTE_ONLY ->
          KlabIDEController.modeler().engine().startLocalServices();
      case ACTIVE_LOCAL_ONLY, ACTIVE_LOCAL_AND_REMOTE ->
          KlabIDEController.modeler().engine().stopLocalServices();
      case TRANSITIONING -> Toolkit.getDefaultToolkit().beep();
    }
  }

  private void toggleInspector() {

    setButton(
        inspectorButton,
        Theme.INSPECTOR_ICON,
        24,
        inspectorIsOn ? Theme.CURRENT_THEME.getDefaultTextColor() : Color.GOLDENROD,
        inspectorIsOn
            ? "Click to show the knowledge inspector"
            : "Click to hide the knowledge inspector");

    Platform.runLater(
        () -> {
          if (inspectorIsOn) {
            inspectorArea.getChildren().removeAll(inspectorView);
            inspectorIsOn = false;
            KlabIDEApplication.instance().setInspectorShown(false);
            NodeUtils.toggleVisibility(inspectorArea, false);
          } else {
            inspectorArea.getChildren().add(inspectorView);
            inspectorIsOn = true;
            KlabIDEApplication.instance().setInspectorShown(true);
            NodeUtils.toggleVisibility(inspectorArea, true);
          }
        });
  }

  public InspectorView getInspector() {
    return inspectorView;
  }

  @Override
  public void notifyServiceStatus(KlabService service, KlabService.ServiceStatus status) {}

  @Override
  public void engineStatusChanged(Engine.Status status) {

    engineStatus.set(status);

    switch (status.getCondition()) {
      case TRANSITIONING ->
          setButton(
              startButton,
              BootstrapIcons.CLOCK,
              16,
              Color.DARKGOLDENROD,
              "Local services are starting or stopping. Wait until status changes.");
      case INOPERATIVE, ACTIVE_REMOTE_ONLY -> {
        if (distribution != null && distribution.isUsable()) {
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
      case ACTIVE_LOCAL_ONLY, ACTIVE_LOCAL_AND_REMOTE ->
          setButton(
              startButton,
              BootstrapIcons.STOP,
              16,
              Color.DARKRED,
              "Local services are running. Click to stop them.");
    }

    for (var serviceType : KlabService.Type.operationCritical()) {

      String serviceName = serviceType.name().toLowerCase();
      var provision = status.getServicesProvision().get(serviceType);
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

      var tooltip = serviceName; // FIXME use meaningful tooltip based on provision

      setButton(button, icon, 16, color, tooltip);
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
    //    var ret = this.digitalTwinView.showDigitalTwin(scope, service);
  }

  @Override
  public void notifyDigitalTwinModified(DigitalTwin digitalTwin, Message change) {
    Logging.INSTANCE.info("Digital twin changed: " + change);
  }

  @Override
  public void notifyObservationSubmission(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Observation submitted: " + observation);
    for (var viewer : getDigitalTwinViewers(contextScope, service)) {
      viewer.submissionStarted(observation);
    }
  }

  /**
   * Retrieve any viewers for the passed DT, also managing the DT widget, if any is open.
   *
   * @param contextScope
   * @param service
   * @return
   */
  private List<DigitalTwinViewer> getDigitalTwinViewers(
      ContextScope contextScope, RuntimeService service) {
    // TODO
    return List.of();
  }

  @Override
  public void notifyObservationSubmissionAborted(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Observation submission aborted: " + observation);
    for (var viewer : getDigitalTwinViewers(contextScope, service)) {
      viewer.submissionAborted(observation);
    }
  }

  @Override
  public void notifyObservationSubmissionFinished(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Observation submission finished: " + observation);
    for (var viewer : getDigitalTwinViewers(contextScope, service)) {
      viewer.submissionFinished(observation);
    }
  }

  @Override
  public void notifyContextObservationResolved(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Context observation resolved: " + observation);
    for (var viewer : getDigitalTwinViewers(contextScope, service)) {
      viewer.setContext(observation);
    }
  }

  @Override
  public void notifyObserverResolved(
      Observation observation, ContextScope contextScope, RuntimeService service) {
    Logging.INSTANCE.info("Observer resolved: " + observation);
    for (var viewer : getDigitalTwinViewers(contextScope, service)) {
      viewer.setObserver(observation);
    }
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

  public void notifyDistribution(Distribution distribution) {

    Ikon icon = BootstrapIcons.DOWNLOAD;
    var color = Color.GREEN;
    var status = modeler().engine().getDistributionStatus();
    var tooltip = "No k.LAB distribution is available";
    var startColor = Color.GREEN;
    var startTooltip = "Local services are not available";

    if (status.getDevelopmentStatus() == Product.Status.UP_TO_DATE
    /*&& "source".equals(settings.getPrimaryDistribution().getValue())*/ ) {

      this.distribution = distribution;
      icon = BootstrapIcons.LAPTOP;
      tooltip = "Using locally available source k.LAB distribution";
      startTooltip = "Start local k.LAB services";

    } else {
      switch (status.getDownloadedStatus()) {
        case UNAVAILABLE -> {
          color = Color.RED;
          tooltip = "No distribution available. Click to download";
          startButton.setDisable(true);
        }
        case LOCAL_ONLY -> {
          startTooltip = "Start local k.LAB services";
          startButton.setDisable(false);
        }
        case UP_TO_DATE -> {
          startTooltip = "Start local k.LAB services";
          icon = BootstrapIcons.CHECK;
          startButton.setDisable(false);
        }
        case OBSOLETE -> {
          color = Color.GOLDENROD;
          tooltip = "Updated k.LAB distribution available. Click to update";
          startTooltip = "Start out-of-date local services";
          startButton.setDisable(false);
        }
      }
    }
    setButton(startButton, Material2MZ.POWER_SETTINGS_NEW, 16, startColor, startTooltip);
    setButton(downloadButton, icon, 16, color, tooltip);
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
}

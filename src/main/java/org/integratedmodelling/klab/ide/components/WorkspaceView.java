package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ResourcesNavigatorController;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.cards.ResourceSmallViewComponent;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;
import org.kordamp.ikonli.javafx.FontIcon;

public class WorkspaceView extends BrowsablePage<WorkspaceEditor, NavigableWorkspace>
    implements ResourcesNavigator {

  private final ResourcesNavigatorController controller;

  private static final Duration SERVICE_DEADLINE = Duration.ofSeconds(4);
  private static final Duration CATALOG_FRESHNESS = Duration.ofSeconds(15);

  private final WorkspaceCatalogLoader catalogLoader = new WorkspaceCatalogLoader();
  private final Map<String, List<ResourceInfo>> workspacesByService = new LinkedHashMap<>();
  private final Map<String, ResourcesService> creatableServices = new LinkedHashMap<>();
  private final Map<String, String> serviceLabels = new HashMap<>();
  private final Map<String, String> serviceIssues = new LinkedHashMap<>();
  private final Map<String, WorkspaceEditor> openEditors = new HashMap<>();
  private WorkflowUIProvider workflowUIProvider = WorkflowUIProvider.NONE;
  private List<Node> components = new ArrayList<>();
  private Node workspaceDialog;
  private boolean catalogLoading;
  private long catalogLoadedAt;

  public WorkspaceView() {
    super(
        "Choose or create a workspace from the top-left menu",
        "Workspaces contain k.LAB assets organized in projects. They are hosted by the connected Resources services");
    this.controller =
        KlabIDEController.instance().viewController(ResourcesNavigatorController.class);
    this.controller.registerView(this);
  }

  @Override
  protected void assetEditorSelected(WorkspaceEditor asset) {}

  @Override
  protected void assetEditorClosed(WorkspaceEditor editor) {
    openEditors.remove(editor.getEditedAsset().getUrn());
    editor.close();
  }

  @Override
  public String getName() {
    return "Workspaces";
  }

  @Override
  public Parent getView() {
    return this;
  }

  @Override
  public void reset() {}

  public List<ResourcesService> getServices() {
    return KlabIDEController.instance().user().getServices(ResourcesService.class).stream()
        .sorted(Comparator.comparing(service -> !Utils.URLs.isLocalHost(service.getUrl())))
        .toList();
  }

  /** Install workflow discovery and specialized stage editors for all current and future tabs. */
  public void setWorkflowUIProvider(WorkflowUIProvider workflowUIProvider) {
    this.workflowUIProvider =
        workflowUIProvider == null ? WorkflowUIProvider.NONE : workflowUIProvider;
    openEditors.values().forEach(editor -> editor.setWorkflowUIProvider(this.workflowUIProvider));
  }

  public List<ResourceInfo> getWorkspaceList() {
    return workspacesByService.values().stream()
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(info -> !openEditors.containsKey(info.getUrn()))
        .sorted(Comparator.comparing(ResourceInfo::getUrn))
        .toList();
  }

  @Override
  protected void defineBrowser(VBox browserComponents) {

    if (!catalogLoading
        && System.nanoTime() - catalogLoadedAt > CATALOG_FRESHNESS.toNanos()) {
      refreshCatalog();
    }
    browserComponents.getChildren().removeAll(components);
    components.clear();
    components.add(makeHeader("Workspaces", this::addWorkspace));
    if (workspaceDialog != null) {
      components.add(workspaceDialog);
    }
    for (var workspace : getWorkspaceList()) {
      try {
        components.add(
            new ResourceSmallViewComponent(
                workspace,
                this::raiseWorkspace,
                /* TODO */ null,
                serviceLabels.getOrDefault(workspace.getServiceId(), workspace.getServiceId())));
      } catch (Throwable e) {
        // TODO temporary - when services are up to date it should be OK
        Logging.INSTANCE.error("Error loading workspace: " + workspace);
      }
    }
    if (catalogLoading) {
      var progress = new ProgressIndicator();
      progress.setMaxSize(18, 18);
      var loading = new HBox(8, progress, new Label("Loading available workspaces…"));
      loading.setAlignment(Pos.CENTER_LEFT);
      loading.setPadding(new Insets(8));
      components.add(loading);
    }
    if (!serviceIssues.isEmpty()) {
      var issue =
          new Label(
              "Some service data is unavailable or invalid; showing the workspaces that could be loaded.");
      issue.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
      issue.setWrapText(true);
      issue.setPadding(new Insets(4, 8, 8, 8));
      components.add(issue);
    }
    browserComponents.getChildren().addAll(components);
  }

  private void refreshCatalog() {
    var services = getServices();
    var currentServiceIds =
        services.stream()
            .map(WorkspaceView::safeServiceId)
            .collect(java.util.stream.Collectors.toSet());
    workspacesByService.keySet().retainAll(currentServiceIds);
    creatableServices.keySet().retainAll(currentServiceIds);
    serviceLabels.keySet().retainAll(currentServiceIds);
    serviceIssues.keySet().retainAll(currentServiceIds);
    catalogLoading = !services.isEmpty();
    serviceIssues.clear();
    if (services.isEmpty()) {
      catalogLoadedAt = System.nanoTime();
      return;
    }
    var remaining = new AtomicInteger(services.size() * 2);
    var user = KlabIDEController.instance().user();
    for (var service : services) {
      catalogLoader.requestWorkspaces(
          service,
          user,
          SERVICE_DEADLINE,
          result ->
              Platform.runLater(
                  () -> {
                    var serviceId = safeServiceId(result.service());
                    if (result.succeeded()) {
                      workspacesByService.put(serviceId, result.workspaces());
                      if (result.discardedEntries() > 0) {
                        serviceIssues.put(
                            serviceId,
                            result.discardedEntries() + " invalid workspace entries were ignored");
                      } else {
                        serviceIssues.remove(serviceId);
                      }
                    } else {
                      serviceIssues.put(serviceId, failureMessage(result.failure()));
                    }
                    finishCatalogRequest(remaining, result.late());
                  }));
      catalogLoader.requestCapabilities(
          service,
          user,
          SERVICE_DEADLINE,
          result ->
              Platform.runLater(
                  () -> {
                    var serviceId = safeServiceId(result.service());
                    if (result.succeeded()) {
                      if (result.serviceName() != null && !result.serviceName().isBlank()) {
                        serviceLabels.put(serviceId, result.serviceName());
                      }
                      if (result.canCreate()) {
                        creatableServices.put(serviceId, result.service());
                      } else {
                        creatableServices.remove(serviceId);
                      }
                    }
                    finishCatalogRequest(remaining, result.late());
                  }));
    }
  }

  private void finishCatalogRequest(AtomicInteger remaining, boolean late) {
    if (!late && remaining.decrementAndGet() == 0) {
      catalogLoading = false;
      catalogLoadedAt = System.nanoTime();
    }
    updateBrowser();
  }

  private static String failureMessage(Throwable failure) {
    if (failure == null || failure.getMessage() == null || failure.getMessage().isBlank()) {
      return "Workspace catalogue unavailable";
    }
    return failure.getMessage();
  }

  private static String safeServiceId(ResourcesService service) {
    var serviceId = service.serviceId();
    return serviceId == null || serviceId.isBlank()
        ? Integer.toHexString(System.identityHashCode(service))
        : serviceId;
  }

  private void addWorkspace() {
    workspaceDialog = createWorkspaceDialog();
    updateBrowser();
  }

  private Node createWorkspaceDialog() {

    var availableServices =
        getServices().stream()
            .filter(service -> creatableServices.containsKey(safeServiceId(service)))
            .toList();

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setStyle("-fx-background-color: -color-neutral-muted;");
    grid.setPadding(new Insets(6, 6, 6, 6));

    TextField workspaceTitle = new TextField();
    workspaceTitle.setPromptText("Workspace name");
    TextArea description = new TextArea();
    description.setPromptText("Description");
    description.setPrefRowCount(3);
    final ComboBox<String> serviceSelector = new ComboBox<>();
    serviceSelector
        .getItems()
        .addAll(
            availableServices.stream()
                .map(
                    service ->
                        serviceLabels.getOrDefault(
                            safeServiceId(service), safeServiceId(service)))
                .toList());
    serviceSelector.setMaxWidth(Double.MAX_VALUE);
    var ok = new Button("Create");
    var cancel = new Button("Cancel");
    ok.setOnAction(
        event -> {
          if (serviceSelector.getSelectionModel().getSelectedIndex() < 0) {
            return;
          }
          createWorkspace(
              workspaceTitle.getText(),
              description.getText(),
              availableServices.get(serviceSelector.getSelectionModel().getSelectedIndex()));
          workspaceDialog = null;
          updateBrowser();
        });
    ok.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS, Styles.SMALL);
    cancel.setOnAction(
        event -> {
          workspaceDialog = null;
          updateBrowser();
        });
    var buttons = new HBox(ok, cancel);
    buttons.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
    buttons.setSpacing(4);
    cancel.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER, Styles.SMALL);
    grid.add(new FontIcon(Theme.WORKSPACE_ICON), 0, 0);
    grid.add(workspaceTitle, 1, 0);
    grid.add(new FontIcon(Theme.EDIT_ICON), 0, 1);
    grid.add(description, 1, 1);
    GridPane.setFillWidth(serviceSelector, true);
    grid.getColumnConstraints().add(new ColumnConstraints());
    grid.getColumnConstraints()
        .add(new ColumnConstraints(200, 200, Double.MAX_VALUE, Priority.ALWAYS, HPos.LEFT, true));

    grid.add(new FontIcon(Theme.LOCAL_SERVICE_ICON), 0, 2);
    grid.add(serviceSelector, 1, 2);
    grid.add(buttons, 0, 3, 2, 1);

    if (availableServices.isEmpty()) {
      grid.setDisable(true);
    } else {
      serviceSelector.getSelectionModel().select(0);
    }

    return grid;
  }

  private void createWorkspace(String workspaceName, String description, ResourcesService service) {
    if (!KlabIDEController.instance().createWorkspace(service, workspaceName, description)) {
      KlabIDEController.instance().alert(Notification.error("Workspace creation failed"));
      return;
    }

    var workspaceInfo =
        service.info(
            workspaceName,
            KlabAsset.KnowledgeClass.WORKSPACE,
            ResourceInfo.class,
            KlabIDEController.instance().user());
    if (workspaceInfo != null) {
      raiseWorkspace(workspaceInfo);
    } else {
      updateBrowser();
    }
  }

  private void raiseWorkspace(ResourceInfo resourceInfo) {

    hideBrowser();
    if (openEditors.containsKey(resourceInfo.getUrn())) {
      openEditors
          .get(resourceInfo.getUrn())
          .requestFocus(); // FIXME must remember the tabs and select(tab) - in both cases
    } else {
      var service =
          KlabIDEController.instance()
              .user()
              .findService(
                  ResourcesService.class, s -> resourceInfo.getServiceId().equals(s.serviceId()))
              .get();

      // TODO handle the unlikely case that the service is unavailable. That will throw an exception
      //  from getService

      var newEditor = new WorkspaceEditor(service, resourceInfo, this, workflowUIProvider);
      openEditors.put(resourceInfo.getUrn(), newEditor);
      addEditor(
          newEditor,
          resourceInfo.getUrn()
              + "@"
              + serviceLabels.getOrDefault(safeServiceId(service), safeServiceId(service)),
          new IconLabel(Theme.WORKSPACE_ICON, 18, "-color-fg-default"));
    }
  }

  @Override
  public void workspaceModified(
      NavigableContainer changedContainer,
      ResourceSet changes,
      Collection<NavigableAsset> changedAssets) {
    //    System.out.println("Workspace modified: " + changedContainer);
    var editor = openEditors.get(changedContainer.getUrn());
    if (editor != null && changedContainer instanceof NavigableWorkspace workspace) {
      editor.updateWorkspace(workspace, changes, changedAssets);
    }
  }

  @Override
  public void engineStatusChanged(Engine.Status status) {}

  @Override
  public NavigableContainer getVisualizedWorkspace(String workspace) {
    var editor = this.openEditors.get(workspace);
    return editor == null ? null : editor.getEditedAsset();
  }
}

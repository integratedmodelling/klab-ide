package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.engine.Engine;
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
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.ColumnConstraints;
import javafx.geometry.HPos;
import javafx.geometry.Insets;

import java.util.*;

public class WorkspaceView extends BrowsablePage<WorkspaceEditor, NavigableWorkspace>
    implements ResourcesNavigator {

  private final ResourcesNavigatorController controller;

  private final Map<String, ResourceInfo> workspaces = new HashMap<>();
  private final Map<String, WorkspaceEditor> openEditors = new HashMap<>();
  //  private final Map<ResourceInfo, ResourcesService> services = new HashMap<>();
  private String localServiceId;
  private List<Node> components = new ArrayList<>();
  private Node workspaceDialog;

  public WorkspaceView() {
    this.controller =
        KlabIDEController.instance().viewController(ResourcesNavigatorController.class);
    this.controller.registerView(this);
  }

  @Override
  protected void assetEditorSelected(WorkspaceEditor asset) {}

  @Override
  protected void assetEditorClosed(WorkspaceEditor editor) {
    workspaces.remove(editor.getEditedAsset().getUrn());
    openEditors.remove(editor.getEditedAsset().getUrn());
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
        /* .filter(
        s ->
            s.capabilities(KlabIDEController.modeler().user())
                .getPermissions()
                .contains(CRUDOperation.CREATE))*/
        .sorted(
            (s1, s2) ->
                Utils.URLs.isLocalHost(s1.getUrl()) && !Utils.URLs.isLocalHost(s2.getUrl())
                    ? -1
                    : (Utils.URLs.isLocalHost(s2.getUrl()) ? 0 : 1))
        .toList();
  }

  public List<ResourceInfo> getWorkspaceList() {
    List<ResourceInfo> ret = new ArrayList<>();
    for (var rService : getServices()) {
      for (var workspace :
          rService.capabilities(KlabIDEController.instance().user()).getWorkspaceNames()) {
        if (openEditors.containsKey(workspace)) {
          continue;
        }
        ret.add(rService.resourceInfo(workspace, KlabIDEController.instance().user()));
      }
    }
    return ret;
  }

  @Override
  protected void defineBrowser(VBox browserComponents) {

    browserComponents.getChildren().removeAll(components);
    components.clear();
    components.add(makeHeader("Workspaces", this::addWorkspace));
    if (workspaceDialog != null) {
      components.add(workspaceDialog);
    }
    for (var workspace : getWorkspaceList()) {
      components.add(new ResourceSmallViewComponent(workspace, this::raiseWorkspace, /* TODO */ null));
    }
    browserComponents.getChildren().addAll(components);
  }

  private void addWorkspace() {
    workspaceDialog = createWorkspaceDialog();
    updateBrowser();
  }

  private Node createWorkspaceDialog() {

    var availableServices =
        KlabIDEController.instance().user().getServices(ResourcesService.class).stream()
            .filter(
                s ->
                    s.capabilities(KlabIDEController.instance().user())
                        .getPermissions()
                        .contains(CRUDOperation.CREATE))
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
        .addAll(availableServices.stream().map(ResourcesService::serviceName).toList());
    serviceSelector.setMaxWidth(Double.MAX_VALUE);
    var ok = new Button("Create");
    var cancel = new Button("Cancel");
    var service = (ResourcesService) null;
    ok.setOnAction(
        event -> {
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
      if (!service.createWorkspace(
          workspaceName,
          Metadata.create(Metadata.DC_COMMENT, description),
          KlabIDEController.instance().user())) {
        KlabIDEController.instance().alert(Notification.error("Workspace creation failed"));
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

      var newEditor = new WorkspaceEditor(service, resourceInfo, this);
      openEditors.put(resourceInfo.getUrn(), newEditor);
      addEditor(
          newEditor,
          resourceInfo.getUrn() + "@" + service.serviceName(),
          new FontIcon(Theme.WORKSPACE_ICON));
    }
  }

  @Override
  public void workspaceModified(
      NavigableContainer changedContainer,
      ResourceSet changes,
      Collection<NavigableAsset> changedAssets) {
    System.out.println("Workspace modified: " + changedContainer);
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

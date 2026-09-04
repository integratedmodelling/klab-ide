package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.IOException;
import java.util.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableFolder;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.TreeSearchField;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.integratedmodelling.klab.modeler.model.*;
import org.integratedmodelling.klabeditor.Document;
import org.integratedmodelling.klabeditor.MonacoEditorView;
import org.integratedmodelling.klabeditor.lsp.KlabLspService;
import org.integratedmodelling.klabeditor.lsp.LspDocumentSession;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import org.kordamp.ikonli.materialdesign.MaterialDesign;
import org.kordamp.ikonli.octicons.Octicons;

public class WorkspaceEditor extends EditorPage<NavigableWorkspace, NavigableAsset> {

  private static final String THEME_ICON_COLOR = "-color-fg-default";

  private final ResourcesService service;
  private NavigableWorkspace workspace;
  private final WorkspaceView view;
  private WorkflowUIProvider workflowUIProvider;
  private TreeItem<NavigableAsset> root;
  private ProgressBar progressBar;
  private TreeView<NavigableAsset> treeView;
  private final Map<Node, LspDocumentSession> lspSessions = new IdentityHashMap<>();
  private final Set<String> assetsWithFlows = new HashSet<>();

  /** Saved source snapshots waiting for their corresponding parsed workspace updates. */
  private final Map<String, Deque<String>> pendingSavedSources = new HashMap<>();

  public WorkspaceEditor(ResourcesService service, ResourceInfo resourceInfo, WorkspaceView view) {
    this(service, resourceInfo, view, WorkflowUIProvider.NONE);
  }

  public WorkspaceEditor(
      ResourcesService service,
      ResourceInfo resourceInfo,
      WorkspaceView view,
      WorkflowUIProvider workflowUIProvider) {
    super(
        new NavigableWorkspace(
            service.retrieve(
                resourceInfo.getUrn(), Workspace.class, KlabIDEController.instance().user())));
    this.service = service;
    this.view = view;
    this.workflowUIProvider =
        workflowUIProvider == null ? WorkflowUIProvider.NONE : workflowUIProvider;
    this.workspace = getEditedAsset();
    try {
      service.getFlows(true, KlabIDEController.instance().user()).stream()
          .map(Flow::getAssetUrn)
          .filter(Objects::nonNull)
          .forEach(assetsWithFlows::add);
    } catch (RuntimeException ignored) {
      // A disconnected service must not prevent the workspace from opening.
    }
    // lock all projects that let us
    for (var project : workspace.getProjects()) {
      if (service.lockProject(project.getUrn(), KlabIDEController.instance().user())
          && project instanceof NavigableProject navigableProject) {
        navigableProject.setLocked(true);
      }
    }
  }

  public void setWorkflowUIProvider(WorkflowUIProvider workflowUIProvider) {
    this.workflowUIProvider =
        workflowUIProvider == null ? WorkflowUIProvider.NONE : workflowUIProvider;
  }

  private void setupWorkflowMenu(ContextMenu contextMenu, NavigableAsset asset) {
    var scope = KlabIDEController.instance().user();
    List<Workflow> workflows;
    List<Flow> flows;
    try {
      workflows =
          Optional.ofNullable(workflowUIProvider.availableWorkflows(asset, scope))
              .orElseGet(List::of);
      flows =
          service.getFlows(true, scope).stream()
              .filter(flow -> Objects.equals(asset.getUrn(), flow.getAssetUrn()))
              .toList();
    } catch (Throwable error) {
      KlabIDEController.instance().handleNotification(Notification.error(error));
      return;
    }
    var participant = WorkflowParticipant.from(scope);
    var workflowMenus = new ArrayList<Menu>();

    if (participant.getRoles().contains(WorkflowRole.EDITOR)
        || participant.getRoles().contains(WorkflowRole.ADMIN)) {
      var start = new Menu("Start workflow");
      workflows.stream()
          .filter(Objects::nonNull)
          .filter(participant::isWorkflowPermitted)
          .filter(workflow -> workflow.admitsAsset(assetType(asset)))
          .sorted(Comparator.comparing(WorkspaceEditor::workflowName))
          .forEach(
              workflow -> {
                var item = new MenuItem(workflowName(workflow));
                item.setOnAction(event -> startWorkflow(asset, workflow));
                start.getItems().add(item);
              });
      if (!start.getItems().isEmpty()) workflowMenus.add(start);
    }

    addFlowSubmenu(workflowMenus, "Open flows", flows, Flow.Status.ACTIVE);
    addFlowSubmenu(workflowMenus, "Closed flows", flows, Flow.Status.CLOSED);
    if (!workflowMenus.isEmpty()) {
      if (!contextMenu.getItems().isEmpty()) contextMenu.getItems().add(new SeparatorMenuItem());
      contextMenu.getItems().addAll(workflowMenus);
    }
  }

  private void addFlowSubmenu(
      List<Menu> workflowMenus, String title, List<Flow> flows, Flow.Status status) {
    var submenu = new Menu(title);
    flows.stream()
        .filter(flow -> flow.getStatus() == status)
        .sorted(
            Comparator.comparing(
                Flow::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .forEach(
            flow -> {
              Workflow workflow;
              try {
                workflow =
                    service.getWorkflow(flow.getWorkflowId(), KlabIDEController.instance().user());
              } catch (Throwable error) {
                workflow = null;
              }
              var name = workflow == null ? flow.getWorkflowId() : workflowName(workflow);
              var item = new MenuItem(name + " — " + shortFlowId(flow));
              var resolvedWorkflow = workflow;
              item.setOnAction(event -> openWorkflow(flow, resolvedWorkflow));
              submenu.getItems().add(item);
            });
    if (!submenu.getItems().isEmpty()) workflowMenus.add(submenu);
  }

  private void startWorkflow(NavigableAsset asset, Workflow workflow) {
    try {
      var participant = WorkflowParticipant.from(KlabIDEController.instance().user());
      if (!participant.isWorkflowPermitted(workflow)) {
        throw new IllegalStateException("Workflow is not permitted: " + workflowName(workflow));
      }
      var assetType = assetType(asset);
      if (!workflow.admitsAsset(assetType)) {
        throw new IllegalStateException(
            "Workflow " + workflowName(workflow) + " does not admit " + assetType + " assets");
      }
      var initialTransition =
          workflow.getTransitions().values().stream()
              .filter(transition -> transition.getSourceStates().contains(Workflow.INIT))
              .filter(transition -> participant.hasAnyRole(transition.getRoles()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No permitted initial stage for " + workflowName(workflow)));
      var initial = Flow.State.create();
      initial.setSchemaId(initialTransition.getTargetState());
      initial.setAssetUrn(asset.getUrn());
      initial.setAssetType(assetType);
      initial.setOwner(participant.getIdentity());
      initial.getAssignees().add(participant.getIdentity());
      var flow =
          workflowUIProvider.draftFlow(
              workflow, initial, KlabIDEController.instance().user());
      openWorkflow(flow, workflow);
    } catch (Throwable error) {
      KlabIDEController.instance().handleNotification(Notification.error(error));
    }
  }

  private static KlabAsset.KnowledgeClass assetType(NavigableAsset asset) {
    if (asset == null || asset.getDelegate() == null) return null;
    try {
      return KlabAsset.classify(asset.getDelegate());
    } catch (RuntimeException unsupportedAsset) {
      return null;
    }
  }

  private void openWorkflow(Flow flow, Workflow knownWorkflow) {
    try {
      var workflow =
          knownWorkflow == null
              ? service.getWorkflow(flow.getWorkflowId(), KlabIDEController.instance().user())
              : knownWorkflow;
      var editorKey = "workflow:" + flow.getId();
      var editor =
          new WorkflowEditor(
              service,
              KlabIDEController.instance().user(),
              workflow,
              flow,
              workflowUIProvider::stageEditor,
              () -> closeAuxiliaryEditor(editorKey),
              initialized -> {
                assetsWithFlows.add(initialized.getAssetUrn());
                if (treeView != null) treeView.refresh();
              },
              deleted -> {
                try {
                  boolean anotherFlow =
                      service.getFlows(true, KlabIDEController.instance().user()).stream()
                          .anyMatch(
                              candidate ->
                                  Objects.equals(deleted.getAssetUrn(), candidate.getAssetUrn()));
                  if (!anotherFlow) assetsWithFlows.remove(deleted.getAssetUrn());
                } catch (RuntimeException ignored) {
                  // The deletion succeeded; refresh the indicator on the next service-backed view.
                }
                if (treeView != null) treeView.refresh();
              });
      showAuxiliaryEditor(
          editorKey, workflowName(workflow) + " — workflow", editor);
    } catch (Throwable error) {
      KlabIDEController.instance().handleNotification(Notification.error(error));
    }
  }

  private static String workflowName(Workflow workflow) {
    return workflow.getName() == null || workflow.getName().isBlank()
        ? workflow.getId()
        : workflow.getName();
  }

  private static String shortFlowId(Flow flow) {
    var id = flow.getId();
    return id == null || id.length() <= 8 ? String.valueOf(id) : id.substring(0, 8);
  }

  @Override
  protected void onVisualize(boolean visibleAfterCall) {
    KlabIDEController.instance().setFocalEditor(this, visibleAfterCall);
  }

  @Override
  protected void configureDigitalTwinWidget(DigitalTwinControlPanel digitalTwinMinified) {
    // TODO contents
    digitalTwinMinified.setOnDragOver(
        event -> {
          if (event.getGestureSource() == this.treeView) {
            event.acceptTransferModes(TransferMode.ANY);
          }
          event.consume();
        });
    digitalTwinMinified.setOnDragDropped(
        event -> {
          if (event.getGestureSource() == this.treeView) {
            event.setDropCompleted(true);
            event.consume();
          }
        });
  }

  @Override
  protected TreeView<NavigableAsset> createContentTree() {

    treeView = new TreeView<>(this.root = defineTree(workspace));
    treeView.setCellFactory(p -> new AssetTreeCell(this));
    treeView.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    treeView.setShowRoot(false);
    treeView.setPrefWidth(340);

    treeView.setOnDragDetected(
        event -> {
          TreeItem<NavigableAsset> item = treeView.getSelectionModel().getSelectedItem();
          if (item != null) {
            // TODO check if this is draggable in the current conditions
            digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.RECEIVING);
            showDigitalTwinControlPanel();
            var dragboard = treeView.startDragAndDrop(TransferMode.ANY);
            var content = new ClipboardContent();
            content.putString(item.getValue().getUrn());
            dragboard.setContent(content);
            // TODO paint the dragged asset appropriately
            //                dragboard.setDragView(Theme.getImageForAsset(item.getValue()));
            event.consume();
          }
        });

    treeView.setOnDragDone(
        event -> {
          TreeItem<NavigableAsset> item = treeView.getSelectionModel().getSelectedItem();
          if (item != null && event.isAccepted()) {
            handleAssetDrop(item.getValue());
          }
          event.consume();
        });

    return treeView;
  }

  private void setupDocumentMenu(ContextMenu contextMenu, KlabDocument<?> document) {
    if (document instanceof NavigableAsset asset) {
      if (document instanceof KActorsBehavior behavior) {
        var localRuntime =
            KlabIDEController.instance().user().getServices(RuntimeService.class).stream()
                .filter(s -> s.isLocal())
                .findFirst();
        var editLocally =
            new MenuItem(
                "Edit and run locally",
                new IconLabel(MaterialDesign.MDI_CLOUD_DOWNLOAD, 16, THEME_ICON_COLOR));
        editLocally.setOnAction(event -> editBehaviorLocally(behavior, asset));
        if (localRuntime.isEmpty()) {
          editLocally.setDisable(true);
        }
        contextMenu.getItems().addAll(editLocally, new SeparatorMenuItem());
      }
      var delete = new MenuItem("Delete", new IconLabel(Material2AL.DELETE, 16, THEME_ICON_COLOR));
      delete.setOnAction(e -> KlabIDEController.instance().deleteAsset(service, asset));
      contextMenu.getItems().add(delete);
    }
  }

  private void editBehaviorLocally(KActorsBehavior behavior, NavigableAsset asset) {
    try {
      var project = behavior.getProjectName();
      if (project == null || project.isBlank()) {
        var parent = asset.parent(NavigableProject.class);
        project = parent == null ? null : parent.getUrn();
      }
      var managedBehavior =
          service.retrieve(
              behavior.getUrn(), KActorsBehavior.class, KlabIDEController.instance().user());
      if (managedBehavior == null || project == null || project.isBlank()) {
        KlabIDEController.instance()
            .handleNotification(
                Notification.error("Unable to retrieve the project behavior for local editing"));
        return;
      }
      var checkout =
          ManagedBehaviorMirrors.getDefault()
              .checkout(
                  service.serviceId(),
                  project,
                  managedBehavior.getUrn(),
                  managedBehavior.getSourceCode());
      KlabIDEController.instance().openBehaviorFile(checkout.file());
    } catch (IOException | IllegalArgumentException e) {
      KlabIDEController.instance()
          .handleNotification(Notification.error("Unable to create the local behavior mirror", e));
    }
  }

  private void setupProjectMenu(ContextMenu contextMenu, NavigableProject project) {
    var lockUnlock =
        new MenuItem(
            project.isLocked() ? "Unlock" : "Lock",
            new IconLabel(
                project.isLocked() ? BootstrapIcons.LOCK : BootstrapIcons.UNLOCK,
                16,
                THEME_ICON_COLOR));
    lockUnlock.setOnAction(
        e -> {
          if (project.isLocked()) {
            service.unlockProject(project.getUrn(), KlabIDEController.instance().user());
            project.setLocked(false);
          } else {
            service.lockProject(project.getUrn(), KlabIDEController.instance().user());
            project.setLocked(true);
          }
        });

    var projectSettings =
        new MenuItem(
            "Project settings...",
            new IconLabel(Theme.WORKSPACE_SETTINGS_ICON, 16, THEME_ICON_COLOR));
    projectSettings.setOnAction(
        e -> {
          /* TODO */
        });

    var deleteProject =
        new MenuItem("Delete project...", new IconLabel(Material2AL.DELETE, 16, THEME_ICON_COLOR));
    deleteProject.setOnAction(
        e -> {
          deleteProject(project);
        });

    var newMenu = new Menu("New", new IconLabel(CarbonIcons.DOCUMENT_ADD, 16, THEME_ICON_COLOR));
    var newNamespace =
        new MenuItem("Namespace...", new IconLabel(Theme.NAMESPACE_ICON, 16, THEME_ICON_COLOR));
    var newBehavior =
        new MenuItem(
            "Behavior, Application or test case...",
            new IconLabel(Theme.BEHAVIOR_ICON, 16, THEME_ICON_COLOR));
    var newOntology =
        new MenuItem("Ontology...", new IconLabel(Theme.ONTOLOGY_ICON, 16, THEME_ICON_COLOR));
    var newObservationStrategy =
        new MenuItem(
            "Observation strategy...", new IconLabel(Theme.OBSERVATION_ICON, 16, THEME_ICON_COLOR));

    newNamespace.setOnAction(
        actionEvent -> {
          createNewDocument(project, ProjectStorage.ResourceType.MODEL_NAMESPACE);
        });
    newBehavior.setOnAction(
        actionEvent -> {
          createNewDocument(project, ProjectStorage.ResourceType.BEHAVIOR);
        });
    newOntology.setOnAction(
        actionEvent -> {
          createNewDocument(project, ProjectStorage.ResourceType.ONTOLOGY);
        });
    newObservationStrategy.setOnAction(
        actionEvent -> {
          createNewDocument(project, ProjectStorage.ResourceType.STRATEGY);
        });

    newMenu.getItems().addAll(newNamespace, newBehavior, newOntology, newObservationStrategy);

    var teamMenu = new Menu("Team", new IconLabel(Material2MZ.PEOPLE, 16, THEME_ICON_COLOR));

    if (project.getRepositoryState().getOverallStatus() == RepositoryState.Status.UNTRACKED) {

      var newProject = new MenuItem("Add to version control...");
      teamMenu.getItems().add(newProject);

    } else {

      for (var op : RepositoryState.Operation.values()) {

        // these are confusing unless you know what they do
        if (!KlabIDEController.instance()
                .engine()
                .getSettings()
                .get(Setting.LIST_LOCAL_COMMIT_OPERATIONS, Boolean.class)
            && (op == RepositoryState.Operation.PUBLISH_CHANGES
                || op == RepositoryState.Operation.SAVE_CHANGES)) {
          continue;
        }

        var teamOperation =
            new MenuItem(
                op.description(),
                new IconLabel(
                    switch (op) {
                      case SYNC_AND_PUBLISH -> MaterialDesign.MDI_CLOUD_SYNC;
                      case GET_LATEST -> MaterialDesign.MDI_CLOUD_DOWNLOAD;
                      case COMMIT_AND_SWITCH -> MaterialDesign.MDI_SOURCE_BRANCH;
                      case DISCARD_LOCAL_CHANGES -> MaterialDesign.MDI_RELOAD;
                      case MERGE_CHANGES_FROM -> MaterialDesign.MDI_GIT;
                      case PUBLISH_CHANGES -> MaterialDesign.MDI_CLOUD_UPLOAD;
                      case SAVE_CHANGES -> MaterialDesign.MDI_CONTENT_SAVE;
                    },
                    16,
                    THEME_ICON_COLOR));
        teamOperation.setOnAction(
            e -> {
              KlabIDEController.instance()
                  .manageProject(
                      service, project.getUrn(), op, getOperationParameters(project, op));

              // TODO the new branch/switch menus should be submenus with the existing branches +
              //  New branch...

            });
        teamMenu.getItems().add(teamOperation);
      }

      // TODO add Untrack after separator
      var detach =
          new MenuItem(
              "Detach from version control",
              new IconLabel(CarbonIcons.UNLINK, 16, THEME_ICON_COLOR));
      detach.setOnAction(
          e -> {
            /* TODO */
          });
      teamMenu.getItems().add(new SeparatorMenuItem());
      teamMenu.getItems().add(detach);
    }
    contextMenu.getItems().add(newMenu);
    contextMenu.getItems().add(teamMenu);
    contextMenu.getItems().add(new SeparatorMenuItem());
    contextMenu.getItems().addAll(lockUnlock, projectSettings, deleteProject);
  }

  @Override
  protected Node createTopMenu() {
    var hBox = new HBox();
    hBox.setAlignment(Pos.CENTER_LEFT);

    var expand = new Button("", new IconLabel(CarbonIcons.EXPAND_ALL, 16, THEME_ICON_COLOR));
    var collapse = new Button("", new IconLabel(CarbonIcons.COLLAPSE_ALL, 16, THEME_ICON_COLOR));

    expand.setOnAction(
        actionEvent -> {
          expandTreeView(treeView.getRoot());
        });
    collapse.setOnAction(
        actionEvent -> {
          collapseTreeView(treeView.getRoot());
          treeView.getRoot().setExpanded(true);
        });

    expand.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    collapse.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    var workspaceSettings = new Button("");
    workspaceSettings.setGraphic(
        new IconLabel(Theme.WORKSPACE_SETTINGS_ICON, 16, THEME_ICON_COLOR));
    workspaceSettings.setOnAction(actionEvent -> showWorkspaceSettings());
    workspaceSettings.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);

    var searchField =
        new TreeSearchField<>(
            this.treeView, (q, asset) -> asset.getUrn().toLowerCase().contains(q));
    HBox.setHgrow(searchField, Priority.ALWAYS);

    var addProject = new Button("");
    addProject.setGraphic(new IconLabel(Theme.ADD_PROJECT_ICON, 16, THEME_ICON_COLOR));
    addProject.setOnAction(
        actionEvent -> {
          createNewProject();
        });
    addProject.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    hBox.getChildren().addAll(addProject, workspaceSettings, searchField, expand, collapse);
    return new VBox(hBox /*, new Separator()*/);
  }

  private void showWorkspaceSettings() {
    // TODO add settings tab or switch to it
  }

  private boolean createNewProject() {
    var dialog = new TextInputDialog();
    dialog.setTitle("Create a new project");
    dialog.setHeaderText("Create a new project in the " + workspace.getUrn() + " workspace");
    dialog.setContentText("URN of new project:");
    dialog.initOwner(getScene().getWindow());
    var urn = dialog.showAndWait().orElse(null);
    return KlabIDEController.instance().createProject(service, urn, workspace.getUrn());
  }

  private boolean createNewDocument(
      NavigableProject project, ProjectStorage.ResourceType knowledgeClass) {
    var dialog = new TextInputDialog();
    dialog.setTitle("Create a new " + knowledgeClass.name().toLowerCase());
    dialog.setHeaderText(
        "Create a new "
            + knowledgeClass.name().toLowerCase()
            + " document in project "
            + project.getUrn()
            + "");
    dialog.setContentText("URN of new document:");
    dialog.initOwner(getScene().getWindow());
    var urn = dialog.showAndWait().orElse(null);
    return KlabIDEController.instance()
        .createDocument(service, project.getUrn(), urn, knowledgeClass);
  }

  public void deleteProject(NavigableProject project) {
    var alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Delete project " + project.getUrn());
    alert.setHeaderText("You are about to remove the project. Please confirm");
    alert.setContentText(
        "Removing this project will also remove all assets in it for all users. All data will be deleted permanently. ");
    ButtonType yesBtn = new ButtonType("Confirm", ButtonBar.ButtonData.YES);
    ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    alert.getButtonTypes().setAll(yesBtn, cancelBtn);
    alert.initOwner(getScene().getWindow());
    var result = alert.showAndWait();
    if (!result.isEmpty() && result.get().getButtonData() == ButtonBar.ButtonData.YES) {
      KlabIDEController.instance().deleteProject(service, project.getUrn());
    }
  }

  private String[] getOperationParameters(NavigableProject project, RepositoryState.Operation op) {
    // TODO use alerts and config, including confirmation
    return new String[] {};
  }

  private void handleAssetDrop(NavigableAsset value) {

    var scope = KlabIDEController.instance().requireDefaultContext();
    if (scope == null) {
      // This shouldn't happen when the drop action and panel become smarter
      digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.IDLE);
      KlabIDEController.instance()
          .handleNotification(
              Notification.error("No scope selected and no local runtime service available."));
      return;
    }
    digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.COMPUTING);
    digitalTwinControlPanel.setDigitalTwin(scope, true);
    KlabIDEController.instance()
        .observe(scope, value, /* TODO check drop params */ false)
        .exceptionally(
            throwable -> {
              if (isCancellation(throwable)) {
                digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.IDLE);
              } else {
                digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.ERROR);
                KlabIDEController.instance()
                    .handleNotifications(List.of(Notification.error(throwable)));
              }
              return Observation.EMPTY_OBSERVATION;
            })
        .thenApply(
            observation -> {
              digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.IDLE);
              return observation;
            });
  }

  private static boolean isCancellation(Throwable failure) {
    while (failure != null) {
      if (failure instanceof java.util.concurrent.CancellationException) {
        return true;
      }
      failure = failure.getCause();
    }
    return false;
  }

  private static final class AssetTreeCell extends TreeCell<NavigableAsset> {

    WorkspaceEditor editor;

    public AssetTreeCell(WorkspaceEditor workspaceEditor) {
      this.editor = workspaceEditor;
    }

    @Override
    protected void updateItem(NavigableAsset asset, boolean empty) {
      super.updateItem(asset, empty);
      if (asset != null && !empty) {
        setText(null);
        var icon = getTreeGraphics(asset);
        var label = new Label(Theme.getLabel(asset));
        if (asset instanceof NavigableProject project && project.isLocked()) {
          // The cell text became a graphic child when flow indicators were introduced, so preserve
          // the established locked-project cue on the label itself.
          label.setStyle("-fx-text-fill: -color-success-fg;");
        }
        if (editor.assetsWithFlows.contains(asset.getUrn())) {
          var dot = new Label("●");
          dot.setStyle("-fx-text-fill: -color-accent-fg; -fx-font-size: 9px;");
          var graphic = new HBox(5, icon, label, dot);
          graphic.setAlignment(Pos.CENTER_LEFT);
          setGraphic(graphic);
        } else {
          var graphic = new HBox(5, icon, label);
          graphic.setAlignment(Pos.CENTER_LEFT);
          setGraphic(graphic);
        }
        setOnContextMenuRequested(
            event -> {
              var contextMenu = new ContextMenu();
              contextMenu.setAutoHide(true);
              switch (asset) {
                case NavigableProject project -> {
                  editor.setupProjectMenu(contextMenu, project);
                }
                case KlabDocument<?> document -> {
                  editor.setupDocumentMenu(contextMenu, document);
                }
                default -> {}
              }
              editor.setupWorkflowMenu(contextMenu, asset);
              if (!contextMenu.getItems().isEmpty()) {
                contextMenu.show(this, event.getScreenX(), event.getScreenY());
              }
              //                  }
            });
        switch (asset) {
          case NavigableProject navigableProject -> {
            if (navigableProject.isLocked()) {
              setStyle("-fx-text-fill: -color-success-fg;");
            }
          }
          case NavigableDocument navigableProject -> {
            // leave these - there is an unclear style "leaking" phenomenon otherwise
            setStyle(null);
          }
          case NavigableKimConceptStatement navigableProject -> {
            setStyle(null);
          }
          case NavigableKimModel navigableProject -> {
            setStyle(null);
          }
          default -> {
            setStyle(null);
          }
        }

      } else {
        setText(null);
        setGraphic(null);
      }
    }
  }

  private void setWaiting(boolean b) {
    //    Platform.runLater(() -> this.progressBar.progressProperty().setValue(b ? -1d : 0d));
  }

  private TreeItem<NavigableAsset> defineTree(NavigableAsset asset) {
    var root = new TreeItem<>(asset);
    root.setGraphic(getTreeGraphics(asset));
    for (var child : asset.children()) {
      root.getChildren().add(defineTree(child));
    }
    return root;
  }

  private static IconLabel getTreeGraphics(NavigableAsset asset) {
    return asset instanceof KActorsAction
        ? new IconLabel(Theme.ACTION_ICON, 15, THEME_ICON_COLOR)
        : Theme.getGraphics(asset);
  }

  @Override
  protected Node createEditor(NavigableAsset asset) {
    if (asset instanceof NavigableKlabDocument<?, ?> document) {
      boolean lspAvailable =
          KlabLspService.getInstance()
              .ensureInitialized(
                  KlabIDEController.instance().getLanguageServer(),
                  KlabIDEController.instance().user());
      if (!lspAvailable) {
        KlabIDEController.instance()
            .handleNotification(
                Notification.error("LSP Server is not running: no edit support available"));
      }

      String languageId =
          document.getLanguage().languageId(); // even if Monaco treats it as plain-text for now

      String theme = Theme.CURRENT_THEME.isDark() ? "vs-dark" : "vs";

      // For now use the Urn
      // TODO these buttons etc. should be in a dedicated DocumentEditor subclass
      String documentUri =
          "inmemory:///klab/" + document.getUrn() + "." + document.getLanguage().fileExtension();
      var saveButton =
          IconButton.of(Codicons.SAVE, 12, Theme.FOREGROUND_COLOR, Theme.FOREGROUND_COLOR, null);
      saveButton.setTooltip(new Tooltip("Save"));
      var status = new Label("Ready");

      var ret =
          new MonacoEditorView(
              documentUri, content -> Platform.runLater(() -> saveDocument(content, asset))) {
            @Override
            protected Collection<BarComponent> createHeaderBarComponents() {

              // TODO save, review mode on the left

              var lineNumbers =
                  IconButton.toggle(
                      Material2AL.FORMAT_LIST_NUMBERED,
                      12,
                      () -> {
                        this.setLineNumbers(!this.isLineNumbersVisible());
                        return this.isLineNumbersVisible();
                      });
              lineNumbers.setToggled(true);
              var minimap =
                  IconButton.toggle(
                      BootstrapIcons.LAYOUT_SIDEBAR_REVERSE,
                      12,
                      () -> {
                        this.setMinimapVisible(!this.isMinimapVisible());
                        return this.isMinimapVisible();
                      });
              lineNumbers.setToggled(this.isLineNumbersVisible());
              minimap.setToggled(this.isMinimapVisible());
              lineNumbers.setTooltip(new Tooltip("Toggle line numbers"));
              minimap.setTooltip(new Tooltip("Toggle the minimap"));

              var reviewMode =
                  IconButton.toggle(
                      Octicons.CODE_REVIEW_24,
                      12,
                      () -> {
                        return toggleReviewMode(document, this);
                      });

              reviewMode.setToggled(this.isReviewMode());
              // TODO enable only if the doc has some public flow or if user can
              //  open a review
              reviewMode.setTooltip(new Tooltip("Toggle review mode"));

              return List.of(
                  new BarComponent(saveButton, BarSide.LEFT),
                  new BarComponent(reviewMode, BarSide.LEFT),
                  new BarComponent(lineNumbers, BarSide.RIGHT),
                  new BarComponent(minimap, BarSide.RIGHT));
            }

            @Override
            protected Collection<BarComponent> createStatusBarComponents() {

              return List.of(new BarComponent(status, BarSide.LEFT));
            }
          };

      saveButton.setDisable(true);
      saveButton.setOnAction(
          event -> {
            Platform.runLater(() -> saveDocument(ret.getText(), asset));
          });

      ret.setMinimapVisible(
          KlabIDEController.instance()
              .engine()
              .getSettings()
              .get(Setting.START_WITH_MINIMAP_VISIBLE, Boolean.class));
      ret.setLineNumbers(
          KlabIDEController.instance()
              .engine()
              .getSettings()
              .get(Setting.START_WITH_LINE_NUMBERS_VISIBLE, Boolean.class));

      ret.runAfterEditorRendered(() -> ret.markNotifications(document.getNotifications(), false));
      ret.loadEditor(document.getSourceCode(), languageId, theme);

      ret.setCursorPositionListener(
          offset -> {
            if (isEditorSelected(document)) {
              // TODO put description in status bar; check if inspector is open
              //  and queue descriptor
              focusTreeOn(document.getAssetsAt(offset));
            }
          });
      ret.setOnDirtyChanged(
          dirty -> {
            Platform.runLater(
                () -> {
                  saveButton.setDisable(!dirty);
                  status.setText(dirty ? "Modified" : "Ready");
                });
          });
      if (lspAvailable) {
        var session = new LspDocumentSession(ret, languageId, document.getSourceCode());
        lspSessions.put(ret, session);
      }
      return ret;
    }
    return null;
  }

  @Override
  protected void disposeEditor(NavigableAsset asset, Node editor) {
    var session = lspSessions.remove(editor);
    if (session != null) {
      session.close();
    }
  }

  private void saveDocument(String text, NavigableAsset asset) {
    //    Logging.INSTANCE.info("Save document requested: " + asset.getUrn());
    if (asset instanceof KlabDocument<?> document) {
      pendingSavedSources
          .computeIfAbsent(document.getUrn(), ignored -> new ArrayDeque<>())
          .add(text);
      KlabIDEController.instance()
          .updateDocument(
              service,
              asset.parent(NavigableProject.class).getUrn(),
              document.getUrn(),
              ProjectStorage.ResourceType.classify(document),
              text);
    }
  }

  public boolean toggleReviewMode(NavigableKlabDocument<?, ?> document, MonacoEditorView editor) {
    var ret = !editor.isReviewMode();
    editor.setReviewMode(ret);
    // TODO the rest
    return ret;
  }

  public void updateWorkspace(
      NavigableWorkspace workspace, ResourceSet changes, Collection<NavigableAsset> changedAssets) {
    // Resource sets can contain a notification or an UPDATE that does not alter the navigable
    // model.  In that case there is nothing to repaint.  In particular, do not refresh the whole
    // tree: TreeView.refresh() also resets the visual position of the browsing pane.
    if (changes.isEmpty()) {
      return;
    }
    var resourceChanges = Utils.Resources.collectChanges(changes);
    var hasChangedAssets = changedAssets != null && !changedAssets.isEmpty();

    this.workspace = workspace;
    Platform.runLater(
        () -> {
          var selectedItem = treeView.getSelectionModel().getSelectedItem();
          var selectedAsset = selectedItem == null ? null : selectedItem.getValue();
          setWaiting(true);
          for (var change : resourceChanges) {
            var parsedDocument = findChangedDocument(change);
            boolean causedByOpenEditorSave =
                change.getOperation() == CRUDOperation.UPDATE
                    && parsedDocument != null
                    && consumePendingSave(
                        pendingSavedSources,
                        change.getResourceUrn(),
                        parsedDocument.getSourceCode());
            if (hasChangedAssets) {
              mergeChangeIntoTree(change, causedByOpenEditorSave);
            }
            updateEditorNotifications(parsedDocument, change.getNotifications());
          }
          // Reconciliation can temporarily invalidate the selection when children are reordered.
          // Select the equivalent surviving node again, without scrolling the tree or selecting
          // the node that happens to occupy the old row.
          if (selectedAsset != null) {
            var restoredSelection = findTreeNodeByPath(root, selectedAsset);
            if (restoredSelection != null) {
              treeView.getSelectionModel().select(restoredSelection);
            }
          }
          // TreeItem changes update only the affected rows and retain expansion, selection and
          // scroll position.  Avoid a full TreeView.refresh(), which redraws the entire viewport.
          setWaiting(false);
        });
  }

  private TreeItem<NavigableAsset> findTreeNode(TreeItem<NavigableAsset> root, String urn) {
    if (root.getValue().getUrn().equals(urn)) {
      return root;
    }
    for (TreeItem<NavigableAsset> child : root.getChildren()) {
      var found = findTreeNode(child, urn);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private TreeItem<NavigableAsset> findNodeContaining(String assetUrn) {
    return findTreeNode(this.root, assetUrn);
  }

  private NavigableKlabDocument<?, ?> findChangedDocument(ResourceSet.Resource change) {
    if (workspace instanceof NavigableKlabAsset<?> root) {
      var asset =
          root.findAsset(change.getResourceUrn(), KlabAsset.class, change.getKnowledgeClass());
      if (asset instanceof NavigableKlabDocument<?, ?> document) {
        return document;
      }
    }
    var node = findNodeContaining(change.getResourceUrn());
    return node != null && node.getValue() instanceof NavigableKlabDocument<?, ?> document
        ? document
        : null;
  }

  private void updateEditorNotifications(
      NavigableKlabDocument<?, ?> document, Collection<Notification> fallbackNotifications) {
    if (document != null && getEditor(document) instanceof MonacoEditorView editor) {
      editor.markSaved(document.getSourceCode());
      var notifications = document.getNotifications();
      editor.markNotifications(
          notifications == null
              ? Objects.requireNonNullElse(fallbackNotifications, List.of())
              : notifications,
          true);
    }
  }

  static boolean consumePendingSave(
      Map<String, Deque<String>> pendingSources, String urn, String parsedSource) {
    if (parsedSource == null) {
      return false;
    }
    var sources = pendingSources.get(urn);
    if (sources == null) {
      return false;
    }
    var matched = sources.removeFirstOccurrence(parsedSource);
    if (sources.isEmpty()) {
      pendingSources.remove(urn);
    }
    return matched;
  }

  private void mergeChangeIntoTree(ResourceSet.Resource change, boolean causedByOpenEditorSave) {

    TreeItem<NavigableAsset> focus = null;
    if (change.getOperation() == CRUDOperation.DELETE) {

      var root = findNodeContaining(change.getResourceUrn());
      if (root != null) {
        root.getParent().getChildren().remove(root);
      }

    } else if (change.getOperation() == CRUDOperation.CREATE
        && workspace instanceof NavigableKlabAsset<?> wroot) {

      // Find the proper place to put it
      var newAsset =
          wroot.findAsset(change.getResourceUrn(), KlabAsset.class, change.getKnowledgeClass());
      var parentAsset = wroot.getParentFor(newAsset, wroot);
      var root =
          parentAsset instanceof NavigableFolder folder
              ? findOrAddFolder(folder)
              : findTreeNode(this.root, parentAsset.getUrn());
      if (root != null) {
        root.getChildren().add(focus = new TreeItem<>((NavigableAsset) newAsset));
      }
      // TODO enqueue an event to edit a newly created document. Doing it here hangs everything.

    } else if (change.getOperation() == CRUDOperation.UPDATE
        && workspace instanceof NavigableKlabAsset<?> wroot) {
      var node = findNodeContaining(change.getResourceUrn());
      var newAsset =
          wroot.findAsset(change.getResourceUrn(), KlabAsset.class, change.getKnowledgeClass());

      if (node != null && newAsset instanceof NavigableAsset navigableAsset) {
        var oldAsset = node.getValue();
        node.setValue(navigableAsset);
        focus = node;
        updateTree(node, navigableAsset);
        // The save callback has already put the new source into the currently visible editor.
        // Recreating it here loses Monaco's cursor/scroll position and makes the save feel like a
        // navigation event.  Other updates still recreate the editor so that external source
        // changes are loaded into it.
        if (causedByOpenEditorSave) {
          rebindEditor(oldAsset, navigableAsset);
        } else {
          refreshEditor(oldAsset, navigableAsset);
        }
        if (navigableAsset instanceof KActorsBehavior updatedBehavior) {
          KlabIDEController.instance().synchronizeManagedBehavior(service, updatedBehavior);
        }
      }
    } else if (change.getOperation() == CRUDOperation.UPDATE_METADATA
        && change.getKnowledgeClass() == KlabAsset.KnowledgeClass.PROJECT
        && workspace instanceof NavigableKlabAsset<?> wroot) {
      var node = findNodeContaining(change.getResourceUrn());
      var updatedProject =
          wroot.findAsset(
              change.getResourceUrn(), NavigableProject.class, KlabAsset.KnowledgeClass.PROJECT);
      if (node != null && updatedProject != null) {
        node.setValue(updatedProject);
        focus = node;
      }
    }

    if (focus != null) {
      // TODO incorporate errors and walk the tree upwards to update the status icons
    }
  }

  private TreeItem<NavigableAsset> findOrAddFolder(NavigableFolder folder) {
    var existing = findTreeNodeByPath(this.root, folder);
    if (existing != null) {
      return existing;
    }

    NavigableAsset folderAsset = folder;
    var parentAsset = folderAsset.parent();
    if (parentAsset == null) {
      return null;
    }

    var parentNode = findTreeNodeByPath(this.root, parentAsset);
    if (parentNode == null) {
      return null;
    }

    var added = new TreeItem<>(folderAsset);
    added.setGraphic(Theme.getGraphics(folderAsset));
    parentNode.getChildren().add(added);
    return added;
  }

  private TreeItem<NavigableAsset> findTreeNodeByPath(
      TreeItem<NavigableAsset> candidate, NavigableAsset asset) {
    if (candidate.getValue().equals(asset)) {
      return candidate;
    }
    for (var child : candidate.getChildren()) {
      var found = findTreeNodeByPath(child, asset);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private void updateTree(TreeItem<NavigableAsset> root, NavigableAsset changed) {

    if (root.getValue() != null) {

      root.setValue(changed);
      root.setGraphic(getTreeGraphics(changed));

      var existingChildren = new ArrayList<>(root.getChildren());
      var newChildren = new ArrayList<>(changed.children());
      var updatedChildren = new ArrayList<TreeItem<NavigableAsset>>();

      // Process children in order of new asset's children
      for (NavigableAsset newChild : newChildren) {
        // Find existing child if present
        var existingChild =
            existingChildren.stream()
                .filter(child -> child.getValue().equals(newChild))
                .findFirst();

        if (existingChild.isPresent()) {
          // Update existing child
          var child = existingChild.get();
          updateTree(child, newChild);
          updatedChildren.add(child);
        } else {
          // Add new child
          updatedChildren.add(defineTree(newChild));
        }
      }

      // Replace all children with ordered list
      root.getChildren().clear();
      root.getChildren().addAll(updatedChildren);
    }
  }

  @Override
  protected void onSingleClickItemSelection(NavigableAsset value) {
    //    if (KlabIDEApplication.instance().isInspectorShown()) {
    //      KlabIDEController.instance().getInspector().inspect(value);
    //    }
    navigateToAsset(value, false);
  }

  @Override
  public boolean isAffectedBy(IDEContextScope scope) {
    return this.digitalTwinControlPanel != null && this.digitalTwinControlPanel.isAffectedBy(scope);
  }

  @Override
  public void closeDigitalTwin(IDEContextScope ideContextScope) {
    digitalTwinControlPanel.closeDigitalTwin(ideContextScope);
  }

  @Override
  public void unsetDigitalTwin(IDEContextScope focalScope) {}

  @Override
  protected void onDoubleClickItemSelection(NavigableAsset value) {
    navigateToAsset(value, true);
  }

  private void navigateToAsset(NavigableAsset asset, boolean activateDocument) {

    var document = containingDocument(asset);
    if (document == null) {
      return;
    }

    if (activateDocument) {
      edit(document);
    } else if (!isEditorSelected(document)) {
      return;
    }

    // TODO if the editor is in "doc" (link with inspector) mode and the inspector is open,
    //  we should document the asset. In all cases, we should put the asset URN in the status
    //  bar along with the dirty status and maybe more.

    if (isEditorSelected(document) && getEditor(document) instanceof MonacoEditorView editor) {
      int offset = asset instanceof KlabStatement statement ? statement.getOffsetInDocument() : 0;
      if (offset >= 0) {
        editor.setCursorPosition(offset);
        editor.requestEditorFocus();
        // HERE
      }
    }
  }

  private NavigableKlabDocument<?, ?> containingDocument(NavigableAsset asset) {
    if (asset instanceof NavigableKlabDocument<?, ?> document) {
      return document;
    }
    if (asset instanceof NavigableKlabStatement<?> statement) {
      return statement.document();
    }
    return null;
  }

  private void focusTreeOn(List<NavigableAsset> assets) {
    if (treeView == null || assets == null || assets.isEmpty()) {
      return;
    }
    // getAssetsAt() returns the path from the document to the most specific containing asset.
    var item = findTreeItem(root, assets.getLast());
    if (item == null) {
      return;
    }
    expandAncestors(item);
    treeView.getSelectionModel().clearAndSelect(treeView.getRow(item));
    treeView.scrollTo(treeView.getRow(item));
  }

  private TreeItem<NavigableAsset> findTreeItem(
      TreeItem<NavigableAsset> candidate, NavigableAsset asset) {
    if (candidate == null) {
      return null;
    }
    if (candidate.getValue() == asset || Objects.equals(candidate.getValue(), asset)) {
      return candidate;
    }
    for (var child : candidate.getChildren()) {
      var found = findTreeItem(child, asset);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private void expandAncestors(TreeItem<NavigableAsset> item) {
    for (var parent = item.getParent(); parent != null; parent = parent.getParent()) {
      parent.setExpanded(true);
    }
  }

  private void expandTreeView(TreeItem<?> item) {
    if (item != null && !item.isLeaf()) {
      item.setExpanded(true);
      for (TreeItem<?> child : item.getChildren()) {
        expandTreeView(child);
      }
    }
  }

  private void collapseTreeView(TreeItem<?> item) {
    if (item != null && !item.isLeaf()) {
      item.setExpanded(false);
      for (TreeItem<?> child : item.getChildren()) {
        collapseTreeView(child);
      }
    }
  }
}

package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.integratedmodelling.common.diagnostics.DocumentValidationTrace;
import org.integratedmodelling.common.services.client.ResourcesMerger;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resources.AuthoringSource;
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
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.TreeSearchField;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.integratedmodelling.klab.ide.utils.AsyncLoad;
import org.integratedmodelling.klab.ide.utils.AsyncOpen;
import org.integratedmodelling.klab.modeler.model.*;
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
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AsyncOpen<MonacoEditorView, Boolean> lspOpening = new AsyncOpen<>();
  private NavigableWorkspace workspace;
  private final WorkspaceView view;
  private WorkflowUIProvider workflowUIProvider;
  private TreeItem<NavigableAsset> root;
  private ProgressBar progressBar;
  private TreeView<NavigableAsset> treeView;
  private final Map<Node, LspDocumentSession> lspSessions = new IdentityHashMap<>();
  // Navigable delegates are mutated before workspaceModified is delivered. Keep the source
  // independently so validation/metadata updates cannot look like external content changes.
  private final Map<Node, String> editorSources = new IdentityHashMap<>();
  private final Map<Node, Integer> editorCursorOffsets = new IdentityHashMap<>();
  private final Map<String, WorkflowEditor> workflowEditors = new HashMap<>();
  private final Map<MonacoEditorView, Runnable> pairedReviewRefresh = new IdentityHashMap<>();
  private final Map<MonacoEditorView, Flow.State> pairedReviewStages = new IdentityHashMap<>();
  private final Map<String, IconButton> pairButtons = new HashMap<>();
  private final Map<String, IconButton> reviewButtons = new HashMap<>();
  private final Set<String> assetsWithFlows = new HashSet<>();
  private final Map<String, String> pendingGitCredentials = new HashMap<>();
  private final Map<String, WorkspaceSemanticValidation.Update> semanticUpdates = new HashMap<>();
  private final Map<String, Label> semanticStatusLabels = new HashMap<>();
  private final Set<AssetTreeCell> assetTreeCells = Collections.newSetFromMap(new WeakHashMap<>());
  private boolean treeTracePending;
  private boolean treeLayoutPending;
  private boolean automaticValidation = true;
  private boolean validationCommandPending;
  private boolean resumeAutomaticRequested;
  private final AtomicBoolean resumeAccepted = new AtomicBoolean();
  private IconButton automaticValidationControl;
  private IconButton validateNowControl;
  private final java.util.concurrent.ExecutorService validationCommands =
      java.util.concurrent.Executors.newSingleThreadExecutor(
          r -> {
            var thread = new Thread(r, "workspace-save-validation");
            thread.setDaemon(true);
            return thread;
          });
  private final WorkspaceSemanticValidation semanticValidation =
      new WorkspaceSemanticValidation(
          () -> KlabIDEController.instance().user(),
          Platform::runLater,
          this::semanticValidationCompleted);

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
    this(
        service,
        WorkspaceOpening.prepare(
            service, resourceInfo.getUrn(), KlabIDEController.instance().user(), () -> false),
        view,
        workflowUIProvider);
  }

  WorkspaceEditor(
      ResourcesService service,
      WorkspaceOpening.Prepared prepared,
      WorkspaceView view,
      WorkflowUIProvider workflowUIProvider) {
    super(prepared.workspace());

    // this must strictly be the service that provides the asset
    assert !(service instanceof ResourcesMerger);

    this.service = service;
    this.view = view;
    this.workflowUIProvider =
        workflowUIProvider == null ? WorkflowUIProvider.NONE : workflowUIProvider;
    this.workspace = getEditedAsset();
    if (hasDeferredValidation(workspace)) {
      automaticValidation = false;
      semanticValidation.setAutomatic(false);
    }
    sceneProperty()
        .addListener(
            (property, oldScene, newScene) -> {
              if (closed.get()) return;
              if (newScene == null) semanticValidation.pause();
              else {
                requestSemanticValidation();
                semanticValidation.start();
              }
            });
    var access = prepared.access();
    assetsWithFlows.addAll(access.flows());
    workspace
        .getProjects()
        .forEach(
            project -> {
              if (project instanceof NavigableProject navigable
                  && access.lockedProjects().contains(project.getUrn())) navigable.setLocked(true);
            });
    access
        .failures()
        .forEach(
            message ->
                KlabIDEController.instance().handleNotification(Notification.warning(message)));
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
          workflowUIProvider.draftFlow(workflow, initial, KlabIDEController.instance().user());
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
      if (workflowEditors.containsKey(editorKey)) {
        restorePairedEditors();
        selectAuxiliaryEditor(editorKey);
        return;
      }
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
      workflowEditors.put(editorKey, editor);
      var node = findNodeContaining(flow.getAssetUrn(), flow.getAssetType());
      if (node != null && node.getValue() instanceof NavigableKlabDocument<?, ?> document) {
        editor.configureSideBySide(() -> toggleWorkflowPair(document, editorKey, false));
        refreshPairButton(document.getUrn());
      }
      showAuxiliaryEditor(editorKey, workflowName(workflow) + " — workflow", editor);
    } catch (Throwable error) {
      KlabIDEController.instance().handleNotification(Notification.error(error));
    }
  }

  private boolean toggleWorkflowPair(
      NavigableKlabDocument<?, ?> document, String key, boolean fromDocument) {
    edit(document);
    var editor = (MonacoEditorView) getEditor(document);
    var workflowEditor = workflowEditors.get(key);
    if (workflowEditor == null) return false;
    boolean paired =
        togglePairedEditors(
            document,
            key,
            fromDocument,
            () -> {
              pairButtons.values().forEach(button -> button.setToggled(false));
              workflowEditors.values().forEach(value -> value.setSideBySide(false));
              editor.setOnReviewMarkerClicked(null);
              editor.setOnReviewMarginDoubleClicked(null);
              editor.clearReviewMarkers();
              pairedReviewRefresh.remove(editor);
              pairedReviewStages.remove(editor);
              workflowEditor.setOnStageLoaded(null);
            });
    if (paired) {
      editor.setReviewMode(true);
      var review = reviewButtons.get(document.getUrn());
      if (review != null) review.setToggled(true);
      editor.setOnReviewMarkerClicked(workflowEditor::reviewMarkerClicked);
      editor.setOnReviewMarginDoubleClicked(workflowEditor::reviewCommentRequested);
      workflowEditor.setOnStageLoaded(
          state -> {
            pairedReviewStages.put(editor, state);
            Runnable refresh =
                () -> {
                  if (pairedReviewStages.get(editor) != state) return;
                  var markers =
                      ProposalLexicalMarkers.markers(
                          state.getId(),
                          state.getProposalReview(),
                          document.getSourceCode(),
                          editor.getText(),
                          ProposalLexicalMarkers.declarationOffsets(document));
                  var stages = new LinkedHashMap<String, String>();
                  markers.forEach(marker -> stages.put(marker.id(), state.getId()));
                  workflowEditor.setReviewMarkerStages(stages);
                  editor.setReviewMarkers(markers);
                };
            pairedReviewRefresh.put(editor, refresh);
            refresh.run();
            editor.runAfterEditorRendered(refresh);
          });
    }
    workflowEditor.setSideBySide(paired);
    var button = pairButtons.get(document.getUrn());
    if (button != null) button.setToggled(paired);
    return paired;
  }

  private boolean pairDocument(NavigableKlabDocument<?, ?> document) {
    var existing =
        workflowEditors.entrySet().stream()
            .filter(
                entry ->
                    Objects.equals(document.getUrn(), entry.getValue().getFlow().getAssetUrn()))
            .findFirst();
    if (existing.isPresent()) return toggleWorkflowPair(document, existing.get().getKey(), true);
    var flows =
        service.getFlows(true, KlabIDEController.instance().user()).stream()
            .filter(flow -> Objects.equals(document.getUrn(), flow.getAssetUrn()))
            .toList();
    if (flows.isEmpty()) return false;
    if (flows.size() > 1) {
      var menu = new ContextMenu();
      for (var flow : flows) {
        var item = new MenuItem(flow.getWorkflowId() + " — " + shortFlowId(flow));
        item.setOnAction(
            event -> {
              openWorkflow(flow, null);
              toggleWorkflowPair(document, "workflow:" + flow.getId(), true);
            });
        menu.getItems().add(item);
      }
      menu.show(pairButtons.get(document.getUrn()), javafx.geometry.Side.BOTTOM, 0, 0);
      return false;
    }
    var flow = flows.getFirst();
    openWorkflow(flow, null);
    return toggleWorkflowPair(document, "workflow:" + flow.getId(), true);
  }

  @Override
  protected void disposeAuxiliaryEditor(Node editor) {
    if (editor instanceof ProjectMaterialBrowser browser) {
      materialBrowsers.values().removeIf(value -> value == browser);
      browser.close();
    }
    if (editor instanceof WorkspaceSettingsEditor settingsEditor) {
      if (workspaceSettingsEditor == settingsEditor) workspaceSettingsEditor = null;
      settingsEditor.close();
    }
    if (editor instanceof ProjectSettingsEditor settingsEditor) {
      projectSettingsEditors.values().removeIf(value -> value == settingsEditor);
      settingsEditor.close();
    }
    if (editor instanceof WorkflowEditor workflowEditor) {
      workflowEditors.values().removeIf(value -> value == workflowEditor);
      refreshPairButton(workflowEditor.getFlow().getAssetUrn());
      workflowEditor.close();
    }
  }

  private boolean hasLinkedWorkflow(String assetUrn) {
    return assetsWithFlows.contains(assetUrn)
        || workflowEditors.values().stream()
            .anyMatch(editor -> Objects.equals(assetUrn, editor.getFlow().getAssetUrn()));
  }

  /** Recompute availability after a draft is opened, cancelled, or closed. */
  private void refreshPairButton(String assetUrn) {
    var button = pairButtons.get(assetUrn);
    if (button == null) return;
    boolean available = hasLinkedWorkflow(assetUrn);
    button.setVisible(available);
    button.setManaged(available);
    if (!available) button.setToggled(false);
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
    if (closed.get()) return;
    KlabIDEController.instance().setFocalEditor(this, visibleAfterCall);
    if (visibleAfterCall) {
      requestSemanticValidation();
      semanticValidation.start();
    } else semanticValidation.pause();
  }

  private NavigableAsset draggedAsset;
  private boolean dropCompleted;
  private boolean panelShownBeforeDrag;
  private final AsyncLoad<DroppedObservation> dropLoad = new AsyncLoad<>();

  private record DroppedObservation(
      IDEContextScope scope, CompletableFuture<Observation> submission) {}

  @Override
  protected void configureDigitalTwinWidget(DigitalTwinControlPanel digitalTwinMinified) {
    // TODO contents
    digitalTwinMinified.setOnDragOver(
        event -> {
          if (event.getGestureSource() == this.treeView && draggedAsset != null) {
            event.acceptTransferModes(TransferMode.ANY);
          }
          event.consume();
        });
    digitalTwinMinified.setOnDragDropped(
        event -> {
          if (event.getGestureSource() == this.treeView && draggedAsset != null) {
            dropCompleted = true;
            event.setDropCompleted(true);
            event.consume();
          }
        });
  }

  @Override
  protected TreeView<NavigableAsset> createContentTree() {

    treeView = new TreeView<>(this.root = defineTree(workspace));
    treeView.setCellFactory(p -> new AssetTreeCell(this));
    root.addEventHandler(
        TreeItem.branchExpandedEvent(),
        event -> {
          scheduleTreeLayout();
          traceTree("branch expanded");
        });
    root.addEventHandler(
        TreeItem.branchCollapsedEvent(),
        event -> {
          scheduleTreeLayout();
          traceTree("branch collapsed");
        });
    Platform.runLater(this::requestSemanticValidation);
    treeView.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    treeView.setShowRoot(false);
    treeView.setPrefWidth(340);

    treeView.setOnDragDetected(
        event -> {
          TreeItem<NavigableAsset> item = treeView.getSelectionModel().getSelectedItem();
          if (item != null) {
            // TODO check if this is draggable in the current conditions
            draggedAsset = item.getValue();
            dropCompleted = false;
            panelShownBeforeDrag = isDigitalTwinControlPanelShown();
            digitalTwinControlPanel.beginReceiving(draggedAsset);
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
          var asset = draggedAsset;
          draggedAsset = null;
          if (asset != null && dropCompleted && event.getTransferMode() != null) {
            handleAssetDrop(asset);
          } else {
            digitalTwinControlPanel.endReceiving();
            if (!panelShownBeforeDrag || digitalTwinControlPanel.getScope() == null) {
              hideDigitalTwinControlPanel();
            }
          }
          dropCompleted = false;
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
                behavior.getBehaviorType() == KActorsBehavior.Type.APP
                    ? "Edit and run application locally" : "Edit and run locally",
                new IconLabel(MaterialDesign.MDI_CLOUD_DOWNLOAD, 16, THEME_ICON_COLOR));
        editLocally.setOnAction(event -> editBehaviorLocally(behavior, asset));
        if (localRuntime.isEmpty()) {
          editLocally.setDisable(true);
        }
        contextMenu.getItems().addAll(editLocally, new SeparatorMenuItem());
      }
      var delete = new MenuItem("Delete", new IconLabel(Material2AL.DELETE, 16, THEME_ICON_COLOR));
      delete.setOnAction(e -> deleteDocument(document, asset));
      contextMenu.getItems().add(delete);
    }
  }

  private void deleteDocument(KlabDocument<?> document, NavigableAsset asset) {
    confirmDocumentDeletion(
        document,
        getScene() == null ? null : getScene().getWindow(),
        () -> KlabIDEController.instance().deleteAsset(service, asset));
  }

  static void confirmDocumentDeletion(
      KlabDocument<?> document, javafx.stage.Window owner, Runnable deleteAction) {
    var confirmation = new Alert(Alert.AlertType.CONFIRMATION);
    confirmation.setTitle("Delete document");
    confirmation.setHeaderText("Delete " + document.getUrn() + "?");
    confirmation.setContentText(
        "This will remove the document from project "
            + document.getProjectName()
            + " and stage its removal if tracked by Git. Commit and publish to share the deletion. Any unsaved edits will be lost.");
    var delete = new ButtonType("Delete", ButtonBar.ButtonData.YES);
    var cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    confirmation.getButtonTypes().setAll(delete, cancel);
    if (owner != null) confirmation.initOwner(owner);
    ((Button) confirmation.getDialogPane().lookupButton(delete)).setDefaultButton(false);
    ((Button) confirmation.getDialogPane().lookupButton(cancel)).setDefaultButton(true);
    if (confirmation.showAndWait().orElse(cancel) == delete) deleteAction.run();
  }

  private final Map<String, ProjectSettingsEditor> projectSettingsEditors = new HashMap<>();
  private final Map<String, ProjectMaterialBrowser> materialBrowsers = new HashMap<>();

  private void openProjectMaterial(NavigableProject project) {
    String key = "project-material:" + project.getUrn();
    if (materialBrowsers.containsKey(key)) { selectAuxiliaryEditor(key); return; }
    var browser = new ProjectMaterialBrowser(service, KlabIDEController.instance().user(),
        project.getUrn(), result -> KlabIDEController.instance().handleResultSets(result),
        () -> closeAuxiliaryEditor(key));
    browser.updateRepositoryState(project.getRepositoryState());
    browser.setOnPublish(() -> {
      var parameters = getOperationParameters(project, RepositoryState.Operation.SYNC_AND_PUBLISH);
      if (parameters != null) KlabIDEController.instance().manageProject(
          service, project.getUrn(), RepositoryState.Operation.SYNC_AND_PUBLISH, parameters);
    });
    materialBrowsers.put(key, browser);
    var tab = showAuxiliaryEditor(key, project.getUrn() + " material", browser);
    tab.setOnCloseRequest(event -> {
      if (browser.isBusy() || !browser.discardDraft()) event.consume();
    });
    tab.selectedProperty().addListener((observable, before, after) -> { if (after) browser.refresh(); });
    tab.addEventHandler(Tab.CLOSED_EVENT, event -> { materialBrowsers.remove(key); browser.close(); });
  }

  private void openProjectSettings(NavigableProject project) {
    String key = "project-settings:" + project.getUrn();
    var existing = projectSettingsEditors.get(key);
    if (existing != null) {
      existing.refreshAccess();
      selectAuxiliaryEditor(key);
      return;
    }
    var editor =
        new ProjectSettingsEditor(
            service,
            KlabIDEController.instance().user(),
            workspace.getUrn(),
            project.getUrn(),
            project::isLocked,
            updated -> {
              project.refreshSettings(updated);
              if (treeView != null) treeView.refresh();
            });
    projectSettingsEditors.put(key, editor);
    var tab = showAuxiliaryEditor(key, project.getUrn() + " settings", editor);
    tab.setOnCloseRequest(
        event -> {
          if (editor.isBusy()) event.consume();
        });
    tab.selectedProperty()
        .addListener(
            (value, old, selected) -> {
              if (selected) editor.refreshAccess();
            });
    tab.addEventHandler(
        Tab.CLOSED_EVENT,
        event -> {
          projectSettingsEditors.remove(key);
          editor.close();
        });
  }

  private void editBehaviorLocally(KActorsBehavior behavior, NavigableAsset asset) {
    try {
      var project = behavior.getProjectName();
      if (project == null || project.isBlank()) {
        var parent = asset.parent(NavigableProject.class);
        project = parent == null ? null : parent.getUrn();
      }
      var managedBehavior =
          ProjectBehaviorSource.retrieve(service, KlabIDEController.instance().user(), project,
              behavior.getUrn(), behavior.getBehaviorType());
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
                  managedBehavior.getBehaviorType(),
                  managedBehavior.getSourceCode(),
                  managedBehavior.getLastUpdateTimestamp());
      KlabIDEController.instance().openBehaviorFile(checkout.file());
    } catch (IOException | IllegalArgumentException e) {
      KlabIDEController.instance()
          .handleNotification(Notification.error("Unable to create the local behavior mirror", e));
    }
  }

  private void setupProjectMenu(ContextMenu contextMenu, NavigableProject project) {
    if (!service.isLocal()) {
      var export = new MenuItem("Export to local services");
      export.setDisable(true);
      contextMenu.getItems().addAll(export, new SeparatorMenuItem());
      var user = KlabIDEController.instance().user();
      CompletableFuture.supplyAsync(() -> user.getServices(ResourcesService.class).stream()
          .filter(ResourcesService::isLocal)
          .filter(candidate -> candidate.status() != null && candidate.status().isOperational())
          .findFirst().filter(candidate -> !LocalProjectExport.present(candidate, project.getUrn(), user)))
          .whenComplete((local, error) -> Platform.runLater(() -> {
            if (closed.get() || error != null || local.isEmpty()) return;
            export.setDisable(false);
            export.setOnAction(event -> {
              export.setDisable(true);
              CompletableFuture.supplyAsync(() -> {
                try {
                  return LocalProjectExport.transfer(service, local.get(), project,
                      workspace.getUrn(), user);
                } catch (Exception failure) {
                  throw new java.util.concurrent.CompletionException(failure);
                }
              }).whenComplete((result, failure) -> {
                if (failure == null && result != null)
                  KlabIDEController.instance().handleResultSets(List.of(result));
                Platform.runLater(() -> {
                  if (failure != null || result == null || result.getResults().isEmpty()
                      || Utils.Notifications.hasErrors(result.getNotifications())) {
                    KlabIDEController.instance().handleNotification(Notification.error(
                        "Unable to export project to local services",
                        failure == null ? "The import did not return a project" : failure.getMessage()));
                  } else {
                    view.refreshAfterLocalImport();
                    KlabIDEController.instance().handleNotification(Notification.info(
                        "Project " + project.getUrn() + " is now available in local services"));
                  }
                });
              });
            });
          }));
    }
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
            if (service.unlockProject(project.getUrn(), KlabIDEController.instance().user()))
              project.setLocked(false);
          } else {
            if (service.lockProject(project.getUrn(), KlabIDEController.instance().user()))
              project.setLocked(true);
          }
        });

    lockUnlock.addEventHandler(
        javafx.event.ActionEvent.ACTION,
        event -> {
          var editor = projectSettingsEditors.get("project-settings:" + project.getUrn());
          if (editor != null) editor.refreshAccess();
        });

    var projectSettings =
        new MenuItem(
            "Project settings...",
            new IconLabel(Theme.WORKSPACE_SETTINGS_ICON, 16, THEME_ICON_COLOR));
    projectSettings.setOnAction(
        e -> {
          openProjectSettings(project);
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
              var parameters = getOperationParameters(project, op);
              if (parameters != null) {
                var credentialId = pendingGitCredentials.get(project.getUrn());
                if (credentialId == null) {
                  KlabIDEController.instance().manageProject(service, project.getUrn(), op, parameters);
                } else {
                  CompletableFuture.supplyAsync(() -> service.manageRepository(
                      project.getUrn(), op, KlabIDEController.instance().user(), credentialId, parameters))
                      .whenComplete((results, failure) -> {
                        if (failure == null) KlabIDEController.instance().handleResultSets(results);
                        else Platform.runLater(() -> KlabIDEController.instance()
                            .handleNotification(Notification.error(failure)));
                      });
                }
              }
            });
        teamMenu.getItems().add(teamOperation);
      }

      // TODO add Untrack after separator
      if (service.isLocal()) {
        var credentials = new MenuItem("Use Git credential set...");
        credentials.setOnAction(event -> {
          var dialog = new TextInputDialog(pendingGitCredentials.getOrDefault(project.getUrn(), ""));
          dialog.setTitle("Local Git credentials");
          dialog.setHeaderText("Enter a credential set ID from this local resources service");
          dialog.setContentText("Set ID (remembered after the next Git operation):");
          dialog.showAndWait().filter(id -> !id.isBlank())
              .ifPresent(id -> pendingGitCredentials.put(project.getUrn(), id.trim()));
        });
        teamMenu.getItems().add(credentials);
      }
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
    // Query the service's effective project grants; never expose the browser to read-only users.
    CompletableFuture.supplyAsync(() -> service.info(project.getUrn(),
        KlabAsset.KnowledgeClass.PROJECT,
        org.integratedmodelling.klab.api.services.resources.ResourceInfo.class,
        KlabIDEController.instance().user())).whenComplete((info, error) -> Platform.runLater(() -> {
          if (closed.get() || error != null || !ProjectMaterialBrowser.canBrowse(info)) return;
          var material = new MenuItem("Additional material…", new IconLabel(Theme.FOLDER_ICON, 16, THEME_ICON_COLOR));
          material.setOnAction(event -> openProjectMaterial(project));
          contextMenu.getItems().add(material);
        }));
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
    automaticValidationControl =
        IconButton.toggle(
                Material2AL.CHECK_CIRCLE,
                16,
                "-color-fg-default",
                "-color-fg-muted",
                () -> {
                  if (automaticValidationControl.isToggled()) runDeferredValidation(true);
                  else {
                    automaticValidation = false;
                    semanticValidation.setAutomatic(false);
                    semanticUpdates
                        .entrySet()
                        .removeIf(entry -> entry.getValue().response() == null);
                    for (var cell : assetTreeCells) cell.refreshDecoration(false);
                    updateValidationControls();
                  }
                  return automaticValidationControl.isToggled();
                })
            .styleClass(Styles.FLAT)
            .tooltip("Automatically validate saved ontologies and namespaces and their dependents");
    automaticValidationControl.setAccessibleText("Validate on save");
    automaticValidationControl.setToggled(automaticValidation);

    validateNowControl =
        IconButton.of(
                Material2AL.AUTORENEW,
                16,
                "-color-fg-default",
                "-color-fg-muted",
                () -> {
                  runDeferredValidation(false);
                  return true;
                })
            .styleClass(Styles.FLAT)
            .tooltip("Validate saved changes now");
    validateNowControl.setAccessibleText("Validate now");
    validateNowControl.visibleProperty().bind(automaticValidationControl.selectedProperty().not());
    validateNowControl.managedProperty().bind(validateNowControl.visibleProperty());
    validateNowControl.setDisable(automaticValidation || validationCommandPending);
    hBox.getChildren()
        .addAll(
            addProject,
            workspaceSettings,
            automaticValidationControl,
            validateNowControl,
            searchField,
            expand,
            collapse);
    return hBox;
  }

  private void updateValidationControls() {
    if (automaticValidationControl == null) return;
    automaticValidationControl.setSelected(automaticValidation);
    automaticValidationControl.setDisable(validationCommandPending);
    validateNowControl.setDisable(automaticValidation || validationCommandPending);
  }

  private void runDeferredValidation(boolean resumeAutomatic) {
    if (validationCommandPending || closed.get()) return;
    validationCommandPending = true;
    resumeAutomaticRequested = resumeAutomatic;
    resumeAccepted.set(false);
    updateValidationControls();
    validationCommands.execute(
        () -> {
          try {
            var controller = KlabIDEController.instance();
            var request =
                new org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl();
            request.setUrn(workspace.getUrn());
            var changes =
                service.submit(
                    request, ResourcesService.SubmissionMode.VALIDATE, controller.user());
            if (changes.stream()
                .anyMatch(change -> Utils.Notifications.hasErrors(change.getNotifications())))
              throw new IllegalStateException("The service could not validate the saved changes");
            controller.handleResultSets(changes);
            resumeAccepted.set(resumeAutomatic);
            // Fetch the validated saved snapshots, independently of asynchronous tree
            // reconciliation.
            var documents = new ArrayList<KlabDocument<?>>();
            for (var change : changes)
              for (var resource : Utils.Resources.collectChanges(change)) {
                if (resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.ONTOLOGY
                    || resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.NAMESPACE) {
                  var asset =
                      service.retrieve(
                          resource.getResourceUrn(),
                          resource.getKnowledgeClass().getAssetClass(),
                          controller.user());
                  if (asset instanceof KlabDocument<?> document) documents.add(document);
                }
              }
            Platform.runLater(
                () -> {
                  if (closed.get()) return;
                  semanticValidation.setAutomatic(false);
                  if (documents.isEmpty()) collectSemanticDocuments(root.getValue(), documents);
                  semanticValidation.documents(documents);
                  semanticValidation.validateOnce(
                      () -> {
                        if (closed.get()) return;
                        validationCommandPending = false;
                        automaticValidation = resumeAutomatic;
                        semanticValidation.setAutomatic(resumeAutomatic);
                        if (resumeAutomatic) requestSemanticValidation();
                        updateValidationControls();
                      });
                });
          } catch (Exception failure) {
            Platform.runLater(
                () -> {
                  if (closed.get()) return;
                  validationCommandPending = false;
                  updateValidationControls();
                });
          }
        });
  }

  private WorkspaceSettingsEditor workspaceSettingsEditor;

  private void showWorkspaceSettings() {
    String key = "workspace-settings:" + workspace.getUrn();
    if (workspaceSettingsEditor != null) {
      workspaceSettingsEditor.refreshAccess();
      selectAuxiliaryEditor(key);
      return;
    }
    workspaceSettingsEditor =
        new WorkspaceSettingsEditor(
            service,
            KlabIDEController.instance().user(),
            workspace.getUrn(),
            info -> {
              workspace.updateSettings(info.getMetadata(), info.getRights());
              if (treeView != null) treeView.refresh();
            });
    var editor = workspaceSettingsEditor;
    var tab = showAuxiliaryEditor(key, workspace.getUrn() + " settings", editor);
    tab.setOnCloseRequest(
        event -> {
          if (editor.isBusy()) event.consume();
        });
    tab.selectedProperty()
        .addListener(
            (value, old, selected) -> {
              if (selected) editor.refreshAccess();
            });
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
    if (op != RepositoryState.Operation.COMMIT_AND_SWITCH
        && op != RepositoryState.Operation.MERGE_CHANGES_FROM) {
      return new String[] {};
    }
    return chooseBranch(
        project.getRepositoryState(),
        op == RepositoryState.Operation.COMMIT_AND_SWITCH,
        getScene() == null ? null : getScene().getWindow());
  }

  static String[] chooseBranch(
      RepositoryState state, boolean switching, javafx.stage.Window owner) {
    var branches =
        state.getBranchNames().stream()
            .filter(branch -> !branch.equals(state.getCurrentBranch()))
            .distinct()
            .sorted()
            .toList();
    var dialog = new Dialog<String>();
    dialog.setTitle(switching ? "Commit and switch branch" : "Merge branch");
    dialog.setHeaderText(
        switching
            ? "Choose an existing branch or enter a new branch name"
            : "Choose the branch to merge into " + state.getCurrentBranch());
    var branch = new ComboBox<String>();
    branch.getItems().setAll(branches);
    branch.setEditable(switching);
    branch.setMaxWidth(Double.MAX_VALUE);
    if (!branches.isEmpty()) branch.getSelectionModel().selectFirst();
    dialog.getDialogPane().setContent(branch);
    var accept =
        new ButtonType(switching ? "Commit and switch" : "Merge", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().setAll(accept, ButtonType.CANCEL);
    var acceptButton = dialog.getDialogPane().lookupButton(accept);
    if (switching) {
      acceptButton.disableProperty().bind(branch.getEditor().textProperty().isEmpty());
    } else {
      acceptButton.disableProperty().bind(branch.valueProperty().isNull());
    }
    dialog.setResultConverter(
        button ->
            button == accept
                ? (switching ? branch.getEditor().getText() : branch.getValue())
                : null);
    if (owner != null) dialog.initOwner(owner);
    return dialog
        .showAndWait()
        .map(String::strip)
        .filter(value -> !value.isBlank())
        .map(value -> new String[] {value})
        .orElse(null);
  }

  private void handleAssetDrop(NavigableAsset value) {
    digitalTwinControlPanel.showDropProgress();
    dropLoad.load(
        () -> {
          var grid = ObservationDropTarget.gridDefinition(value);
          var scope =
              KlabIDEController.instance()
                  .requireDefaultContext(grid == null ? null : grid.getUrn());
          if (scope == null) {
            throw new IllegalStateException(
                "No scope selected and no local runtime service available.");
          }
          var submission = KlabIDEController.instance().observe(scope, value, false);
          return new DroppedObservation(scope, submission);
        },
        result -> {
          var scope = result.scope();
          var submission = result.submission();
          digitalTwinControlPanel.setDigitalTwin(scope, true);
          digitalTwinControlPanel.submissionTask(submission);
          submission
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
        },
        failure -> {
          digitalTwinControlPanel.endReceiving();
          if (!panelShownBeforeDrag || digitalTwinControlPanel.getScope() == null) {
            hideDigitalTwinControlPanel();
          }
          digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.IDLE);
          KlabIDEController.instance()
              .handleNotification(
                  Notification.error(
                      failure.getMessage() != null
                          ? failure.getMessage()
                          : "Failed to observe asset",
                      failure));
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
    private final IconLabel icon = new IconLabel(Theme.UNKNOWN_ICON, 18, THEME_ICON_COLOR);
    private final Label flowDot = new Label("●");
    private final ProgressIndicator spinner = new ProgressIndicator(-1);
    private final Label badge = new Label();
    private final HBox decoration = new HBox(6, icon, flowDot, spinner, badge);

    public AssetTreeCell(WorkspaceEditor workspaceEditor) {
      this.editor = workspaceEditor;
      workspaceEditor.assetTreeCells.add(this);
      icon.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      icon.setTextOverrun(OverrunStyle.CLIP);
      flowDot.setStyle("-fx-text-fill: -color-accent-fg; -fx-font-size: 12px;");
      flowDot.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      spinner.setMinSize(12, 12);
      spinner.setPrefSize(12, 12);
      spinner.setMaxSize(12, 12);
      badge.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      decoration.setAlignment(Pos.CENTER_LEFT);
      decoration.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      decoration.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      for (var node : List.of(flowDot, spinner, badge)) {
        node.managedProperty().bind(node.visibleProperty());
        node.setVisible(false);
      }
    }

    @Override
    protected void updateItem(NavigableAsset asset, boolean empty) {
      super.updateItem(asset, empty);
      render(asset, empty);
    }

    @Override
    protected boolean isItemChanged(NavigableAsset oldItem, NavigableAsset newItem) {
      // NavigableAsset.equals compares paths, not parsed contents. A reparse at the same
      // path must still replace the cell's item and its listeners.
      return oldItem != newItem;
    }

    private void refreshDecoration(boolean assetChanged) {
      // Structural changes can leave a recycled cell awaiting its next JavaFX update.
      // Never change its item/empty state or paint a different asset in that interval.
      if (!isEmpty() && getTreeItem() != null && getTreeItem().getValue() == getItem()) {
        if (assetChanged) render(getItem(), false);
        else updateValidationDecoration(getItem());
        requestLayout();
        if (getTreeView() != null) getTreeView().requestLayout();
        editor.scheduleTreeLayout();
      }
    }

    private void updateValidationDecoration(NavigableAsset asset) {
      setTooltip(new Tooltip(Theme.getLabel(asset)));
      var semantic = editor.semanticSummary(asset);
      if (DocumentValidationTrace.enabled() && asset instanceof KlabDocument<?> document) {
        var result = editor.semanticUpdates.get(WorkspaceSemanticValidation.key(document));
        DocumentValidationTrace.render(
            document,
            result == null ? null : result.request(),
            result == null ? null : result.response(),
            semantic,
            asset.localMetadata().get(NavigableAsset.ERROR_NOTIFICATION_COUNT_KEY, 0),
            asset.localMetadata().get(NavigableAsset.WARNING_NOTIFICATION_COUNT_KEY, 0));
      }
      if (!semantic.isEmpty()) {
        String badgeText =
            semantic.startsWith("Semantic errors")
                ? semantic.replace("Semantic errors", "Errors")
                : semantic.startsWith("Semantic warnings")
                    ? semantic.replace("Semantic warnings", "Warnings")
                    : semantic.equals("Document errors")
                        ? "Errors"
                        : semantic.equals("Validating")
                            ? "Validating"
                            : semantic.equals("Validation out of date")
                                ? "Out of date"
                                : semantic.contains("waiting")
                                    ? "Waiting"
                                    : semantic.contains("unavailable")
                                        ? "Unavailable"
                                        : semantic.contains("failed") ? "Failed" : "Pending";
        boolean pending = badgeText.equals("Pending");
        spinner.setVisible(pending);
        badge.setVisible(!pending);
        badge.setText(badgeText.equals("Failed") ? "●" : badgeText);
        badge.setStyle(
            semantic.startsWith("Semantic errors") || badgeText.equals("Failed")
                ? "-fx-text-fill: -color-danger-fg;"
                : "-fx-text-fill: -color-warning-fg;");
        var indicator = pending ? spinner : badge;
        indicator.setAccessibleText(semantic);
        var details = editor.semanticDetails(asset);
        indicator.setTooltip(new Tooltip(details));
        setTooltip(new Tooltip(Theme.getLabel(asset) + "\n" + details));
      } else {
        spinner.setVisible(false);
        badge.setVisible(false);
      }
    }

    private void render(NavigableAsset asset, boolean empty) {
      if (asset != null && !empty) {
        // Let the cell skin truncate the name without shrinking the icon glyphs.
        setText(
            asset instanceof KlabDocument<?> document
                ? AuthoringSource.label(document)
                : Theme.getLabel(asset));
        setTextOverrun(OverrunStyle.ELLIPSIS);
        setTooltip(new Tooltip(getText()));
        var appearance = getTreeGraphics(asset);
        icon.setText(appearance.getText());
        if (!Objects.equals(icon.getFont(), appearance.getFont()))
          icon.setFont(appearance.getFont());
        icon.setStyle(appearance.getStyle());
        if (appearance.getStyle().isEmpty()) icon.setTextFill(appearance.getTextFill());
        flowDot.setVisible(editor.assetsWithFlows.contains(asset.getUrn()));
        updateValidationDecoration(asset);
        // VirtualFlow may call updateItem during CSS/measurement. Keep the same controls
        // and skins: replacing them here repeatedly creates unstyled, unmeasured graphics.
        setGraphic(decoration);
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
            setStyle(navigableProject.isLocked() ? "-fx-text-fill: -color-success-fg;" : null);
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
        spinner.setVisible(false);
        badge.setVisible(false);
        flowDot.setVisible(false);
        setText(null);
        setGraphic(null);
        setTooltip(null);
        setStyle(null);
        setOnContextMenuRequested(null);
      }
    }
  }

  @Override
  protected String editorTitle(NavigableAsset asset) {
    return asset instanceof KlabDocument<?> document
        ? AuthoringSource.label(document)
        : super.editorTitle(asset);
  }

  private void setWaiting(boolean b) {
    //    Platform.runLater(() -> this.progressBar.progressProperty().setValue(b ? -1d : 0d));
  }

  private static TreeItem<NavigableAsset> defineTree(NavigableAsset asset) {
    var root = new TreeItem<>(asset);
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

      String languageId =
          document.getLanguage().languageId(); // even if Monaco treats it as plain-text for now

      String theme = Theme.CURRENT_THEME.isDark() ? "vs-dark" : "vs";

      // For now use the Urn
      // TODO these buttons etc. should be in a dedicated DocumentEditor subclass
      String documentUri = AuthoringSource.modelUri(document);
      var saveButton =
          IconButton.of(Codicons.SAVE, 12, Theme.FOREGROUND_COLOR, Theme.FOREGROUND_COLOR, null);
      saveButton.setTooltip(new Tooltip("Save"));
      var status = new Label("Ready");
      semanticStatusLabels.put(WorkspaceSemanticValidation.key(document), status);

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

              reviewButtons.put(document.getUrn(), reviewMode);
              var paired =
                  IconButton.toggle(
                      BootstrapIcons.LAYOUT_SPLIT,
                      12,
                      () -> {
                        try {
                          boolean enabled = pairDocument(document);
                          pairButtons.get(document.getUrn()).setToggled(enabled);
                          return enabled;
                        } catch (RuntimeException error) {
                          pairButtons.get(document.getUrn()).setToggled(false);
                          throw error;
                        }
                      });
              paired.setTooltip(new Tooltip("Side-to-side workflow review"));
              paired.setAccessibleText("Side-to-side workflow review");
              paired.setVisible(hasLinkedWorkflow(document.getUrn()));
              paired.setManaged(paired.isVisible());
              pairButtons.put(document.getUrn(), paired);
              reviewMode.setToggled(this.isReviewMode());
              // TODO enable only if the doc has some public flow or if user can
              //  open a review
              reviewMode.setTooltip(new Tooltip("Toggle review mode"));

              return List.of(
                  new BarComponent(saveButton, BarSide.LEFT),
                  new BarComponent(reviewMode, BarSide.LEFT),
                  new BarComponent(lineNumbers, BarSide.RIGHT),
                  new BarComponent(minimap, BarSide.RIGHT),
                  new BarComponent(paired, BarSide.RIGHT));
            }

            @Override
            protected Collection<BarComponent> createStatusBarComponents() {

              return List.of(new BarComponent(status, BarSide.LEFT));
            }
          };

      ret.setOnComposeObservable(context -> ObservableComposerDialog.show(ret, context));
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

      ret.runAfterEditorRendered(
          () -> {
            ret.markNotifications(document.getNotifications(), false);
            showSemanticValidation(document, ret);
            requestSemanticValidation();
            refreshAuthorityProposals(document, ret, false);
          });
      // Only the successful ownership-checked initialization callback may install shared metadata.
      ret.setUseSharedLspMetadata(false);
      ret.loadEditor(document.getSourceCode(), languageId, theme);
      editorSources.put(ret, document.getSourceCode());
      editorSourceVersions.put(ret, (String) document.getMetadata().get(AuthoringSource.VERSION));
      editorDocuments.put(ret, document);

      ret.setCursorPositionListener(offset -> cursorPositionChanged(ret, document, offset));
      ret.setOnDirtyChanged(
          dirty -> {
            Platform.runLater(
                () -> {
                  saveButton.setDisable(!dirty);
                  if (dirty) ret.clearReviewMarkers();
                  else if (pairedReviewRefresh.containsKey(ret)) pairedReviewRefresh.get(ret).run();
                  if (!(document instanceof org.integratedmodelling.klab.api.lang.kim.KimNamespace
                      || document
                          instanceof org.integratedmodelling.klab.api.lang.kim.KimOntology)) {
                    status.setText(dirty ? "Modified" : "Ready");
                    return;
                  }
                  if (dirty) {
                    ret.markSemanticNotifications(List.of());
                    status.setText("Modified · semantic validation requires save");
                  } else showSemanticValidation(document, ret);
                });
          });
      initializeLanguageSupport(ret, languageId, status);
      return ret;
    }
    return null;
  }

  @Override
  protected void disposeEditor(NavigableAsset asset, Node editor) {
    editorSources.remove(editor);
    editorSourceVersions.remove(editor);
    editorDocuments.remove(editor);
    editorCursorOffsets.remove(editor);
    if (editor instanceof MonacoEditorView monaco) {
      lspOpening.cancel(monaco);
      var feedback = authorityFeedback.remove(monaco);
      if (feedback != null) feedback.close();
      authorityNotifications.remove(monaco);
      authoritySources.remove(monaco);
      authorityDocuments.remove(monaco);
      pairedReviewRefresh.remove(monaco);
      pairedReviewStages.remove(monaco);
      monaco.clearReviewMarkers();
    }
    if (asset instanceof KlabDocument<?> document)
      semanticStatusLabels.remove(WorkspaceSemanticValidation.key(document));
    pairButtons.remove(asset.getUrn());
    reviewButtons.remove(asset.getUrn());
    var session = lspSessions.remove(editor);
    if (session != null) {
      session.close();
    }
  }

  private void initializeLanguageSupport(MonacoEditorView editor, String languageId, Label status) {
    var controller = KlabIDEController.instance();
    var server = controller.getLanguageServer();
    var user = controller.user();
    java.util.function.BooleanSupplier current =
        () ->
            !closed.get()
                && editorSources.containsKey(editor)
                && user == controller.user()
                && server == controller.getLanguageServer();
    status.setTooltip(new Tooltip("Language support is loading; source is editable"));
    lspOpening.open(
        editor,
        () -> server != null && KlabLspService.getInstance().ensureInitialized(server, user),
        current,
        available -> {
          if (!available) {
            status.setTooltip(new Tooltip("Language support unavailable; source is editable"));
            controller.handleNotification(
                Notification.warning("Language server unavailable; source remains editable"));
            return;
          }
          // Wait for Monaco rather than opening an empty LSP document before JS is ready. Bind
          // the current text, including edits made while initialization was in flight; never
          // reload.
          editor.runAfterEditorRendered(
              () -> {
                if (!current.getAsBoolean() || lspSessions.containsKey(editor)) return;
                var lsp = KlabLspService.getInstance();
                editor.preloadKeywordHighlighterCache(
                    languageId, lsp.getLanguageKeywords(languageId));
                editor.preloadConceptHighlighterCache(lsp.getConceptCache());
                lspSessions.put(
                    editor, new LspDocumentSession(editor, languageId, editor.getText()));
                status.setTooltip(null);
              });
        },
        error -> {
          status.setTooltip(new Tooltip("Language support unavailable; source is editable"));
          controller.handleNotification(Notification.warning(error));
        },
        () -> {});
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    lspOpening.cancelAll();
    validationCommands.shutdown(); // Finish already accepted saves; do not discard source writes.
    materialBrowsers.values().forEach(ProjectMaterialBrowser::close);
    semanticValidation.close();
    super.close();
  }

  private final Map<MonacoEditorView, AuthorityProposalFeedback> authorityFeedback =
      new IdentityHashMap<>();
  private final Map<MonacoEditorView, List<Notification>> authorityNotifications =
      new IdentityHashMap<>();
  private final Map<MonacoEditorView, String> authoritySources = new IdentityHashMap<>();
  private final Map<MonacoEditorView, KlabDocument<?>> authorityDocuments = new IdentityHashMap<>();

  private void refreshAuthorityProposals(
      KlabDocument<?> document, MonacoEditorView editor, boolean submit) {
    authorityDocuments.put(editor, document);
    var feedback =
        authorityFeedback.computeIfAbsent(
            editor,
            key ->
                new AuthorityProposalFeedback(
                    notifications -> {
                      if (editor.isDirty()
                          || !Objects.equals(editor.getText(), authoritySources.get(editor)))
                        return;
                      authorityNotifications.put(editor, notifications);
                      showSemanticValidation(authorityDocuments.get(editor), editor);
                    }));
    authoritySources.put(editor, document.getSourceCode());
    feedback.update(document, submit);
  }

  private final Map<MonacoEditorView, String> editorSourceVersions = new IdentityHashMap<>();
  private final Map<MonacoEditorView, KlabDocument<?>> editorDocuments = new IdentityHashMap<>();

  static void bindAuthoringSave(
      KlabDocument<?> opened,
      KlabDocument<?> current,
      org.integratedmodelling.klab.api.lang.kim.impl.KlabDocumentImpl<?> payload) {
    var bound =
        AuthoringSource.id(opened) != null
                && current != null
                && AuthoringSource.same(opened, current)
            ? current
            : opened;
    payload.setUrn(bound.getUrn());
    payload.setProjectName(bound.getProjectName());
    AuthoringSource.copy(bound, payload);
  }

  private void saveDocument(String text, NavigableAsset asset) {
    //    Logging.INSTANCE.info("Save document requested: " + asset.getUrn());
    if (asset instanceof KlabDocument<?> document) {
      pendingSavedSources
          .computeIfAbsent(WorkspaceSemanticValidation.key(document), ignored -> new ArrayDeque<>())
          .add(text);
      if (document instanceof org.integratedmodelling.klab.api.lang.kim.KimOntology
          || document instanceof org.integratedmodelling.klab.api.lang.kim.KimNamespace) {
        semanticValidation.invalidatePending();
        semanticUpdates
            .entrySet()
            .removeIf(
                entry ->
                    entry.getKey().equals(WorkspaceSemanticValidation.key(document))
                        || entry.getValue().response() == null);
        org.integratedmodelling.klab.api.lang.kim.impl.KlabDocumentImpl<?> payload =
            document instanceof org.integratedmodelling.klab.api.lang.kim.KimOntology
                ? new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl()
                : new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
        payload.setUrn(document.getUrn());
        payload.setProjectName(asset.parent(NavigableProject.class).getUrn());
        payload.setSourceCode(text);
        bindAuthoringSave(
            document,
            getEditor(asset) instanceof MonacoEditorView editor
                ? editorDocuments.get(editor)
                : null,
            payload);
        if (AuthoringSource.id(document) != null
            && getEditor(asset) instanceof MonacoEditorView editor
            && editorSourceVersions.get(editor) != null)
          payload.getMetadata().put(AuthoringSource.VERSION, editorSourceVersions.get(editor));
        var mode =
            automaticValidation
                ? ResourcesService.SubmissionMode.UPDATE
                : ResourcesService.SubmissionMode.UPDATE_DEFERRED;
        boolean followsResume = validationCommandPending && resumeAutomaticRequested;
        validationCommands.execute(
            () -> {
              try {
                var controller = KlabIDEController.instance();
                var result =
                    service.submit(
                        payload,
                        followsResume && resumeAccepted.get()
                            ? ResourcesService.SubmissionMode.UPDATE
                            : mode,
                        controller.user());
                controller.handleResultSets(result);
                if (result.stream()
                    .anyMatch(change -> Utils.Notifications.hasErrors(change.getNotifications())))
                  throw new IllegalStateException("The service rejected the save");
              } catch (Exception failure) {
                Platform.runLater(
                    () -> {
                      consumePendingSave(
                          pendingSavedSources, WorkspaceSemanticValidation.key(document), text);
                    });
              }
            });
        return;
      }
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
    if (pairedReviewRefresh.containsKey(editor)) pairedReviewRefresh.get(editor).run();
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

    Platform.runLater(
        () -> {
          this.workspace = workspace;
          for (var projectChange : changes.getProjects()) {
            var browser = materialBrowsers.get("project-material:" + projectChange.getResourceUrn());
            if (browser != null && projectChange.getRepositoryState() != null)
              browser.updateRepositoryState(projectChange.getRepositoryState());
          }
          var selectedItem = treeView.getSelectionModel().getSelectedItem();
          var selectedAsset = selectedItem == null ? null : selectedItem.getValue();
          var focusedItem = treeView.getFocusModel().getFocusedItem();
          var focusedAsset = focusedItem == null ? null : focusedItem.getValue();
          setWaiting(true);
          for (var change : resourceChanges) {
            var parsedDocument = findChangedDocument(change);
            if (!currentSourceUpdate(change, parsedDocument)) continue;
            // Keep every behavior family current even when no tree row changes (or is open).
            if (change.getOperation() != CRUDOperation.DELETE
                && parsedDocument instanceof KActorsBehavior behavior) {
              KlabIDEController.instance().synchronizeManagedBehavior(service, behavior);
            }
            boolean causedByOpenEditorSave =
                change.getOperation() == CRUDOperation.UPDATE
                    && parsedDocument != null
                    && consumePendingSave(
                        pendingSavedSources,
                        WorkspaceSemanticValidation.key(parsedDocument),
                        parsedDocument.getSourceCode());
            if (hasChangedAssets || change.getOperation() == CRUDOperation.DELETE) {
              mergeChangeIntoTree(change, causedByOpenEditorSave);
            }
            if (change.getOperation() != CRUDOperation.DELETE) {
              updateEditorNotifications(parsedDocument, change.getNotifications());
              if (parsedDocument != null
                  && getEditor(parsedDocument) instanceof MonacoEditorView editor)
                refreshAuthorityProposals(
                    parsedDocument, editor, causedByOpenEditorSave && automaticValidation);
            }
          }
          // Reconciliation can temporarily invalidate the selection when children are reordered.
          // Select the equivalent surviving node again, without scrolling the tree or selecting
          // the node that happens to occupy the old row.
          if (selectedAsset != null) {
            var restoredSelection = findTreeNodeByPath(root, selectedAsset);
            if (restoredSelection != null
                && treeView.getSelectionModel().getSelectedItem() != restoredSelection) {
              treeView.getSelectionModel().select(restoredSelection);
            }
          }
          if (focusedAsset != null) {
            var restoredFocus = findTreeNodeByPath(root, focusedAsset);
            if (restoredFocus != null
                && treeView.getFocusModel().getFocusedItem() != restoredFocus) {
              treeView.getFocusModel().focus(treeView.getRow(restoredFocus));
            }
          }
          // TreeItem changes update only the affected rows and retain expansion, selection and
          // scroll position.  Avoid a full TreeView.refresh(), which redraws the entire viewport.
          requestSemanticValidation();
          setWaiting(false);
          scheduleTreeLayout();
          traceTree("workspace update");
        });
  }

  static TreeItem<NavigableAsset> findTreeNode(
      TreeItem<NavigableAsset> root, String urn, KlabAsset.KnowledgeClass knowledgeClass) {
    if (root == null || urn == null || knowledgeClass == null) return null;
    // Projects and documents can share a URN (e.g. imod); identity includes the asset type.
    if (root.getValue() != null
        && Objects.equals(root.getValue().getUrn(), urn)
        && assetType(root.getValue()) == knowledgeClass) {
      return root;
    }
    for (TreeItem<NavigableAsset> child : root.getChildren()) {
      var found = findTreeNode(child, urn, knowledgeClass);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private TreeItem<NavigableAsset> findNodeContaining(
      String assetUrn, KlabAsset.KnowledgeClass knowledgeClass) {
    return findTreeNode(this.root, assetUrn, knowledgeClass);
  }

  static TreeItem<NavigableAsset> findChangedTreeNode(
      TreeItem<NavigableAsset> root, ResourceSet.Resource change) {
    var match = findChangedTreeNodeByIdentity(root, change);
    if (match != null
        && AuthoringSource.id(change) != null
        && change.getOperation() == CRUDOperation.DELETE
        && (!(match.getValue() instanceof KlabDocument<?> document)
            || !currentSourceUpdate(change, document))) return null;
    return match;
  }

  private static TreeItem<NavigableAsset> findChangedTreeNodeByIdentity(
      TreeItem<NavigableAsset> root, ResourceSet.Resource change) {
    var search = root;
    if (change.getKnowledgeClass() == KlabAsset.KnowledgeClass.ONTOLOGY
        && change.getProjectUrn() != null) {
      search = findTreeNode(root, change.getProjectUrn(), KlabAsset.KnowledgeClass.PROJECT);
      if (search == null) return null;
    }
    var matches = new ArrayList<TreeItem<NavigableAsset>>();
    collectChangedTreeNodes(search, change, matches);
    if (matches.size() != 1) return null;
    return matches.getFirst();
  }

  static boolean currentSourceUpdate(ResourceSet.Resource change, KlabDocument<?> current) {
    return AuthoringSource.id(change) == null
        || (current != null
            && AuthoringSource.matches(change, current)
            && change.getMetadata().get(AuthoringSource.VERSION) instanceof String version
            && !version.isBlank()
            && Objects.equals(
                change.getMetadata().get(AuthoringSource.VERSION),
                current.getMetadata().get(AuthoringSource.VERSION)));
  }

  private static void collectChangedTreeNodes(
      TreeItem<NavigableAsset> item,
      ResourceSet.Resource change,
      List<TreeItem<NavigableAsset>> matches) {
    if (item == null) return;
    if (AuthoringSource.matches(change, item.getValue())) matches.add(item);
    for (var child : item.getChildren()) collectChangedTreeNodes(child, change, matches);
  }

  static KlabAsset findChangedAsset(NavigableKlabAsset<?> root, ResourceSet.Resource change) {
    NavigableKlabAsset<?> search = root;
    if (change.getKnowledgeClass() == KlabAsset.KnowledgeClass.ONTOLOGY
        && change.getProjectUrn() != null) {
      search =
          root instanceof NavigableProject receiver
                  && Objects.equals(receiver.getUrn(), change.getProjectUrn())
              ? receiver
              : root.findAsset(
                  change.getProjectUrn(), NavigableProject.class, KlabAsset.KnowledgeClass.PROJECT);
      if (search == null) return null;
    }
    var matches = new ArrayList<KlabAsset>();
    collectChangedAssets(search, change, matches);
    return matches.size() == 1 ? matches.getFirst() : null;
  }

  private static void collectChangedAssets(
      NavigableAsset parent, ResourceSet.Resource change, List<KlabAsset> matches) {
    for (var child : parent.children()) {
      if (AuthoringSource.matches(change, child)) matches.add(child);
      collectChangedAssets(child, change, matches);
    }
  }

  private NavigableKlabAsset<?> sourceModel() {
    if (workspace instanceof NavigableKlabAsset<?> model) return model;
    return root != null && root.getValue() instanceof NavigableKlabAsset<?> model ? model : null;
  }

  private NavigableKlabDocument<?, ?> findChangedDocument(ResourceSet.Resource change) {
    var model = sourceModel();
    if (model != null) {
      var asset = findChangedAsset(model, change);
      if (asset instanceof NavigableKlabDocument<?, ?> document) {
        return document;
      }
      if (AuthoringSource.id(change) != null && change.getOperation() == CRUDOperation.DELETE)
        return model.acceptedSourceDeletion(change);
    }
    var node = findChangedTreeNode(this.root, change);
    return node != null && node.getValue() instanceof NavigableKlabDocument<?, ?> document
        ? document
        : null;
  }

  private void updateEditorNotifications(
      NavigableKlabDocument<?, ?> document, Collection<Notification> fallbackNotifications) {
    if (document != null && getEditor(document) instanceof MonacoEditorView editor) {
      editor.markSaved(document.getSourceCode());
      editorSources.put(editor, document.getSourceCode());
      editorSourceVersions.put(
          editor, (String) document.getMetadata().get(AuthoringSource.VERSION));
      editorDocuments.put(editor, document);
      var notifications = document.getNotifications();
      if (DocumentValidationTrace.enabled())
        DocumentValidationTrace.snapshot(
            DocumentValidationTrace.Event.IDE_INPUT,
            document,
            DocumentValidationTrace.version(document),
            -1,
            -1,
            DocumentValidationTrace.Decision.OBSERVE);
      editor.markNotifications(
          notifications == null
              ? Objects.requireNonNullElse(fallbackNotifications, List.of())
              : notifications,
          true);
      showSemanticValidation(document, editor);
    }
  }

  private void requestSemanticValidation() {
    if (!automaticValidation) return;
    if (closed.get()) return;
    if (root == null) return;
    var documents = new ArrayList<KlabDocument<?>>();
    collectSemanticDocuments(root.getValue(), documents);
    var keys =
        documents.stream()
            .map(WorkspaceSemanticValidation::key)
            .collect(java.util.stream.Collectors.toSet());
    semanticUpdates.keySet().retainAll(keys);
    semanticValidation.documents(documents);
  }

  private void collectSemanticDocuments(NavigableAsset asset, List<KlabDocument<?>> documents) {
    if (asset instanceof NavigableKlabDocument<?, ?> document) {
      var delegate = document.getDelegate();
      if (delegate instanceof org.integratedmodelling.klab.api.lang.kim.KimNamespace
          || delegate instanceof org.integratedmodelling.klab.api.lang.kim.KimOntology)
        documents.add(delegate);
    } else for (var child : asset.children()) collectSemanticDocuments(child, documents);
  }

  private void semanticValidationCompleted(String key, WorkspaceSemanticValidation.Update update) {
    if (DocumentValidationTrace.enabled())
      DocumentValidationTrace.response(
          DocumentValidationTrace.Event.IDE_PRESENTATION,
          update.request(),
          update.response(),
          -1,
          DocumentValidationTrace.Decision.OBSERVE);
    var previous = semanticUpdates.get(key);
    if (Objects.equals(
        WorkspaceSemanticValidation.presentation(previous),
        WorkspaceSemanticValidation.presentation(update))) {
      semanticUpdates.put(key, update);
      return;
    }
    var affected = new HashSet<TreeItem<NavigableAsset>>();
    collectSemanticPath(root, key, affected);
    var decorations = new IdentityHashMap<AssetTreeCell, String>();
    for (var cell : assetTreeCells) {
      if (affected.contains(cell.getTreeItem()) && !cell.isEmpty() && cell.getItem() != null)
        decorations.put(
            cell, semanticSummary(cell.getItem()) + "\n" + semanticDetails(cell.getItem()));
    }
    semanticUpdates.put(key, update);
    // TreeItem graphics fire tree modification events. Validation only changes decoration:
    // repaint the affected visible cells without touching selection, focus, or tree structure.
    for (var cell : assetTreeCells) {
      if (affected.contains(cell.getTreeItem())
          && !cell.isEmpty()
          && cell.getItem() != null
          && !Objects.equals(
              decorations.get(cell),
              semanticSummary(cell.getItem()) + "\n" + semanticDetails(cell.getItem())))
        cell.refreshDecoration(false);
    }
    for (var item : affected) {
      if (item.getValue() instanceof NavigableKlabDocument<?, ?> document
          && WorkspaceSemanticValidation.key(document).equals(key)
          && getEditor(document) instanceof MonacoEditorView editor)
        showSemanticValidation(document, editor);
    }
    traceTree("validation " + key + " " + update.status());
  }

  private void scheduleTreeLayout() {
    if (treeLayoutPending || closed.get() || treeView == null) return;
    treeLayoutPending = true;
    Platform.runLater(
        () -> {
          treeLayoutPending = false;
          if (closed.get() || treeView == null || treeView.getScene() == null) return;
          // Live traces show dirty tree/tab content surviving successive scene pulses. Finish
          // this local layout outside the active pulse, without rebuilding cells or selection.
          treeView.applyCss();
          treeView.layout();
        });
  }

  /** Opt-in diagnostics for intermittent layout failures; never logs document source. */
  private void traceTree(String reason) {
    if (!Boolean.getBoolean("klab.ide.traceTree")
        || treeTracePending
        || treeView == null
        || treeView.getScene() == null) return;
    var scene = treeView.getScene();
    long queued = System.nanoTime();
    treeTracePending = true;
    Runnable reporter =
        new Runnable() {
          @Override
          public void run() {
            scene.removePostLayoutPulseListener(this);
            treeTracePending = false;
            if (closed.get() || treeView == null) return;
            System.err.println(
                "[WorkspaceTree] post-layout "
                    + java.time.Instant.now()
                    + " "
                    + reason
                    + " pulseDelayMs="
                    + (System.nanoTime() - queued) / 1_000_000
                    + " expandedRows="
                    + treeView.getExpandedItemCount()
                    + " selectedRow="
                    + treeView.getSelectionModel().getSelectedIndex()
                    + " focusedRow="
                    + treeView.getFocusModel().getFocusedIndex()
                    + " layoutChain="
                    + traceLayoutChain(treeView));
            for (var cell : assetTreeCells) {
              if (cell.isEmpty() || !cell.isVisible() || cell.getScene() == null) continue;
              boolean showing = true;
              for (Node ancestor = cell; ancestor != null; ancestor = ancestor.getParent())
                showing &= ancestor.isVisible();
              if (!showing
                  || !cell.localToScene(cell.getBoundsInLocal())
                      .intersects(treeView.localToScene(treeView.getBoundsInLocal()))) continue;
              var item = cell.getTreeItem();
              System.err.println(
                  "[WorkspaceTree] row="
                      + cell.getIndex()
                      + " asset="
                      + (cell.getItem() == null ? "null" : cell.getItem().getUrn())
                      + " rowMatches="
                      + (treeView.getTreeItem(cell.getIndex()) == item)
                      + " itemMatches="
                      + (item != null && item.getValue() == cell.getItem())
                      + " expanded="
                      + (item != null && item.isExpanded())
                      + " children="
                      + (item == null ? 0 : item.getChildren().size())
                      + " graphicOwned="
                      + (cell.getGraphic() != null && cell.getGraphic().getParent() == cell)
                      + " icon="
                      + cell.icon.getWidth()
                      + "x"
                      + cell.icon.getHeight()
                      + " iconPaint="
                      + traceLabelPaint(cell.icon)
                      + " spinner="
                      + cell.spinner.isVisible()
                      + ":"
                      + cell.spinner.getWidth()
                      + "x"
                      + cell.spinner.getHeight()
                      + " badge="
                      + cell.badge.isVisible()
                      + ":"
                      + cell.badge.getWidth()
                      + "x"
                      + cell.badge.getHeight());
            }
          }
        };
    scene.addPostLayoutPulseListener(reporter);
    Platform.requestNextPulse();
  }

  private static String traceLabelPaint(Label label) {
    var glyph = label.lookup(".text");
    var text = glyph instanceof javafx.scene.text.Text t ? t : null;
    return "{font="
        + label.getFont().getName()
        + ",size="
        + label.getFont().getSize()
        + ",fill="
        + label.getTextFill()
        + ",visible="
        + label.isVisible()
        + ",opacity="
        + label.getOpacity()
        + ",content="
        + label.getContentDisplay()
        + ",glyphMatches="
        + (text != null && Objects.equals(label.getText(), text.getText()))
        + ",glyphFont="
        + (text == null ? null : text.getFont().getName())
        + ",glyphFill="
        + (text == null ? null : text.getFill())
        + ",glyphVisible="
        + (text != null && text.isVisible())
        + ",glyphBounds="
        + (text == null ? null : text.getBoundsInParent())
        + ",clip="
        + (glyph == null || glyph.getClip() == null ? null : glyph.getClip().getBoundsInParent())
        + "}";
  }

  private static String traceLayoutChain(Node node) {
    var chain = new StringBuilder();
    for (Node current = node; current != null; current = current.getParent()) {
      if (!chain.isEmpty()) chain.append(" -> ");
      chain.append(current.getClass().getSimpleName());
      if (current instanceof javafx.scene.Parent parent) {
        chain.append("[needsLayout=").append(parent.isNeedsLayout()).append(']');
      }
      chain
          .append("[managed=")
          .append(current.isManaged())
          .append(",visible=")
          .append(current.isVisible())
          .append(']');
    }
    return chain.toString();
  }

  static boolean collectSemanticPath(
      TreeItem<NavigableAsset> item, String key, Set<TreeItem<NavigableAsset>> affected) {
    if (item == null) return false;
    if (item.getValue() instanceof KlabDocument<?> document) {
      if (!WorkspaceSemanticValidation.key(document).equals(key)) return false;
      affected.add(item);
      return true; // Declaration children do not carry document validation badges.
    }
    boolean found = false;
    for (var child : item.getChildren()) found |= collectSemanticPath(child, key, affected);
    if (found) affected.add(item);
    return found;
  }

  private static String normalizeEditorSource(String source) {
    return source == null ? null : source.replace("\r\n", "\n").replace('\r', '\n');
  }

  private void showSemanticValidation(KlabDocument<?> document, MonacoEditorView editor) {
    if (Boolean.TRUE.equals(
        document.getMetadata().get(ResourcesService.VALIDATION_DEFERRED_METADATA))) {
      editor.markSemanticNotifications(List.of());
      var status = semanticStatusLabels.get(WorkspaceSemanticValidation.key(document));
      if (status != null) status.setText("Saved · validation out of date");
      return;
    }
    if (pairedReviewRefresh.containsKey(editor)) pairedReviewRefresh.get(editor).run();
    String key = WorkspaceSemanticValidation.key(document);
    var update = semanticUpdates.get(key);
    var label = semanticStatusLabels.get(key);
    if (!(document instanceof org.integratedmodelling.klab.api.lang.kim.KimNamespace
        || document instanceof org.integratedmodelling.klab.api.lang.kim.KimOntology)) return;
    if (editor.isDirty()) {
      editor.markSemanticNotifications(List.of());
      if (label != null) label.setText("Modified · semantic validation requires save");
      return;
    }
    boolean matches =
        update != null
            && Objects.equals(
                normalizeEditorSource(editor.getText()),
                normalizeEditorSource(update.request().document().getSourceCode()));
    var notifications =
        matches && update.response() != null
            ? update.response().getNotifications()
            : List.<Notification>of();
    var combined = new ArrayList<Notification>(notifications);
    if (Objects.equals(editor.getText(), authoritySources.get(editor)))
      combined.addAll(authorityNotifications.getOrDefault(editor, List.of()));
    editor.markSemanticNotifications(combined, editor.getText());
    if (label != null) {
      String status = matches ? update.status() : "Semantic validation pending";

      label.setText(status);
      label.setWrapText(
          false); // Details stay in the tooltip; validation must not resize the editor.
      label.setTooltip(
          new Tooltip(
              matches && update.response() != null
                  ? Objects.toString(update.response().getReason(), update.status())
                  : "Validating saved source"));
    }
  }

  private List<WorkspaceSemanticValidation.Update> semanticResults(NavigableAsset asset) {
    if (asset instanceof KlabDocument<?> document) {
      var update = semanticUpdates.get(WorkspaceSemanticValidation.key(document));
      return update == null ? List.of() : List.of(update);
    }
    var result = new ArrayList<WorkspaceSemanticValidation.Update>();
    for (var child : asset.children()) result.addAll(semanticResults(child));
    return result;
  }

  private String semanticSummary(NavigableAsset asset) {
    if (hasDeferredValidation(asset)) return "Validation out of date";
    var updates = semanticResults(asset);
    if (updates.isEmpty()) return "";
    if (updates.stream()
        .anyMatch(
            u ->
                u.response() != null
                    && u.response().getStatus()
                        == org.integratedmodelling.klab.api.services.reasoner.objects
                            .SemanticValidationResponse.Status.FAILED))
      return "Semantic validation failed";
    long errors =
        updates.stream()
            .filter(u -> u.response() != null)
            .flatMap(u -> u.response().getNotifications().stream())
            .filter(
                n ->
                    n.getLevel() == Notification.Level.Error
                        || n.getLevel() == Notification.Level.SystemError)
            .count();
    if (errors > 0) return "Semantic errors: " + errors;
    for (var update : updates)
      if (update.response() == null || !update.response().valid()) return update.status();
    long warnings =
        updates.stream()
            .flatMap(u -> u.response().getNotifications().stream())
            .filter(n -> n.getLevel() == Notification.Level.Warning)
            .count();
    return warnings > 0 ? "Semantic warnings: " + warnings : "";
  }

  private boolean hasDeferredValidation(NavigableAsset asset) {
    if (asset instanceof KlabDocument<?> document)
      return Boolean.TRUE.equals(
          document.getMetadata().get(ResourcesService.VALIDATION_DEFERRED_METADATA));
    for (var child : asset.children()) if (hasDeferredValidation(child)) return true;
    return false;
  }

  private String semanticDetails(NavigableAsset asset) {
    if (hasDeferredValidation(asset))
      return "Saved changes have not been validated. Use Validate now.";
    return WorkspaceSemanticValidation.details(semanticResults(asset));
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
    var normalized = normalizeEditorSource(parsedSource);
    var matched = false;
    for (var iterator = sources.iterator(); iterator.hasNext(); ) {
      if (Objects.equals(normalized, normalizeEditorSource(iterator.next()))) {
        iterator.remove();
        matched = true;
        break;
      }
    }
    if (sources.isEmpty()) {
      pendingSources.remove(urn);
    }
    return matched;
  }

  static boolean preservesEditorSource(
      boolean savedHere, String knownSource, String editorSource, String parsedSource) {
    return savedHere
        || (parsedSource != null
            && (Objects.equals(
                    normalizeEditorSource(knownSource), normalizeEditorSource(parsedSource))
                || Objects.equals(
                    normalizeEditorSource(editorSource), normalizeEditorSource(parsedSource))));
  }

  private void mergeChangeIntoTree(ResourceSet.Resource change, boolean causedByOpenEditorSave) {

    TreeItem<NavigableAsset> focus = null;
    var operation = change.getOperation();
    // A recreation may already be authoritative while the tree still shows its old incarnation.
    // Reconcile the one physical row instead of appending a duplicate after a delayed DELETE.
    if (operation == CRUDOperation.CREATE
        && AuthoringSource.id(change) != null
        && findChangedTreeNodeByIdentity(this.root, change) != null)
      operation = CRUDOperation.UPDATE;
    if (operation == CRUDOperation.DELETE) {

      var model = sourceModel();
      boolean sourceAware = AuthoringSource.id(change) != null;
      if (sourceAware && (model == null || model.acceptedSourceDeletion(change) == null)) return;
      var root =
          sourceAware
              ? findChangedTreeNodeByIdentity(this.root, change)
              : findChangedTreeNode(this.root, change);
      if (root != null) {
        closeEditor(root.getValue());
        pendingSavedSources.remove(
            WorkspaceSemanticValidation.key((KlabDocument<?>) root.getValue()));
        focus = root.getParent();
        if (focus != null) focus.getChildren().remove(root);
      }
      if (sourceAware && this.root != null) {
        // Consume only after the physical row is absent. A delayed/repeated callback then
        // has neither a current source nor a receipt, so it cannot revive or remove a row.
        var remaining = new ArrayList<TreeItem<NavigableAsset>>();
        collectChangedTreeNodes(this.root, change, remaining);
        if (remaining.isEmpty()) model.acknowledgeSourceDeletion(change);
      }

    } else if (operation == CRUDOperation.CREATE
        && workspace instanceof NavigableKlabAsset<?> wroot) {

      // Find the proper place to put it
      var newAsset = findChangedAsset(wroot, change);
      if (newAsset == null) return;
      var parentAsset = wroot.getParentFor(newAsset, wroot);
      var root =
          parentAsset instanceof NavigableFolder folder
              ? findOrAddFolder(folder)
              : findTreeNodeByPath(this.root, parentAsset);
      if (root != null) {
        root.getChildren().add(focus = new TreeItem<>((NavigableAsset) newAsset));
      }
      // TODO enqueue an event to edit a newly created document. Doing it here hangs everything.

    } else if (operation == CRUDOperation.UPDATE
        && workspace instanceof NavigableKlabAsset<?> wroot) {
      var node = findChangedTreeNode(this.root, change);
      var newAsset = findChangedAsset(wroot, change);

      if (node != null && newAsset instanceof NavigableAsset navigableAsset) {
        var oldAsset = node.getValue();
        var openEditor = getEditor(oldAsset);
        boolean preserveEditor =
            navigableAsset instanceof KlabDocument<?> document
                && openEditor instanceof MonacoEditorView editor
                && preservesEditorSource(
                    causedByOpenEditorSave,
                    editorSources.get(editor),
                    editor.getText(),
                    document.getSourceCode());
        focus = node;
        updateTree(node, navigableAsset);
        // Save acknowledgements and subsequent diagnostics for the same source must retain
        // Monaco, its LSP session, undo history, cursor, scroll position and any newer edits.
        if (preserveEditor) {
          rebindEditor(oldAsset, navigableAsset);
        } else {
          refreshEditor(oldAsset, navigableAsset);
        }
      }
    } else if (change.getOperation() == CRUDOperation.UPDATE_METADATA
        && change.getKnowledgeClass() == KlabAsset.KnowledgeClass.PROJECT
        && workspace instanceof NavigableKlabAsset<?> wroot) {
      var node = findChangedTreeNode(this.root, change);
      var updatedProject =
          wroot.findAsset(
              change.getResourceUrn(), NavigableProject.class, KlabAsset.KnowledgeClass.PROJECT);
      if (node != null && updatedProject != null) {
        if (node.getValue() != updatedProject) node.setValue(updatedProject);
        focus = node;
      }
    } else if (change.getOperation() == CRUDOperation.UPDATE_METADATA) {
      // Validation notifications may also arrive as metadata-only changes.
      focus = findChangedTreeNode(this.root, change);
    }

    if (focus != null) {
      refreshDecorations(focus);
      for (var parent = focus.getParent(); parent != null; parent = parent.getParent()) {
        refreshVisibleDecoration(parent);
        rebindEditor(parent.getValue(), parent.getValue());
      }
    }
  }

  private void refreshDecorations(TreeItem<NavigableAsset> node) {
    var asset = node.getValue();
    refreshVisibleDecoration(node);
    rebindEditor(asset, asset);
    for (var child : node.getChildren()) {
      refreshDecorations(child);
    }
  }

  private void refreshVisibleDecoration(TreeItem<NavigableAsset> item) {
    // A graphic change on TreeItem bubbles to the whole tree. Decorations belong to cells;
    // offscreen cells will compute them from the current asset when they become visible.
    for (var cell : assetTreeCells) {
      if (cell.getTreeItem() == item && !cell.isEmpty()) {
        cell.refreshDecoration(true);
      }
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

  static void updateTree(TreeItem<NavigableAsset> root, NavigableAsset changed) {

    if (root.getValue() != null) {

      if (root.getValue() != changed) root.setValue(changed);

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
          // Invalid/incomplete declarations may share a navigable path. Match each old
          // node at most once, or reconciliation will move the same TreeItem twice.
          existingChildren.remove(child);
          updateTree(child, newChild);
          updatedChildren.add(child);
        } else {
          // Add new child
          updatedChildren.add(defineTree(newChild));
        }
      }

      reconcileChildren(root, updatedChildren);
    }
  }

  static <T> void reconcileChildren(TreeItem<T> parent, List<TreeItem<T>> desired) {
    var children = parent.getChildren();
    if (children.equals(desired)) return;
    // Remove only obsolete nodes. Inserting a new declaration must not detach all its siblings.
    var retained = new HashSet<>(desired);
    children.removeIf(child -> !retained.contains(child));
    for (int index = 0; index < desired.size(); index++) {
      var child = desired.get(index);
      if (index < children.size() && children.get(index) == child) continue;
      children.remove(child);
      children.add(index, child);
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

  private void cursorPositionChanged(
      MonacoEditorView editor, NavigableKlabDocument<?, ?> openedDocument, int offset) {
    if (closed.get() || getEditor(openedDocument) != editor) return;
    // Monaco can repeat cursor callbacks after save/layout/diagnostic updates. In particular,
    // a callback queued before a save must not reselect or reveal a row after reconciliation.
    var previous = editorCursorOffsets.put(editor, offset);
    if (Objects.equals(previous, offset) || !isEditorSelected(openedDocument)) return;
    var change = new ResourceSet.Resource();
    change.setKnowledgeClass(assetType(openedDocument));
    change.setResourceUrn(openedDocument.getUrn());
    change.setProjectUrn(openedDocument.getProjectName());
    if (AuthoringSource.id(openedDocument) != null) {
      change.getMetadata().put(AuthoringSource.ID, AuthoringSource.id(openedDocument));
      change.setServiceId(openedDocument.getServiceId());
    }
    var node = findChangedTreeNode(root, change);
    if (node == null
        || !(node.getValue() instanceof NavigableKlabDocument<?, ?> current)
        || getEditor(current) != editor) return;
    // Parsed offsets are meaningful only for the source that was parsed. Newer unsaved edits
    // may shift them; resume navigation on the next cursor movement against matching source.
    if (!Objects.equals(
        normalizeEditorSource(current.getSourceCode()), normalizeEditorSource(editor.getText())))
      return;
    focusTreeOn(current.getAssetsAt(offset));
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
    if (treeView.getSelectionModel().getSelectedItem() == item) return;
    expandAncestors(item);
    treeView.getSelectionModel().clearAndSelect(treeView.getRow(item));
    treeView.scrollTo(treeView.getRow(item));
    traceTree("cursor navigation " + item.getValue().getUrn());
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

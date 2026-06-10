package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
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
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableFolder;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.TreeSearchField;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.integratedmodelling.klab.modeler.model.*;
import org.integratedmodelling.klabeditor.MonacoEditorView;
import org.integratedmodelling.klabeditor.lsp.DiagnosticsService;
import org.integratedmodelling.klabeditor.lsp.KlabLspService;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

public class WorkspaceEditor extends EditorPage<NavigableWorkspace, NavigableAsset> {

  private final ResourcesService service;
  private NavigableWorkspace workspace;
  private final WorkspaceView view;
  private TreeItem<NavigableAsset> root;
  private ProgressBar progressBar;
  private TreeView<NavigableAsset> treeView;

  private final DiagnosticsService diagnosticsService = DiagnosticsService.getInstance();

  public WorkspaceEditor(ResourcesService service, ResourceInfo resourceInfo, WorkspaceView view) {
    super(
        new NavigableWorkspace(
            service.retrieveWorkspace(resourceInfo.getUrn(), KlabIDEController.instance().user())));
    this.service = service;
    this.view = view;
    this.workspace = getEditedAsset();
    // lock all projects that let us
    for (var project : workspace.getProjects()) {
      if (service.lockProject(project.getUrn(), KlabIDEController.instance().user())
          && project instanceof NavigableProject navigableProject) {
        navigableProject.setLocked(true);
      }
    }
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
      var openEdit =
          new MenuItem("Delete", new IconLabel(Material2AL.DELETE, 16, Theme.FOREGROUND_COLOR));
      openEdit.setOnAction(e -> KlabIDEController.instance().deleteAsset(service, asset));
      contextMenu.getItems().add(openEdit);
    }
  }

  private void setupProjectMenu(ContextMenu contextMenu, NavigableProject project) {
    var lockUnlock =
        new MenuItem(
            project.isLocked() ? "Unlock" : "Lock",
            new IconLabel(
                project.isLocked() ? BootstrapIcons.LOCK : BootstrapIcons.UNLOCK,
                16,
                Theme.FOREGROUND_COLOR));
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
            new IconLabel(Theme.WORKSPACE_SETTINGS_ICON, 16, Theme.FOREGROUND_COLOR));
    projectSettings.setOnAction(
        e -> {
          /* TODO */
        });

    var deleteProject =
        new MenuItem(
            "Delete project...", new IconLabel(Material2AL.DELETE, 16, Theme.FOREGROUND_COLOR));
    deleteProject.setOnAction(
        e -> {
          deleteProject(project);
        });

    var newMenu =
        new Menu("New", new IconLabel(CarbonIcons.DOCUMENT_ADD, 16, Theme.FOREGROUND_COLOR));
    var newNamespace =
        new MenuItem(
            "Namespace...", new IconLabel(Theme.NAMESPACE_ICON, 16, Theme.FOREGROUND_COLOR));
    var newBehavior =
        new MenuItem(
            "Behavior, Application or test case...",
            new IconLabel(Theme.BEHAVIOR_ICON, 16, Theme.FOREGROUND_COLOR));
    var newOntology =
        new MenuItem("Ontology...", new IconLabel(Theme.ONTOLOGY_ICON, 16, Theme.FOREGROUND_COLOR));
    var newObservationStrategy =
        new MenuItem(
            "Observation strategy...",
            new IconLabel(Theme.OBSERVATION_ICON, 16, Theme.FOREGROUND_COLOR));

    newNamespace.setOnAction(
        actionEvent -> {
          createNewDocument(project, ProjectStorage.ResourceType.MODEL_NAMESPACE);
        });
    newBehavior.setOnAction(
        actionEvent -> {
          createNewDocument(project, ProjectStorage.ResourceType.BEHAVIOR_COMPONENT);
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

    var teamMenu = new Menu("Team", new IconLabel(Material2MZ.PEOPLE, 16, Theme.FOREGROUND_COLOR));

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
                    Theme.FOREGROUND_COLOR));
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
              new IconLabel(CarbonIcons.UNLINK, 16, Theme.FOREGROUND_COLOR));
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

    var expand = new Button("", new IconLabel(CarbonIcons.EXPAND_ALL, 16, Theme.FOREGROUND_COLOR));
    var collapse =
        new Button("", new IconLabel(CarbonIcons.COLLAPSE_ALL, 16, Theme.FOREGROUND_COLOR));

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
        new IconLabel(Theme.WORKSPACE_SETTINGS_ICON, 16, Theme.FOREGROUND_COLOR));
    workspaceSettings.setOnAction(actionEvent -> showWorkspaceSettings());
    workspaceSettings.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);

    var searchField =
        new TreeSearchField<>(
            this.treeView,
            (q, asset) -> {
              return asset.getUrn().toLowerCase().contains(q);
            });
    HBox.setHgrow(searchField, Priority.ALWAYS);

    var addProject = new Button("");
    addProject.setGraphic(new IconLabel(Theme.ADD_PROJECT_ICON, 16, Theme.FOREGROUND_COLOR));
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
        .createDocument(service, urn, project.getUrn(), knowledgeClass);
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
              digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.ERROR);
              KlabIDEController.instance()
                  .handleNotifications(List.of(Notification.error(throwable)));
              return Observation.EMPTY_OBSERVATION;
            })
        .thenApply(
            observation -> {
              digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.IDLE);
              return observation;
            });
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
        setText(Theme.getLabel(asset));
        setGraphic(Theme.getGraphics(asset));
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
              contextMenu.show(this, event.getScreenX(), event.getScreenY());
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
    root.setGraphic(Theme.getGraphics(asset));
    for (var child : asset.children()) {
      root.getChildren().add(defineTree(child));
    }
    return root;
  }

  @Override
  protected Node createEditor(NavigableAsset asset) {
    if (asset instanceof NavigableKlabDocument<?, ?> document) {
      // 1. LSP init for this workspace
      //      Path workspaceRoot = Paths.get(System.getProperty("user.home") + "/git/klab-ide");
      //      try {
      if (!KlabLspService.getInstance()
          .ensureInitialized(
              KlabIDEController.instance().getLanguageServer(),
              KlabIDEController.instance().user())) {
        KlabIDEController.instance()
            .handleNotification(
                Notification.error("LSP Server is not running: no edit support available"));
        //      } catch (Exception e) {
        //        e.printStackTrace();
        //        System.err.println("[WorkspaceEditor] Error starting LSP Server" + e);
      }

      String languageId =
          document.getLanguage().languageId(); // even if Monaco treats it as plain-text for now

      String theme = Theme.CURRENT_THEME.isDark() ? "vs-dark" : "vs";

      // For now use the Urn
      String documentUri =
          "inmemory:///klab/" + document.getUrn() + "." + document.getLanguage().fileExtension();

      var ret =
          new MonacoEditorView(
              documentUri, content -> Platform.runLater(() -> saveDocument(content, asset)));

      ret.loadEditor(document.getSourceCode(), languageId, theme);

      KlabLspService lsp = KlabLspService.getInstance();

      System.out.println("[WorkspaceEditor] Opening LSP document " + documentUri);
      lsp.openDocument(documentUri, languageId, document.getSourceCode());

      DiagnosticsService diagnosticsService = DiagnosticsService.getInstance();
      ret.setCursorPositionListener(
          offset -> {
            for (var ass : document.getAssetsAt(offset)) {
              System.out.println("Cursor position " + offset + " within asset: " + ass);
            }
          });
      ret.setOnDirtyChanged(
          dirty -> {
            // TODO change the tab title with the asterisk on top
          });
      DiagnosticsService.Listener listener =
          (uri, diagnostics) -> {
            System.out.println(
                "[WorkspaceEditor] Listener fired for URI = "
                    + uri
                    + ", expected = "
                    + documentUri
                    + ", count = "
                    + diagnostics.size());

            if (documentUri.equals(uri)) {
              Platform.runLater(
                  () -> {
                    System.out.println(
                        "[WorkspaceEditor] Forwarding diagnostics to MonacoEditorView");
                    ret.setDiagnostics(diagnostics);
                  });
            } else {
              System.out.println("[WorkspaceEditor] Ignoring diagnostics for " + uri);
            }
          };

      diagnosticsService.addListener(listener);

      // Initialize with any diagnostics already present for this URI
      var existing = diagnosticsService.getDiagnostics(documentUri);
      if (!existing.isEmpty()) {
        ret.setDiagnostics(existing);
      }

      ret.setChangeListener(
          newText -> {
            try {
              // Send to LSP. This does not happen reliably.
              System.err.println("[WorkspaceEditor] Sending changes for " + documentUri);
              lsp.changeDocument(documentUri, newText);
            } catch (Exception e) {
              System.err.println("[WorkspaceEditor] Failed didChange for " + documentUri);
              e.printStackTrace();
            }
          });
      // 5. Automatic cleanup: when editor node is detached from scene, remove listener
      ret.sceneProperty()
          .addListener(
              (obs, oldScene, newScene) -> {
                if (newScene == null) {
                  diagnosticsService.removeListener(listener);
                  lsp.closeDocument(documentUri);
                }
              });
      return ret;
    }
    return null;
  }

  private void saveDocument(String text, NavigableAsset asset) {
    //    Logging.INSTANCE.info("Save document requested: " + asset.getUrn());
    if (asset instanceof KlabDocument<?> document) {
      KlabIDEController.instance()
          .updateDocument(
              service,
              asset.parent(NavigableProject.class).getUrn(),
              document.getUrn(),
              ProjectStorage.ResourceType.classify(document),
              text);
    }
  }

  public void updateWorkspace(
      NavigableWorkspace workspace, ResourceSet changes, Collection<NavigableAsset> changedAssets) {
    this.workspace = workspace;

    if (!changes.isEmpty()) {
      Platform.runLater(
          () -> {
            setWaiting(true);
            for (var change : Utils.Resources.collectChanges(changes)) {
              mergeChangeIntoTree(change);
            }
            setWaiting(false);
          });
    }
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

  private void mergeChangeIntoTree(ResourceSet.Resource change) {

    NavigableDocument document = null;
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
      if (newAsset instanceof NavigableDocument navigableDocument) {
        document = navigableDocument;
        // TODO enqueue an event to edit the document. Doing it here hangs everything
      }

    } else if (change.getOperation() == CRUDOperation.UPDATE
        && workspace instanceof NavigableKlabAsset<?> wroot) {
      var node = findNodeContaining(change.getResourceUrn());
      var newAsset =
          wroot.findAsset(change.getResourceUrn(), KlabAsset.class, change.getKnowledgeClass());

      if (node != null) {
        node.setValue((NavigableAsset) newAsset);
        node.getChildren().clear();
        focus = node;
        updateTree(node, (NavigableAsset) newAsset);
        if (node.getValue() instanceof NavigableDocument navigableDocument) {
          document = navigableDocument;
        }
      }
    }

    if (focus != null) {
      // TODO incorporate errors and walk the tree upwards to update the status icons
    }

    if (document != null) {
      /*
      TODO codeNotifications must be shown in the editors corresponding to the assets they belong to.
       Icons for those same assets must change color accordingly.
       */
      var codeNotifications =
          change.getNotifications().stream()
              .filter(notification -> notification.getLexicalContext() != null)
              .toList();
    }
  }

  private TreeItem<NavigableAsset> findOrAddFolder(NavigableFolder folder) {
    return null;
  }

  private void updateTree(TreeItem<NavigableAsset> root, NavigableAsset changed) {

    if (root.getValue() != null) {

      root.setValue(changed);

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
    if (KlabIDEApplication.instance().isInspectorShown()) {
      KlabIDEController.instance().getInspector().inspect(value);
    }
    System.out.println("clicked on " + value + "");
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
    if (value instanceof KlabDocument<?> document) {
      edit(value);
    } else if (value instanceof KlabStatement statement) {
      // TODO show editor and set the cursor there
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

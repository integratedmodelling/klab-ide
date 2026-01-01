package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.common.logging.Logging;
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
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.lsp.DiagnosticsService;
import org.integratedmodelling.klab.ide.lsp.KlabLspService;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.integratedmodelling.klab.modeler.model.*;
import org.integratedmodelling.klabeditor.MonacoEditorView;

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
    if (service instanceof ResourcesService.Admin admin) {
      // lock all projects that let us
      for (var project : workspace.getProjects()) {
        if (admin.lockProject(project.getUrn(), KlabIDEController.instance().user())
            && project instanceof NavigableProject navigableProject) {
          navigableProject.setLocked(true);
        }
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
    treeView.setCellFactory(p -> new AssetTreeCell());
    treeView.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    treeView.setShowRoot(false);
    treeView.setPrefWidth(340);
    treeView.setOnContextMenuRequested(
        event -> {
          TreeItem<NavigableAsset> item = treeView.getSelectionModel().getSelectedItem();
          if (item != null) {
            var contextMenu = new javafx.scene.control.ContextMenu();
            contextMenu.setAutoHide(true);
            switch (item.getValue()) {
              case NavigableProject project -> {
                setupProjectMenu(contextMenu, project);
              }
              case KlabDocument<?> document -> {
                setupDocumentMenu(contextMenu, document);
              }
              default -> {}
            }
            contextMenu.show(treeView, event.getScreenX(), event.getScreenY());
          }
        });

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
    var openEdit = new javafx.scene.control.MenuItem("Delete");
    //    openEdit.setOnAction(e -> edit(document));
    contextMenu.getItems().add(openEdit);
  }

  private void setupProjectMenu(ContextMenu contextMenu, NavigableProject project) {
    var lockUnlock = new javafx.scene.control.MenuItem(project.isLocked() ? "Unlock" : "Lock");
    lockUnlock.setOnAction(
        e -> {
          if (service instanceof ResourcesService.Admin admin) {
            if (project.isLocked()) {
              admin.unlockProject(project.getUrn(), KlabIDEController.instance().user());
              project.setLocked(false);
            } else {
              admin.lockProject(project.getUrn(), KlabIDEController.instance().user());
              project.setLocked(true);
            }
          }
        });

    var projectSettings = new javafx.scene.control.MenuItem("Project settings...");
    projectSettings.setOnAction(
        e -> {
          if (service instanceof ResourcesService.Admin admin) {
            /* TODO */
          }
        });

    var deleteProject = new javafx.scene.control.MenuItem("Delete project...");
    deleteProject.setOnAction(
        e -> {
          if (service instanceof ResourcesService.Admin admin) {
            deleteProject(project);
          }
        });

    var newMenu = new javafx.scene.control.Menu("New");
    var newNamespace = new javafx.scene.control.MenuItem("Namespace...");
    var newBehavior = new javafx.scene.control.MenuItem("Behavior, Application or test case...");
    var newOntology = new javafx.scene.control.MenuItem("Ontology...");
    var newObservationStrategy = new javafx.scene.control.MenuItem("Observation strategy...");

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

    var teamMenu = new javafx.scene.control.Menu("Team");

    if (project.getRepositoryState().getOverallStatus() == RepositoryState.Status.UNTRACKED) {

      var newProject = new javafx.scene.control.MenuItem("Add to version control...");
      teamMenu.getItems().add(newProject);

    } else {

      for (var op : RepositoryState.Operation.values()) {
        var teamOperation = new javafx.scene.control.MenuItem(op.description());
        teamOperation.setOnAction(
            e -> {
              KlabIDEController.instance()
                  .manageProject(service, project.getUrn(), op, getOperationParameters(project, op));

              // TODO the new branch/switch menus should be submenus with the existing branches +
              //  New branch...

            });
        teamMenu.getItems().add(teamOperation);
      }

      // TODO add Untrack after separator
      var detach = new javafx.scene.control.MenuItem("Detach from version control");
      detach.setOnAction(
          e -> {
            /* TODO */
          });
      teamMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
      teamMenu.getItems().add(detach);
    }
    contextMenu.getItems().add(newMenu);
    contextMenu.getItems().add(teamMenu);
    contextMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
    contextMenu.getItems().addAll(lockUnlock, projectSettings, deleteProject);
  }

  @Override
  protected Node createTopMenu() {
    var hBox = new HBox();
    hBox.setAlignment(Pos.CENTER_RIGHT);

    var workspaceSettings = new Button("");
    workspaceSettings.setGraphic(new IconLabel(Theme.WORKSPACE_SETTINGS_ICON, 16, Color.DARKGREEN));
    workspaceSettings.setOnAction(actionEvent -> showWorkspaceSettings());
    workspaceSettings.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);

    var addProject = new Button("");
    addProject.setGraphic(new IconLabel(Theme.ADD_PROJECT_ICON, 16, Color.DARKGREEN));
    addProject.setOnAction(
        actionEvent -> {
          createNewProject();
        });
    addProject.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    hBox.getChildren().addAll(workspaceSettings, addProject);
    return hBox;
  }

  private void showWorkspaceSettings() {
    // TODO add settings tab or switch to it
  }

  private boolean createNewProject() {
    var dialog = new TextInputDialog();
    dialog.setTitle("Create a new project");
    dialog.setHeaderText(
        "Porcodí, porcodá in workspace " + workspace.getUrn() + ", famo sto progetto diocá");
    dialog.setContentText("URN of new project:");
    dialog.initOwner(getScene().getWindow());
    var urn = dialog.showAndWait().orElse(null);
    return KlabIDEController.instance().createProject(service, urn, workspace.getUrn());
  }

  private boolean createNewDocument(
      NavigableProject project, ProjectStorage.ResourceType knowledgeClass) {
    var dialog = new TextInputDialog();
    dialog.setTitle("Create a new " + knowledgeClass.name().toLowerCase());
    dialog.setHeaderText("Porcodí, porcodó, questo cazzo a chi lo dó");
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
    if (!result.isEmpty()
        && result.get().getButtonData() == ButtonBar.ButtonData.YES
        && service instanceof ResourcesService.Admin admin) {
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
    @Override
    protected void updateItem(NavigableAsset asset, boolean empty) {
      super.updateItem(asset, empty);
      if (asset != null && !empty) {
        setText(Theme.getLabel(asset));
        setGraphic(Theme.getGraphics(asset));
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
    for (var child : asset.children()) {
      root.getChildren().add(defineTree(child));
    }
    return root;
  }

  @Override
  protected Node createEditor(NavigableAsset asset) {
    if (asset instanceof NavigableKlabDocument<?, ?> document) {
      // 1. LSP init for this workspace
      Path workspaceRoot = Paths.get(System.getProperty("user.home") + "/git/klab-ide");
      try {
        KlabLspService.getInstance().startIfNeeded(workspaceRoot);
        System.out.println("[WorkspaceEditor] LSP Server initialized");
      } catch (Exception e) {
        e.printStackTrace();
        System.err.println("[WorkspaceEditor] Error starting LSP Server" + e);
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
              System.out.println("Enclosing asset: " + ass);
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
    Logging.INSTANCE.info("Save document requested: " + asset.getUrn());
    if (service instanceof ResourcesService.Admin admin
        && asset instanceof KlabDocument<?> document) {
      var changes =
          admin.updateDocument(
              asset.parent(NavigableProject.class).getUrn(),
              ProjectStorage.ResourceType.classify(document),
              text,
              KlabIDEController.instance().user());
      // FIXME dispatch EACH changeset to the respective workspace editor if one is present in the
      //  parent view
      var workspaceChanges =
          changes.stream()
              .filter(ch -> this.workspace.getUrn().equals(ch.getWorkspace()))
              .findFirst();
      workspaceChanges.ifPresent(this::updateWorkspace);
    }
  }

  public void updateWorkspace(NavigableWorkspace workspace) {
    this.workspace = workspace;
    Platform.runLater(
        () -> {
          treeView.setRoot(this.root = defineTree(workspace));
        });
  }

  public void updateWorkspace(ResourceSet changes) {

    var codeNotifications =
        changes.getNotifications().stream()
            .filter(notification -> notification.getLexicalContext() != null)
            .toList();
    var systemNotifications =
        changes.getNotifications().stream()
            .filter(notification -> notification.getLexicalContext() == null)
            .toList();

    if (!systemNotifications.isEmpty()
        && KlabIDEController.instance().handleNotifications(systemNotifications)) {
      return;
    }

    /*
    TODO codeNotifications must be shown in the editors corresponding to the assets they belong to.
     Icons for those same assets must change color accordingly.
     */

    if (!changes.isEmpty()) {

      setWaiting(true);
      Platform.runLater(
          () -> {
            for (var asset : workspace.mergeChanges(changes, KlabIDEController.instance().user())) {

              var status =
                  asset
                      .localMetadata()
                      .get(NavigableAsset.REPOSITORY_STATUS_KEY, RepositoryState.Status.class);

              var rootNode = findRootNode(asset);
              if (rootNode == null) {
                findParentNode(asset).getChildren().add(defineTree(asset));
              } else if (status == RepositoryState.Status.REMOVED) {
                rootNode.getParent().getChildren().remove(rootNode);
              } else {
                updateTree(rootNode, asset);
              }
            }
            setWaiting(false);
          });
    }
  }

  private TreeItem<NavigableAsset> findParentNode(NavigableAsset asset) {
    var parent = asset.parent();
    if (parent != null) {
      return findTreeNode(this.root, parent);
    }
    return this.root;
  }

  private TreeItem<NavigableAsset> findTreeNode(
      TreeItem<NavigableAsset> root, NavigableAsset asset) {
    if (root.getValue().equals(asset)) {
      return root;
    }
    for (TreeItem<NavigableAsset> child : root.getChildren()) {
      var found = findTreeNode(child, asset);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private TreeItem<NavigableAsset> findRootNode(NavigableAsset asset) {
    return findTreeNode(this.root, asset);
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
  }

  @Override
  public boolean isAffectedBy(IDEContextScope scope) {
    return this.digitalTwinControlPanel.isAffectedBy(scope);
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
}

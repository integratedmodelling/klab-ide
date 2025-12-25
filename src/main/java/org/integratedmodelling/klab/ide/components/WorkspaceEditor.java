package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.input.ClipboardContent;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.data.RepositoryState;
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
import org.integratedmodelling.klab.ide.lsp.DiagnosticsService;
import org.integratedmodelling.klab.ide.lsp.KlabLspService;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.integratedmodelling.klab.modeler.model.NavigableKimConceptStatement;
import org.integratedmodelling.klab.modeler.model.NavigableKimModel;
import org.integratedmodelling.klab.modeler.model.NavigableProject;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;
import org.integratedmodelling.klabeditor.MonacoEditorView;

public class WorkspaceEditor extends EditorPage<NavigableWorkspace, NavigableAsset> {

  private final ResourcesService service;
  private final NavigableWorkspace workspace;
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
            switch (item.getValue()) {
              case NavigableProject project -> {
                var lockUnlock =
                    new javafx.scene.control.MenuItem(project.isLocked() ? "Unlock" : "Lock");
                lockUnlock.setOnAction(
                    e -> {
                      if (service instanceof ResourcesService.Admin admin) {
                        if (project.isLocked()) {
                          admin.unlockProject(
                              project.getUrn(), KlabIDEController.instance().user());
                          project.setLocked(false);
                        } else {
                          admin.lockProject(project.getUrn(), KlabIDEController.instance().user());
                          project.setLocked(true);
                        }
                      }
                    });
                contextMenu.getItems().add(lockUnlock);
              }
              case KlabDocument<?> document -> {
                var openEdit = new javafx.scene.control.MenuItem("Open");
                openEdit.setOnAction(e -> edit(item.getValue()));
                contextMenu.getItems().add(openEdit);
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
    if (asset instanceof KlabDocument<?> document) {
      // 1. LSP init for this workspace
      Path workspaceRoot = Paths.get(System.getProperty("user.home") + "/git/klab-ide");
      try {
        KlabLspService.getInstance().startIfNeeded(workspaceRoot);
      } catch (Exception e) {
        e.printStackTrace();
      }

      String languageId =
          document
              .getLanguage()
              .languageId(); // even if Monaco treats it as plain-text for now

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

      // 5. Hook editor content changes -> LSP didChange. Sometimes it gets invoked, sometimes not
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

      DiagnosticsService diagnosticsService = DiagnosticsService.getInstance();

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

      // 5. Automatic cleanup: when editor node is detached from scene, remove listener
      ret.sceneProperty()
          .addListener(
              (obs, oldScene, newScene) -> {
                if (newScene == null) {
                  diagnosticsService.removeListener(listener);
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

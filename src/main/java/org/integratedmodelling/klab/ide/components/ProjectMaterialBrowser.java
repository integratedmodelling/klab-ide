package org.integratedmodelling.klab.ide.components;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.fileicons.FileIcons;
import org.kordamp.ikonli.fontawesome5.FontAwesomeBrands;
import org.integratedmodelling.klabeditor.FileLanguageSupport;
import org.integratedmodelling.klabeditor.MarkdownPreview;
import org.integratedmodelling.klabeditor.MonacoEditorView;

/** Authoring-only browser using the generic Resources retrieve, submit and delete operations. */
final class ProjectMaterialBrowser extends BorderPane implements AutoCloseable {
  private static final int MAX_BYTES = 32 * 1024 * 1024;
  private final ResourcesService service;
  private final UserScope user;
  private final String project;
  private final Consumer<List<ResourceSet>> changes;
  private final Runnable closeAction;
  private final java.util.concurrent.ExecutorService commands = Executors.newSingleThreadExecutor(r -> {
    var thread = new Thread(r, "project-material"); thread.setDaemon(true); return thread;
  });
  private final TreeView<ProjectMaterial> tree = new TreeView<>();
  private final TextArea text = new TextArea();
  private final StackPane preview = new StackPane(text);
  private MonacoEditorView editor;
  private MarkdownPreview markdown;
  private MaterialText content;
  private String savedSource;
  private boolean editorReady;
  private RepositoryState repositoryState;
  private Runnable publishAction;
  private final Label status = new Label("Loading project material…");
  private final IconButton folder = toolbarButton(Evaicons.FOLDER_ADD_OUTLINE, "folder", "New folder…", () -> create(true));
  private final IconButton file = toolbarButton(Evaicons.FILE_ADD_OUTLINE, "file", "New file…", () -> create(false));
  private final IconButton upload = toolbarButton(Evaicons.UPLOAD_OUTLINE, "import", "Import files…", this::importFiles);
  private final IconButton download = toolbarButton(Evaicons.DOWNLOAD_OUTLINE, "export", "Export…", this::exportFile);
  private final IconButton delete = toolbarButton(Evaicons.TRASH_2_OUTLINE, "delete", "Delete…", this::deleteSelected);
  private final IconButton save = toolbarButton(Evaicons.SAVE_OUTLINE, "save", "Save", this::save);
  private final IconButton edit = toolbarButton(Evaicons.EDIT_OUTLINE, "edit", "Edit file", this::editSelected);
  private final IconButton view = toolbarButton(Evaicons.EYE_OUTLINE, "view", "Preview file", this::viewSelected);
  private final IconButton publish = toolbarButton(Evaicons.CLOUD_UPLOAD_OUTLINE, "publish", "Commit and publish project…", () -> { if (publishAction != null) publishAction.run(); });
  private final IconButton refresh = toolbarButton(Evaicons.REFRESH_OUTLINE, "refresh", "Refresh", () -> { if (discardDraft()) refresh(); });
  private final IconButton close = toolbarButton(Evaicons.CLOSE_OUTLINE, "close", "Close browser", this::requestClose);
  private ResourceInfo access;
  private TreeItem<ProjectMaterial> selected;
  private ProjectMaterial opened;
  private boolean dirty, loadingText, busy, closed, restoringSelection;
  private long revision;
  private long selectionRevision;
  private String editAfterOpen;

  ProjectMaterialBrowser(ResourcesService service, UserScope user, String project,
      Consumer<List<ResourceSet>> changes, Runnable closeAction) {
    this.service = service; this.user = user; this.project = project; this.changes = changes;
    this.closeAction = closeAction;
    var spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
    var toolbar = new HBox(4, folder, file, upload, download, delete, edit, view, save, publish, refresh, spacer, close);
    toolbar.setPadding(new Insets(4));
    setTop(toolbar);
    tree.setPrefWidth(290);
    tree.setCellFactory(view -> new TreeCell<>() {
      private final IconLabel icon = new IconLabel(Theme.FOLDER_ICON, 16, "-color-fg-default");
      @Override protected void updateItem(ProjectMaterial value, boolean empty) {
        super.updateItem(value, empty);
        setText(empty || value == null ? null : value.getPath().isEmpty() ? "Additional material"
            : Theme.repositoryStatusPrefix(MaterialRepositoryStatus.status(repositoryState, value.getPath(), value.isDirectory())) + name(value.getPath()));
        if (empty || value == null) setGraphic(null);
        else { icon.setIcon(materialIcon(value), 16); setGraphic(icon); }
        setContextMenu(null);
        if (!empty && value != null && !value.isDirectory()) {
          var editFile = new MenuItem("Edit file");
          editFile.setDisable(busy || !canWrite(access));
          editFile.setOnAction(event -> {
            if (getTreeItem() == selected && opened != null) editSelected();
            else { editAfterOpen = value.getUrn(); tree.getSelectionModel().select(getTreeItem()); }
          });
          var menu = new ContextMenu(editFile);
          menu.setOnShowing(event -> editFile.setDisable(busy || !canWrite(access)
              || getTreeItem() == selected && opened != null && content == null));
          setContextMenu(menu);
        }
      }
    });
    var split = new SplitPane(tree, preview); split.setDividerPositions(0.3);
    setCenter(split); setBottom(status); BorderPane.setMargin(status, new Insets(8));
    status.setWrapText(true); text.setEditable(false); text.setWrapText(false);
    text.textProperty().addListener((observable, before, after) -> {
      if (!loadingText && opened != null && text.isEditable()) { dirty = !Objects.equals(after, savedSource); updateControls(); }
    });
    text.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
      if (event.isShortcutDown() && event.getCode() == javafx.scene.input.KeyCode.S && text.isEditable()) {
        event.consume(); save();
      }
    });
    tree.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
      if (restoringSelection || after == null) return;
      if (!discardDraft()) {
        editAfterOpen = null;
        restoringSelection = true; tree.getSelectionModel().select(before); restoringSelection = false;
        return;
      }
      selected = after;
      disposePresentation();
      opened = null; showText("");
      if (after.getValue().isDirectory()) {
        after.setExpanded(true); loadDirectory(after);
      } else openFile(after.getValue());
      updateControls();
    });
    refresh();
  }

  private static IconButton toolbarButton(Ikon icon, String id, String description, Runnable action) {
    var button = new IconButton(icon, 16, "-color-fg-default", "-color-fg-muted", false) {
      @Override protected void action() { action.run(); }
    };
    button.tooltip(description);
    button.setAccessibleText(description);
    button.setId("material-" + id);
    button.getStyleClass().add(atlantafx.base.theme.Styles.FLAT);
    button.setMinSize(28, 28); button.setPrefSize(28, 28); button.setMaxSize(28, 28);
    return button;
  }

  private void requestClose() {
    if (!busy && !closed && discardDraft()) closeAction.run();
  }

  static boolean canBrowse(ResourceInfo info) {
    return permits(info, CRUDOperation.CREATE) || canWrite(info);
  }

  static boolean canWrite(ResourceInfo info) {
    return permits(info, CRUDOperation.UPDATE) || permits(info, CRUDOperation.UPDATE_METADATA);
  }

  private static boolean permits(ResourceInfo info, CRUDOperation operation) {
    return info != null && info.getPermissions() != null
        && (info.getPermissions().contains(operation) || info.getPermissions().contains(CRUDOperation.ADMINISTER));
  }

  static String childPath(String parent, String child) {
    if (child == null || child.isBlank() || child.contains("/") || child.contains("\\"))
      throw new IllegalArgumentException("Enter one file or folder name");
    return ProjectMaterial.canonicalPath(parent.isEmpty() ? child : parent + "/" + child);
  }

  static String editableText(byte[] bytes) {
    var decoded = MaterialText.decode(bytes);
    return decoded == null ? null : decoded.source();
  }

  void updateRepositoryState(RepositoryState state) {
    repositoryState = state;
    tree.refresh(); updateControls();
  }

  void setOnPublish(Runnable action) { publishAction = action; updateControls(); }

  static ProjectMaterial importedFile(String project, String parent, java.nio.file.Path source) throws IOException {
    if (parent.isEmpty()) throw new IllegalArgumentException("Import files into an additional material folder");
    String path = childPath(parent, source.getFileName().toString());
    if (!Files.isRegularFile(source) || Files.size(source) > MAX_BYTES)
      throw new IOException("Import requires regular files no larger than 32 MiB: " + source.getFileName());
    byte[] bytes;
    try (var input = Files.newInputStream(source)) { bytes = input.readNBytes(MAX_BYTES + 1); }
    if (bytes.length > MAX_BYTES) throw new IOException("File exceeds 32 MiB: " + source.getFileName());
    return new ProjectMaterial(project, path, bytes);
  }

  private static String name(String path) { return path.substring(path.lastIndexOf('/') + 1); }

  private static Ikon materialIcon(ProjectMaterial material) {
    if (material.isDirectory()) return Theme.FOLDER_ICON;
    String filename = name(material.getPath()).toLowerCase(java.util.Locale.ROOT);
    String extension = filename.substring(filename.lastIndexOf('.') + 1);
    return switch (extension) {
      case "css", "scss", "sass" -> FontAwesomeBrands.CSS3_ALT;
      case "html", "htm" -> FontAwesomeBrands.HTML5;
      case "js", "mjs", "cjs" -> FontAwesomeBrands.JS_SQUARE;
      case "json", "json5" -> FileIcons.JSON_1;
      case "yaml", "yml" -> FileIcons.YAML;
      case "properties", "ini", "conf", "cfg", "toml" -> FileIcons.CONFIG;
      case "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico" -> FileIcons.IMAGE;
      case "woff", "woff2", "ttf", "otf" -> FileIcons.FONT;
      case "pdf" -> FileIcons.ADOBE_ACROBAT;
      case "md", "markdown" -> FontAwesomeBrands.MARKDOWN;
      case "zip", "gz", "tar", "7z" -> Evaicons.ARCHIVE_OUTLINE;
      case "txt", "csv", "tsv", "xml" -> Evaicons.FILE_TEXT_OUTLINE;
      default -> Evaicons.FILE_OUTLINE;
    };
  }
  boolean isBusy() { return busy; }

  boolean discardDraft() {
    if (!dirty) return true;
    var dialog = new Alert(Alert.AlertType.CONFIRMATION, "Discard unsaved material changes?",
        ButtonType.OK, ButtonType.CANCEL);
    if (getScene() != null) dialog.initOwner(getScene().getWindow());
    if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
    dirty = false; return true;
  }

  /** Recheck access on activation. A revoked grant clears both listing and downloaded content. */
  void refresh() {
    if (closed || busy) return;
    long request = ++revision;
    selectionRevision++;
    busy = true; updateControls();
    CompletableFuture.supplyAsync(() -> service.info(project, KnowledgeClass.PROJECT, ResourceInfo.class, user), commands)
        .whenComplete((info, error) -> Platform.runLater(() -> {
          if (closed || request != revision) return;
          busy = false; access = error == null ? info : null;
          if (!canBrowse(access)) { clearAccess(); return; }
          if (dirty && tree.getRoot() != null) {
            text.setEditable(canWrite(access) && opened != null && FileLanguageSupport.isPlainText(opened.getPath()));
            updateControls(); return;
          }
          status.setText("Empty folders remain local until files are added. Managed k.LAB folders are excluded.");
          var root = new MaterialItem(ProjectMaterial.coordinates(project + "/"));
          tree.setRoot(root); selected = root; opened = null; showText("");
          tree.getSelectionModel().select(root);
          root.setExpanded(true); loadDirectory(root); updateControls();
        }));
  }

  private void clearAccess() {
    revision++; selectionRevision++;
    busy = false;
    disposePresentation();
    access = null; dirty = false; opened = null; selected = null;
    tree.setRoot(null); showText("");
    status.setText("Additional material requires project create or write access."); updateControls();
  }

  private final class MaterialItem extends TreeItem<ProjectMaterial> {
    private boolean loaded, loading;
    MaterialItem(ProjectMaterial material) {
      super(material);
      expandedProperty().addListener((observable, before, after) -> { if (after) loadDirectory(this); });
    }
    @Override public boolean isLeaf() { return !getValue().isDirectory(); }
  }

  private void loadDirectory(TreeItem<ProjectMaterial> item) {
    if (!(item instanceof MaterialItem directory) || !item.getValue().isDirectory()
        || directory.loaded || directory.loading || closed) return;
    directory.loading = true;
    long request = revision;
    CompletableFuture.supplyAsync(() -> service.retrieve(item.getValue().getUrn(), ProjectMaterial.class, user), commands)
        .whenComplete((listing, error) -> Platform.runLater(() -> {
          directory.loading = false;
          if (closed || request != revision) return;
          if (error != null) {
            // Fail closed: a directory failure may mean that write permission was revoked.
            clearAccess(); status.setText("Cannot list project material: " + message(error)); return;
          }
          directory.loaded = true;
          var previous = new java.util.HashMap<String, TreeItem<ProjectMaterial>>();
          for (var child : item.getChildren()) previous.put(child.getValue().getUrn(), child);
          var children = new java.util.ArrayList<TreeItem<ProjectMaterial>>();
          if (listing != null) for (var entry : listing.getEntries()) {
            var child = previous.get(entry.getUrn());
            if (child == null) child = new MaterialItem(entry); else child.setValue(entry);
            children.add(child);
          }
          WorkspaceEditor.reconcileChildren(item, children);
        }));
  }

  private void openFile(ProjectMaterial material) {
    long request = ++selectionRevision; busy = true; updateControls();
    CompletableFuture.supplyAsync(() -> {
      var info = service.info(project, KnowledgeClass.PROJECT, ResourceInfo.class, user);
      if (!canBrowse(info)) throw new IllegalStateException("Project write access is unavailable");
      var result = service.retrieve(material.getUrn(), ProjectMaterial.class, user);
      var decoded = result == null ? null : MaterialText.decode(result.getContent());
      String html = decoded != null && FileLanguageSupport.languageForPath(material.getPath()).equals("markdown")
          ? MarkdownPreview.renderHtml(decoded.source(), Theme.CURRENT_THEME.isDark()) : null;
      return new Opened(result, info, decoded, html);
    }, commands).whenComplete((result, error) -> Platform.runLater(() -> {
      if (closed || request != selectionRevision) return;
      busy = false;
      var value = result == null ? null : result.material();
      if (error != null || value == null) {
        clearAccess(); status.setText("Cannot open material: " + (error == null ? "File no longer exists" : message(error)));
        return;
      }
      access = result.access();
      opened = value;
      content = result.text();
      savedSource = content == null ? null : content.source();
      if (content != null) {
        showText(savedSource);
        text.setEditable(canWrite(access) && FileLanguageSupport.isPlainText(value.getPath()));
        if (result.html() != null) {
          markdown = new MarkdownPreview(); markdown.showRendered(result.html());
          preview.getChildren().setAll(markdown);
        }
        if (Objects.equals(editAfterOpen, value.getUrn())) { editAfterOpen = null; editSelected(); }
      }
      else {
        var image = new Image(new ByteArrayInputStream(value.getContent()));
        if (!image.isError()) {
          var view = new ImageView(image); view.setPreserveRatio(true); view.setFitWidth(700); view.setFitHeight(600);
          preview.getChildren().setAll(new ScrollPane(view));
        } else preview.getChildren().setAll(new Label("Binary file · " + value.getContent().length + " bytes\nUse Export to view externally."));
      }
      status.setText(value.getPath()); updateControls();
    }));
  }

  private record Opened(ProjectMaterial material, ResourceInfo access, MaterialText text, String html) {}

  private void editSelected() {
    if (busy || opened == null || content == null || !canWrite(access)) return;
    if (FileLanguageSupport.isPlainText(opened.getPath())) {
      preview.getChildren().setAll(text); text.setEditable(true); text.requestFocus(); return;
    }
    if (editor == null) {
      String urn = opened.getUrn();
      var active = new MonacoEditorView("inmemory:///material/" + java.net.URLEncoder.encode(service.serviceId() + "/" + urn, StandardCharsets.UTF_8), ignored -> save());
      editor = active; editorReady = false;
      active.setUseSharedLspMetadata(false);
      active.setSaveAcknowledgementRequired(true);
      active.setMinimapVisible(false);
      active.setChangeListener(source -> Platform.runLater(() -> {
        if (editor != active || closed) return;
        dirty = !Objects.equals(source, savedSource); updateControls();
      }));
      active.runAfterEditorRendered(() -> {
        if (editor != active || closed) return;
        editorReady = true; active.requestEditorFocus(); updateControls();
      });
      active.loadEditor(savedSource, FileLanguageSupport.languageForPath(opened.getPath()), Theme.CURRENT_THEME.isDark() ? "vs-dark" : "vs");
    }
    preview.getChildren().setAll(editor); updateControls();
  }

  private void viewSelected() {
    if (opened == null || content == null || busy) return;
    String source = currentSource();
    if (FileLanguageSupport.languageForPath(opened.getPath()).equals("markdown")) {
      long request = selectionRevision;
      CompletableFuture.supplyAsync(() -> MarkdownPreview.renderHtml(source, Theme.CURRENT_THEME.isDark()), commands)
          .whenComplete((html, error) -> Platform.runLater(() -> {
            if (closed || request != selectionRevision) return;
            if (error != null) { status.setText(message(error)); return; }
            if (markdown == null) markdown = new MarkdownPreview();
            markdown.showRendered(html); preview.getChildren().setAll(markdown);
          }));
    } else {
      loadingText = true; text.setText(source); loadingText = false;
      text.setEditable(FileLanguageSupport.isPlainText(opened.getPath()) && canWrite(access));
      preview.getChildren().setAll(text);
    }
  }

  private String currentSource() { return editor != null && editorReady ? editor.getText() : text.getText(); }

  private void disposePresentation() {
    if (editor != null) { editor.close(); editor = null; }
    if (markdown != null) { markdown.close(); markdown = null; }
    editorReady = false; content = null; savedSource = null;
  }

  private void showText(String source) {
    loadingText = true; text.setText(source); loadingText = false;
    dirty = false; text.setEditable(false); preview.getChildren().setAll(text);
  }

  private void updateControls() {
    boolean available = !closed && !busy && canBrowse(access);
    var value = selected == null ? null : selected.getValue();
    boolean directory = value != null && value.isDirectory();
    folder.setDisable(!available || !directory);
    file.setDisable(!available || !directory || value.getPath().isEmpty());
    upload.setDisable(file.isDisable());
    download.setDisable(!available || opened == null);
    save.setDisable(!available || !dirty || !canWrite(access) || editor != null && !editorReady);
    edit.setDisable(!available || opened == null || content == null || !canWrite(access));
    view.setDisable(!available || opened == null || content == null || editor != null && !editorReady);
    publish.setDisable(!available || dirty || publishAction == null || repositoryState == null
        || repositoryState.getCurrentBranch() == null || !permits(access, CRUDOperation.UPDATE));
    delete.setDisable(!available || value == null || value.getPath().isEmpty() || !permits(access, CRUDOperation.DELETE));
    refresh.setDisable(busy); tree.setDisable(busy); text.setDisable(busy);
    close.setDisable(busy);
    if (editor != null) editor.setDisable(busy || !canWrite(access));
  }

  private void create(boolean directory) {
    if (selected == null || !selected.getValue().isDirectory() || !discardDraft()) return;
    var dialog = new TextInputDialog(directory ? "assets" : "style.css");
    dialog.setTitle(directory ? "New material folder" : "New material file");
    dialog.setHeaderText("Name in " + (selected.getValue().getPath().isEmpty() ? project : selected.getValue().getPath()));
    if (getScene() != null) dialog.initOwner(getScene().getWindow());
    dialog.showAndWait().ifPresent(name -> {
      try {
        var payload = new ProjectMaterial(project, childPath(selected.getValue().getPath(), name), directory ? null : new byte[0]);
        payload.setDirectory(directory);
        mutate(() -> submit(payload, ResourcesService.SubmissionMode.ADD));
      } catch (IllegalArgumentException error) { status.setText(error.getMessage()); }
    });
  }

  private void importFiles() {
    if (selected == null || selected.getValue().getPath().isEmpty() || !discardDraft()) return;
    var chooser = new FileChooser(); chooser.setTitle("Import files into " + selected.getValue().getPath());
    var files = chooser.showOpenMultipleDialog(getScene().getWindow());
    if (files == null) return;
    String parent = selected.getValue().getPath();
    mutate(() -> {
      for (var external : files) {
        submit(importedFile(project, parent, external.toPath()), ResourcesService.SubmissionMode.ADD);
      }
    });
  }

  private void exportFile() {
    if (opened == null) return;
    var chooser = new FileChooser(); chooser.setTitle("Export project material"); chooser.setInitialFileName(name(opened.getPath()));
    var destination = chooser.showSaveDialog(getScene().getWindow());
    if (destination == null) return;
    byte[] bytes = opened.getContent();
    // Export is a local copy; keep the editor draft and project selection intact.
    busy = true; updateControls();
    CompletableFuture.runAsync(() -> {
      try { Files.write(destination.toPath(), bytes); }
      catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
    }, commands).whenComplete((ignored, error) -> Platform.runLater(() -> {
      if (closed) return;
      busy = false; status.setText(error == null ? "Exported " + destination.getName() : message(error)); updateControls();
    }));
  }

  private void save() {
    if (busy || opened == null || content == null || !canWrite(access) || editor != null && !editorReady) return;
    String source = currentSource();
    if (Objects.equals(source, savedSource)) return;
    var payload = new ProjectMaterial(project, opened.getPath(), content.encode(source));
    mutate(() -> submit(payload, ResourcesService.SubmissionMode.UPDATE), false, () -> {
      opened.setContent(payload.getContent()); savedSource = source;
      if (editor != null) editor.markSaved(source);
      dirty = !Objects.equals(currentSource(), source);
      status.setText("Saved and staged · publish using the project's Team actions");
    });
  }

  private void deleteSelected() {
    if (selected == null || selected.getValue().getPath().isEmpty() || !discardDraft()) return;
    var material = selected.getValue();
    var dialog = new Alert(Alert.AlertType.CONFIRMATION,
        "Delete " + material.getPath() + "?\nFolders must be empty. Tracked file removals are staged in Git.",
        ButtonType.OK, ButtonType.CANCEL);
    dialog.initOwner(getScene().getWindow());
    ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
    ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
    if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
      mutate(() -> check(service.delete(material.getUrn(), KnowledgeClass.ADDITIONAL_MATERIAL, user)), true);
  }

  private void submit(ProjectMaterial material, ResourcesService.SubmissionMode mode) {
    check(service.submit(material, mode, user));
  }

  private void check(List<ResourceSet> result) {
    if (result == null || result.isEmpty()) throw new IllegalStateException("The service did not accept the operation");
    if (result.stream().anyMatch(set -> Utils.Notifications.hasErrors(set.getNotifications())))
      throw new IllegalStateException(result.stream().flatMap(set -> set.getNotifications().stream())
          .filter(notification -> Utils.Notifications.hasErrors(List.of(notification)))
          .map(notification -> notification.getMessage()).findFirst().orElse("The service rejected the material operation"));
    changes.accept(result);
    for (var set : result) for (var change : set.getProjects()) {
      if (Objects.equals(project, change.getResourceUrn()) && change.getRepositoryState() != null) {
        var state = change.getRepositoryState();
        Platform.runLater(() -> { if (!closed) updateRepositoryState(state); });
      }
    }
  }

  @FunctionalInterface private interface Command { void run() throws Exception; }
  private void mutate(Command command) { mutate(command, false); }
  private void mutate(Command command, boolean deleting) {
    mutate(command, deleting, () -> {});
  }
  private void mutate(Command command, boolean deleting, Runnable committed) {
    if (closed || busy) return;
    var parent = selected == null ? tree.getRoot()
        : !deleting && selected.getValue().isDirectory() ? selected : selected.getParent();
    busy = true; updateControls();
    CompletableFuture.runAsync(() -> {
      try {
        var info = service.info(project, KnowledgeClass.PROJECT, ResourceInfo.class, user);
        if (!canBrowse(info)) throw new IllegalStateException("Project write access is unavailable");
        command.run();
      } catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
    }, commands).whenComplete((ignored, error) -> Platform.runLater(() -> {
      if (closed) return;
      busy = false;
      if (error == null) {
        dirty = false;
        committed.run();
        if (deleting) { opened = null; showText(""); selected = parent; tree.getSelectionModel().select(parent); }
        if (parent instanceof MaterialItem directory) { directory.loaded = false; loadDirectory(directory); }
        tree.refresh();
      } else {
        // Imports can partially succeed. Refresh the destination to show accepted files.
        if (!dirty && parent instanceof MaterialItem directory) { directory.loaded = false; loadDirectory(directory); }
        status.setText(message(error));
      }
      updateControls();
    }));
  }

  private static String message(Throwable error) {
    while (error.getCause() != null) error = error.getCause();
    return Objects.toString(error.getMessage(), error.getClass().getSimpleName());
  }

  @Override public void close() { closed = true; revision++; disposePresentation(); commands.shutdown(); }
}

package org.integratedmodelling.klab.ide.components;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.settings.ProjectSettings;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.cards.MetadataCard;
import org.integratedmodelling.klab.ide.components.cards.PermissionEditor;

/** One draft for project metadata and service-owned access rights. */
public class ProjectSettingsEditor extends VBox implements AutoCloseable {
  private final ResourcesService service;
  private final UserScope user;
  private final String workspace, project;
  private final BooleanSupplier locked;
  private final Consumer<Project> onSaved;
  private final Map<String, Object> metadata = new LinkedHashMap<>();
  private final TextField observer = new TextField(), definedWorldview = new TextField();
  private final VBox observerSection = new VBox(6);
  private String originalWorldview = "";
  private boolean administrator;
  private final TextField metadataKey = new TextField(), metadataValue = new TextField();
  private final ComboBox<String> metadataFormat = new ComboBox<>();
  private final PermissionEditor permissions = new PermissionEditor();
  private final VBox form = new VBox(12);
  private final StackPane metadataCard = new StackPane();
  private final Button save = new Button("Save settings");
  private final Label status = new Label("Loading settings and permissions...");
  private String originalPermissions;
  private boolean busy = true, editable, closed;
  private long accessRequest;

  public ProjectSettingsEditor(ResourcesService service, UserScope user, String workspace,
      String project, BooleanSupplier locked, Consumer<Project> onSaved) {
    this.service = service; this.user = user; this.workspace = workspace; this.project = project;
    this.locked = locked; this.onSaved = onSaved;
    setSpacing(12); setStyle("-fx-padding: 16;");
    observer.setPromptText("worldview:AgentConcept");
    definedWorldview.setPromptText("Worldview name (administrator only)");
    definedWorldview.textProperty().addListener((property, old, value) -> updateObserverVisibility());
    var observerHelp = new Label("Default observer semantics. A blank value contributes no default observer.");
    observerHelp.setWrapText(true);
    var permissionsHelp = new Label("Project access rights are stored by the Resources service. Edit access and your project lock are required to save.");
    permissionsHelp.setWrapText(true);
    var metadataHelp = new Label("Project-owned metadata from META-INF/manifest.json. Observer semantics are available only for worldview-defining projects; service-generated metadata is excluded.");
    metadataHelp.setWrapText(true);
    var key = metadataKey; key.setPromptText("Metadata key, e.g. dc:description");
    var value = metadataValue; value.setPromptText("Value");
    var format = metadataFormat; format.getItems().addAll("Text", "JSON"); format.setValue("Text");
    var put = new Button("Add / replace");
    var remove = new Button("Remove key");
    put.setOnAction(event -> {
      try {
        String name = key.getText().strip();
        if (name.isBlank()) throw new IllegalArgumentException("Enter a metadata key");
        Object parsed = parseValue(value.getText(), "JSON".equals(format.getValue()));
        if (name.equals(Worldview.USER_OBSERVER_SEMANTICS)) {
          if (!hasWorldview(definedWorldview.getText())) throw new IllegalArgumentException("This project does not define a worldview");
          if (!(parsed instanceof String text)) throw new IllegalArgumentException("Observer semantics must be text");
          observer.setText(text);
        } else metadata.put(name, parsed);
        rebuildMetadata();
        status.setText("Unsaved changes");
      } catch (RuntimeException failure) { status.setText("Metadata was not changed: " + failure.getMessage()); }
    });
    remove.setOnAction(event -> {
      String name = key.getText().strip();
      if (name.equals(Worldview.USER_OBSERVER_SEMANTICS)) {
        if (!hasWorldview(definedWorldview.getText())) { status.setText("This project does not define a worldview"); return; }
        observer.clear();
      }
      else metadata.remove(name);
      rebuildMetadata(); status.setText("Unsaved changes");
    });
    var entry = new HBox(8, key, value, format, put, remove);
    HBox.setHgrow(key, Priority.ALWAYS); HBox.setHgrow(value, Priority.ALWAYS);
    observerSection.getChildren().addAll(new Label("Default user observer"), observer, observerHelp);
    form.getChildren().addAll(new Label("Defined worldview (administrator only)"), definedWorldview, observerSection,
        new Separator(), new Label("Permissions"), permissions, permissionsHelp,
        new Separator(), new Label("Project metadata"), metadataHelp, entry, metadataCard);
    VBox.setVgrow(metadataCard, Priority.ALWAYS);
    var scroll = new ScrollPane(form); scroll.setFitToWidth(true);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    status.setWrapText(true);
    getChildren().addAll(scroll, save, status);
    save.setOnAction(event -> save());
    updateAccess();
    CompletableFuture.supplyAsync(() -> {
      var current = service.retrieve(project, Project.class, user);
      if (current == null) throw new IllegalStateException("Project unavailable");
      return new Loaded(current, service.info(project, org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.PROJECT, ResourceInfo.class, user));
    }).whenComplete((loaded, error) -> Platform.runLater(() -> {
      if (closed) return;
      busy = false;
      if (error != null || loaded == null || loaded.info() == null) {
        status.setText("Cannot load settings: " + message(error));
      } else {
        metadata.putAll(loaded.project().getSettings().getMetadata());
        originalWorldview = Objects.toString(loaded.project().getManifest().getDefinedWorldview(), "");
        definedWorldview.setText(originalWorldview);
        administrator = canAdminister(loaded.info());
        observer.setText(Objects.toString(metadata.getOrDefault(Worldview.USER_OBSERVER_SEMANTICS,
            loaded.project().getMetadata().get(Worldview.USER_OBSERVER_SEMANTICS)), ""));
        originalPermissions = loaded.info().getRights() == null ? "" : loaded.info().getRights().toString();
        permissions.setPermissions(originalPermissions);
        editable = canEdit(loaded.info());
        rebuildMetadata();
        status.setText(editable ? (locked.getAsBoolean() ? "Ready to edit settings." : "Lock the project to edit and save settings.") : "You do not have edit access to this project.");
      }
      updateAccess();
    }));
  }

  private record Loaded(Project project, ResourceInfo info) {}
  static Object parseValue(String value, boolean json) {
    return json ? Utils.Json.parseObject(value, Object.class) : value;
  }
  static boolean canEdit(ResourceInfo info) {
    return info != null && info.getPermissions() != null
        && (info.getPermissions().contains(CRUDOperation.UPDATE) || info.getPermissions().contains(CRUDOperation.ADMINISTER));
  }
  static boolean hasWorldview(String name) { return name != null && !name.isBlank(); }
  static boolean canAdminister(ResourceInfo info) {
    return info != null && info.getPermissions() != null && info.getPermissions().contains(CRUDOperation.ADMINISTER);
  }
  private void updateObserverVisibility() {
    boolean visible = hasWorldview(definedWorldview.getText());
    observerSection.setVisible(visible); observerSection.setManaged(visible);
  }
  public boolean isBusy() { return busy; }

  public void refreshAccess() {
    if (closed || busy) return;
    updateAccess();
    long request = ++accessRequest;
    CompletableFuture.supplyAsync(() -> service.info(project, org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.PROJECT, ResourceInfo.class, user))
        .whenComplete((info, error) -> Platform.runLater(() -> {
          if (closed || request != accessRequest) return;
          editable = error == null && canEdit(info);
          administrator = error == null && canAdminister(info);
          if (!administrator) definedWorldview.setText(originalWorldview);
          if (!editable) status.setText("Project edit access is unavailable; your draft is retained.");
          updateAccess();
        }));
  }

  private void updateAccess() {
    boolean disabled = busy || !editable || !locked.getAsBoolean();
    form.setDisable(disabled); save.setDisable(disabled);
    definedWorldview.setEditable(administrator);
    updateObserverVisibility();
  }

  private void rebuildMetadata() {
    var visible = Metadata.create(metadata);
    visible.remove(Worldview.USER_OBSERVER_SEMANTICS);
    var card = new MetadataCard(visible, new MetadataCard.Options().title("Stored project metadata")
        .emptyTitle("No project metadata").pathTree(false).complexValueRenderer((key, value) -> {
          var encoded = Utils.Json.asString(value);
          var label = new Label(encoded); label.setWrapText(true);
          var edit = new Button("Edit JSON");
          edit.setOnAction(event -> {
            metadataKey.setText(key); metadataValue.setText(encoded); metadataFormat.setValue("JSON"); metadataValue.requestFocus();
          });
          return new VBox(4, label, edit);
        }).editHandler((key, old, value) -> {
          metadata.put(key, value); status.setText("Unsaved changes"); return true;
        }), true);
    card.setPrefHeight(300);
    metadataCard.getChildren().setAll(card);
  }

  private void save() {
    if (busy || !editable || !locked.getAsBoolean()) return;
    // Focus loss commits any active MetadataCard inline edit before taking the snapshot.
    save.requestFocus();
    var settings = new ProjectSettings();
    settings.setMetadata(metadata);
    if (hasWorldview(definedWorldview.getText())) settings.getMetadata().put(Worldview.USER_OBSERVER_SEMANTICS, observer.getText().strip());
    if (administrator && !Objects.equals(originalWorldview, definedWorldview.getText().strip()))
      settings.setDefinedWorldview(definedWorldview.getText().strip());
    String submittedPermissions = permissions.getPermissions();
    if (!Objects.equals(originalPermissions, submittedPermissions)) settings.setPermissions(submittedPermissions);
    busy = true; ++accessRequest; updateAccess(); status.setText("Saving...");
    CompletableFuture.supplyAsync(() -> {
      var request = new ProjectImpl(); request.setUrn(workspace + "/" + project); request.setSettings(settings);
      var results = service.submit(request, ResourcesService.SubmissionMode.REPLACE, user);
      boolean failed = results == null || results.isEmpty();
      var details = new ArrayList<String>();
      if (results != null) for (var result : results) {
        if (Utils.Notifications.hasErrors(result.getNotifications())) {
          failed = true;
          result.getNotifications().forEach(notification -> details.add(notification.getMessage()));
        }
        result.getNotifications().forEach(notification -> Platform.runLater(() ->
            KlabIDEController.instance().handleNotification(notification)));
      }
      if (failed) throw new IllegalStateException(details.isEmpty()
          ? "The service returned no settings save result" : String.join("; ", details));
      try { return service.retrieve(project, Project.class, user); }
      catch (RuntimeException refreshFailure) { return null; }
    }).whenComplete((updated, error) -> Platform.runLater(() -> {
      if (closed) return;
      busy = false;
      if (error != null) status.setText("Save failed; draft retained: " + message(error));
      else {
        originalPermissions = submittedPermissions;
        if (settings.getDefinedWorldview() != null) originalWorldview = settings.getDefinedWorldview();
        metadata.clear(); metadata.putAll(settings.getMetadata());
        status.setText(updated == null ? "Settings saved; the project view could not be refreshed." : "Settings saved");
        if (updated != null) onSaved.accept(updated);
      }
      updateAccess(); refreshAccess();
    }));
  }

  private static String message(Throwable error) {
    if (error == null) return "No response from the service";
    while (error.getCause() != null) error = error.getCause();
    return error.getMessage();
  }
  @Override public void close() { closed = true; ++accessRequest; }
}
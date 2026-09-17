package org.integratedmodelling.klab.ide.components;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.cards.MetadataCard;
import org.integratedmodelling.klab.ide.components.cards.PermissionEditor;

/** Catalog-only workspace settings, with an interactive read-only audit mode. */
public class WorkspaceSettingsEditor extends VBox implements AutoCloseable {
  private final ResourcesService service;
  private final UserScope user;
  private final String urn;
  private final Consumer<ResourceInfo> onSaved;
  private final PermissionEditor permissions = new PermissionEditor();
  private final Map<String, Object> metadata = new LinkedHashMap<>();
  private final TextField key = new TextField(), value = new TextField();
  private final ComboBox<String> format = new ComboBox<>();
  private final StackPane metadataCard = new StackPane();
  private final Label owner = new Label(), access = new Label(), status = new Label("Loading workspace settings...");
  private final Button save = new Button("Save settings"), reload = new Button("Discard draft and reload");
  private final HBox changes;
  private boolean editable, busy, closed, loading, dirty;
  private long generation;
  private String originalPermissions;

  public WorkspaceSettingsEditor(ResourcesService service, UserScope user, String urn,
      Consumer<ResourceInfo> onSaved) {
    this.service = service; this.user = user; this.urn = urn; this.onSaved = onSaved;
    setSpacing(12); setStyle("-fx-padding: 16;");
    var explanation = new Label("Workspace permissions and metadata are stored by the Resources service. Only the owner or a service administrator can change them. No project lock is needed.");
    explanation.setWrapText(true); access.setWrapText(true); status.setWrapText(true);
    key.setPromptText("Metadata key"); value.setPromptText("Value");
    format.getItems().addAll("Text", "JSON"); format.setValue("Text");
    var put = new Button("Add / replace"); var remove = new Button("Remove key");
    changes = new HBox(8, key, value, format, put, remove);
    HBox.setHgrow(key, Priority.ALWAYS); HBox.setHgrow(value, Priority.ALWAYS);
    put.setOnAction(event -> {
      try {
        if (key.getText().isBlank()) throw new IllegalArgumentException("Enter a metadata key");
        metadata.put(key.getText().strip(), ProjectSettingsEditor.parseValue(value.getText(), "JSON".equals(format.getValue())));
        changed(); rebuildMetadata();
      } catch (RuntimeException failure) { status.setText("Metadata was not changed: " + failure.getMessage()); }
    });
    remove.setOnAction(event -> { metadata.remove(key.getText().strip()); changed(); rebuildMetadata(); });
    permissions.permissionsProperty().addListener((property, old, selected) -> { if (!loading) changed(); });
    var content = new VBox(12, owner, access, explanation, new Label("Permissions"), permissions,
        new Separator(), new Label("Workspace metadata"), changes, metadataCard);
    var scroll = new ScrollPane(content); scroll.setFitToWidth(true); VBox.setVgrow(scroll, Priority.ALWAYS);
    getChildren().addAll(scroll, new HBox(8, save, reload), status);
    save.setOnAction(event -> save());
    reload.setOnAction(event -> load(true));
    load(true);
  }

  static boolean canEdit(ResourceInfo info) {
    return info != null && info.getKnowledgeClass() == KnowledgeClass.WORKSPACE
        && info.getPermissions() != null && (info.getPermissions().contains(CRUDOperation.UPDATE)
        || info.getPermissions().contains(CRUDOperation.ADMINISTER));
  }
  static boolean canAudit(ResourceInfo info) {
    return canEdit(info) || info != null && info.getKnowledgeClass() == KnowledgeClass.WORKSPACE
        && info.getPermissions() != null && info.getPermissions().contains(CRUDOperation.READ);
  }
  static WorkspaceImpl request(String urn, Map<String, Object> metadata, String rights) {
    var request = new WorkspaceImpl(); request.setUrn(urn);
    request.setMetadata(Metadata.create(metadata));
    request.setPrivileges(rights == null ? null : ResourcePrivileges.create(rights));
    return request;
  }
  public boolean isBusy() { return busy; }
  public void refreshAccess() { if (!busy && !closed) load(false); }
  private void changed() { dirty = true; status.setText("Unsaved changes"); }

  private void load(boolean discard) {
    if (closed || busy) return;
    long request = ++generation; busy = true; updateControls();
    CompletableFuture.supplyAsync(() -> service.info(urn, KnowledgeClass.WORKSPACE, ResourceInfo.class, user))
        .whenComplete((info, error) -> Platform.runLater(() -> {
          if (closed || request != generation) return;
          busy = false;
          if (error != null || !canAudit(info)) {
            editable = false;
            access.setText("Workspace settings are unavailable to this user.");
            status.setText(error == null ? "Access denied or workspace unavailable." : message(error));
          } else {
            editable = canEdit(info);
            owner.setText("Owner: " + Objects.toString(info.getOwner(), "Not recorded (administrator can edit)"));
            access.setText(editable ? "You can edit these settings." : "Read only — you can audit permissions and metadata.");
            if (discard || !dirty) {
              loading = true;
              metadata.clear(); metadata.putAll(info.getMetadata());
              permissions.setPermissions(info.getRights() == null ? "" : info.getRights().toString());
              originalPermissions = permissions.getPermissions();
              loading = false; dirty = false;
              status.setText(editable ? "Ready to edit workspace settings." : "Workspace settings are read only.");
              onSaved.accept(info);
            } else status.setText("Your unsaved draft is retained.");
          }
          rebuildMetadata(); updateControls();
        }));
  }

  private void updateControls() {
    save.setDisable(busy || !editable); save.setVisible(editable); save.setManaged(editable);
    reload.setDisable(busy);
    metadataCard.setDisable(busy);
    changes.setDisable(busy || !editable); changes.setVisible(editable); changes.setManaged(editable);
    readOnly(permissions, busy || !editable);
  }
  private static void readOnly(Node node, boolean readOnly) {
    if (node instanceof TextInputControl field) field.setEditable(!readOnly);
    if (node instanceof CheckBox check) check.setDisable(readOnly);
    if (node instanceof TitledPane pane && pane.getContent() != null) readOnly(pane.getContent(), readOnly);
    else if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> readOnly(child, readOnly));
  }

  private void rebuildMetadata() {
    var options = new MetadataCard.Options().title("Stored workspace metadata").emptyTitle("No workspace metadata")
        .pathTree(false).complexValueRenderer((name, data) -> {
          var encoded = Utils.Json.asString(data); var label = new Label(encoded); label.setWrapText(true);
          if (!editable || busy) return label;
          var edit = new Button("Edit JSON");
          edit.setOnAction(event -> { key.setText(name); value.setText(encoded); format.setValue("JSON"); value.requestFocus(); });
          return new VBox(4, label, edit);
        });
    if (editable && !busy) options.editHandler((name, old, data) -> { metadata.put(name, data); changed(); return true; });
    var card = new MetadataCard(Metadata.create(metadata), options, true); card.setPrefHeight(300);
    metadataCard.getChildren().setAll(card);
  }

  private void save() {
    if (busy || !editable) return;
    save.requestFocus();
    String rights = permissions.getPermissions();
    var submitted = request(urn, metadata, Objects.equals(originalPermissions, rights) ? null : rights);
    long request = ++generation; busy = true; updateControls(); rebuildMetadata(); status.setText("Saving...");
    CompletableFuture.supplyAsync(() -> {
      var results = service.submit(submitted, ResourcesService.SubmissionMode.REPLACE, user);
      boolean failed = results == null || results.isEmpty();
      if (results != null) for (var result : results) {
        if (Utils.Notifications.hasErrors(result.getNotifications())) failed = true;
        result.getNotifications().forEach(notification -> Platform.runLater(() -> KlabIDEController.instance().handleNotification(notification)));
      }
      if (failed) throw new IllegalStateException("The service rejected workspace settings; see notifications");
      return true;
    }).whenComplete((saved, error) -> Platform.runLater(() -> {
      if (closed || request != generation) return;
      busy = false;
      if (error != null) {
        status.setText("Save failed; your draft is retained: " + message(error));
        updateControls(); rebuildMetadata();
      } else {
        dirty = false; originalPermissions = rights;
        status.setText("Settings saved."); load(true);
      }
    }));
  }
  private static String message(Throwable failure) {
    while (failure.getCause() != null) failure = failure.getCause();
    return Objects.toString(failure.getMessage(), "Service request failed");
  }
  @Override public void close() { closed = true; ++generation; }
}
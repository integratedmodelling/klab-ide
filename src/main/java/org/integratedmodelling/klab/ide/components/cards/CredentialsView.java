package org.integratedmodelling.klab.ide.components.cards;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.integratedmodelling.klab.api.authentication.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;

/** Administrator-only editor. Reads metadata; secret fields are always write-only. */
final class CredentialsView extends BorderPane {
  private final KlabService service;
  private final UserScope caller;
  private final ListView<ExternalAuthenticationCredentials.CredentialInfo> sets = new ListView<>();
  private final ComboBox<CredentialSchema> schemas = new ComboBox<>();
  private final TextField name = new TextField();
  private final TextField endpoint = new TextField();
  private final TextField users = new TextField();
  private final CheckBox allUsers = new CheckBox("Allow all authenticated users");
  private final TextField excludedUsers = new TextField();
  private final TextField excludedGroups = new TextField();
  private final Set<String> clearFields = new HashSet<>();
  private final TextField groups = new TextField();
  private final Label status = new Label();
  private final VBox fields = new VBox(8);
  private final Map<String, TextInputControl> inputs = new LinkedHashMap<>();
  private ExternalAuthenticationCredentials.CredentialInfo selected;
  private boolean disposed;

  CredentialsView(KlabService service, UserScope caller) {
    this.service = service;
    this.caller = caller;
    setPadding(new Insets(12));
    var title = new Label("Credential administration");
    title.setStyle("-fx-font-size: 20px;");
    var help = new Label("Reference credentials by set ID. Secrets are stored on this service and never returned.\nLeave secret fields blank when editing to keep their current values.");
    help.setWrapText(true);
    setTop(new VBox(8, title, help, status));
    sets.setCellFactory(ignored -> new ListCell<>() {
      @Override protected void updateItem(ExternalAuthenticationCredentials.CredentialInfo item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.description() + "\n" + item.host() + "\nID: " + item.key());
      }
    });
    sets.getSelectionModel().selectedItemProperty().addListener((o, previous, next) -> edit(next));
    var add = new Button("Add credential set");
    add.setOnAction(e -> { sets.getSelectionModel().clearSelection(); edit(null); });
    var left = new VBox(8, sets, add);
    VBox.setVgrow(sets, Priority.ALWAYS);
    left.setPrefWidth(330);
    schemas.setConverter(new StringConverter<>() {
      public String toString(CredentialSchema schema) { return schema == null ? "" : schema.label(); }
      public CredentialSchema fromString(String value) { return null; }
    });
    schemas.valueProperty().addListener((o, previous, next) -> renderFields());
    var save = new Button("Save credential set");
    save.setOnAction(e -> save());
    var delete = new Button("Delete credential set");
    delete.setOnAction(e -> delete());
    var form = new VBox(8, new Label("Name"), name, new Label("Endpoint URL (e.g. https://github.com/organization)"), endpoint,
        new Label("Authentication schema"), schemas, fields, allUsers, new Label("Authorized users (comma-separated)"), users,
        new Label("Authorized groups (comma-separated)"), groups,
        new Label("Excluded users (comma-separated)"), excludedUsers, new Label("Excluded groups (comma-separated)"), excludedGroups, new HBox(8, save, delete));
    form.setPadding(new Insets(12));
    var scroll = new ScrollPane(form);
    scroll.setFitToWidth(true);
    setCenter(new SplitPane(left, scroll));
    run(() -> {
      var available = service.getCredentialSchemas(caller);
      var installed = service.getCredentialInfo(caller);
      return new Object[] { available, installed };
    }, result -> {
      @SuppressWarnings("unchecked") var available = (List<CredentialSchema>) result[0];
      @SuppressWarnings("unchecked") var installed = (List<ExternalAuthenticationCredentials.CredentialInfo>) result[1];
      schemas.getItems().setAll(available);
      sets.getItems().setAll(installed);
      edit(null);
    });
    sceneProperty().addListener((o, previous, next) -> {
      if (previous != null && next == null) { disposed = true; inputs.values().forEach(TextInputControl::clear); }
    });
  }

  private void edit(ExternalAuthenticationCredentials.CredentialInfo info) {
    selected = info;
    allUsers.setSelected(info != null && info.privileges().isPublic());
    excludedUsers.setText(info == null ? "" : String.join(", ", info.privileges().getExcludedUsers()));
    excludedGroups.setText(info == null ? "" : String.join(", ", info.privileges().getExcludedGroups()));
    name.setText(info == null ? "" : info.description());
    endpoint.setText(info == null ? "" : info.host());
    users.setText(info == null ? "" : String.join(", ", info.privileges().getAllowedUsers()));
    groups.setText(info == null ? "" : String.join(", ", info.privileges().getAllowedGroups()));
    schemas.setDisable(info != null);
    schemas.setValue(info == null ? schemas.getItems().stream().findFirst().orElse(null)
        : schemas.getItems().stream().filter(s -> s.id().equals(info.schema())).findFirst().orElse(null));
    renderFields();
  }

  private void renderFields() {
    inputs.values().forEach(TextInputControl::clear);
    inputs.clear(); clearFields.clear(); fields.getChildren().clear();
    var schema = schemas.getValue();
    if (schema == null) return;
    for (var field : schema.fields()) {
      TextInputControl input = field.secret() ? new PasswordField() : new TextField();
      if (selected != null && selected.schema().equals(schema.id())) {
        if (!field.secret()) input.setText(selected.parameters().getOrDefault(field.name(), ""));
        else if (selected.configuredFields().contains(field.name())) input.setPromptText("Stored securely; leave blank to keep");
      }
      inputs.put(field.name(), input);
      fields.getChildren().addAll(new Label(field.label() + (field.required() ? " *" : "")), input);
      if (field.secret() && !field.required() && selected != null && selected.configuredFields().contains(field.name())) {
        var clear = new CheckBox("Clear stored " + field.label());
        clear.selectedProperty().addListener((o, previous, next) -> { if (next) clearFields.add(field.name()); else clearFields.remove(field.name()); });
        fields.getChildren().add(clear);
      }
    }
  }

  private void save() {
    var schema = schemas.getValue();
    if (schema == null || name.getText().isBlank() || endpoint.getText().isBlank()) {
      status.setText("Enter a name, endpoint and authentication schema."); return;
    }
    var values = new LinkedHashMap<String, String>();
    for (var field : schema.fields()) {
      String value = inputs.get(field.name()).getText();
      if (field.required() && value.isBlank() && (selected == null || !selected.configuredFields().contains(field.name()))) {
        status.setText("Required field: " + field.label()); return;
      }
      if (!field.secret() || !value.isEmpty()) values.put(field.name(), value);
    }
    clearFields.forEach(field -> values.put(field, ""));
    var credential = new ExternalAuthenticationCredentials();
    credential.setId(selected == null ? null : selected.key());
    credential.setName(name.getText()); credential.setScheme(schema.id()); credential.setParameters(values);
    var rights = new ResourcePrivileges();
    if (selected != null) {
      rights.setPublic(selected.privileges().isPublic());
      rights.setExcludedUsers(new HashSet<>(selected.privileges().getExcludedUsers()));
      rights.setExcludedGroups(new HashSet<>(selected.privileges().getExcludedGroups()));
      rights.setAllowedServices(new HashSet<>(selected.privileges().getAllowedServices()));
    }
    rights.setPublic(allUsers.isSelected());
    rights.setExcludedUsers(split(excludedUsers.getText())); rights.setExcludedGroups(split(excludedGroups.getText()));
    rights.setAllowedUsers(split(users.getText())); rights.setAllowedGroups(split(groups.getText()));
    credential.setPrivileges(rights);
    String target = endpoint.getText();
    inputs.values().stream().filter(PasswordField.class::isInstance).forEach(TextInputControl::clear);
    run(() -> { service.addCredentials(target, credential, caller); return service.getCredentialInfo(caller); },
        result -> { sets.getItems().setAll(result); edit(null); status.setText("Credential set saved."); });
  }

  private static Set<String> split(String value) {
    var result = new HashSet<String>();
    for (String item : value.split(",")) if (!item.isBlank()) result.add(item.trim());
    return result;
  }

  private void delete() {
    if (selected == null) return;
    var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
        "Delete credential set “" + selected.description() + "”? References to its ID will stop working.", ButtonType.CANCEL, ButtonType.OK);
    if (getScene() != null) confirmation.initOwner(getScene().getWindow());
    if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
    String id = selected.key();
    run(() -> { service.removeCredentials(id, caller); return service.getCredentialInfo(caller); },
        result -> { sets.getItems().setAll(result); edit(null); status.setText("Credential set deleted."); });
  }

  private <T> void run(Supplier<T> operation, java.util.function.Consumer<T> success) {
    setDisable(true); status.setText("Working…");
    CompletableFuture.supplyAsync(operation).whenComplete((result, error) -> Platform.runLater(() -> {
      if (disposed) return;
      setDisable(false);
      if (error != null) status.setText("Credential operation failed. Verify your administrator access and input.");
      else { status.setText(""); success.accept(result); }
    }));
  }
}

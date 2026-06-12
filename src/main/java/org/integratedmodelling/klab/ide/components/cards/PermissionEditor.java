package org.integratedmodelling.klab.ide.components.cards;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;

/**
 * JavaFX equivalent of the SWT permission editor used by the legacy publish-resource wizard.
 *
 * <p>The editor accepts comma- or whitespace-separated group and user names and exposes the encoded
 * k.LAB permission string through {@link #getPermissions()} and {@link #permissionsProperty()}.
 */
public class PermissionEditor extends VBox {

  private final TextField allowedGroups = new TextField();
  private final TextField allowedUsers = new TextField();
  private final TextField excludedGroups = new TextField();
  private final TextField excludedUsers = new TextField();
  private final CheckBox publicCheckBox = new CheckBox("Public");
  private final Label permissionsLabel = new Label("Only owner has access");
  private final ReadOnlyStringWrapper permissions =
      new ReadOnlyStringWrapper(this, "permissions", "");

  private boolean updating;

  public PermissionEditor() {
    initializeLayout();
    initializeBehavior();
    refresh();
  }

  public PermissionEditor(String permissions) {
    this();
    setPermissions(permissions);
  }

  private void initializeLayout() {
    getStyleClass().add("permission-editor");
    setFillWidth(true);
    setSpacing(8);

    allowedGroups.setPromptText("GROUP1, GROUP2");
    allowedUsers.setPromptText("user1, user2");
    excludedGroups.setPromptText("GROUP1, GROUP2");
    excludedUsers.setPromptText("user1, user2");

    permissionsLabel.getStyleClass().add("permission-editor-empty-label");
    permissionsLabel.setAlignment(Pos.CENTER_RIGHT);
    permissionsLabel.setMaxWidth(Double.MAX_VALUE);

    HBox header = new HBox(8, publicCheckBox, permissionsLabel);
    header.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(permissionsLabel, Priority.ALWAYS);

    getChildren()
        .setAll(
            header,
            createPermissionSection("Allowed", allowedGroups, allowedUsers),
            createPermissionSection("Excluded", excludedGroups, excludedUsers));
  }

  private TitledPane createPermissionSection(
      String title, TextField groupField, TextField userField) {
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(6);
    grid.setPadding(new Insets(8, 10, 10, 10));

    ColumnConstraints labels = new ColumnConstraints();
    labels.setHalignment(HPos.RIGHT);
    labels.setMinWidth(64);

    ColumnConstraints fields = new ColumnConstraints();
    fields.setHgrow(Priority.ALWAYS);

    grid.getColumnConstraints().setAll(labels, fields);

    Label groups = new Label("Groups");
    Label users = new Label("Users");
    groupField.setMaxWidth(Double.MAX_VALUE);
    userField.setMaxWidth(Double.MAX_VALUE);
    GridPane.setHgrow(groupField, Priority.ALWAYS);
    GridPane.setHgrow(userField, Priority.ALWAYS);

    grid.add(groups, 0, 0);
    grid.add(groupField, 1, 0);
    grid.add(users, 0, 1);
    grid.add(userField, 1, 1);

    TitledPane pane = new TitledPane(title, grid);
    pane.setCollapsible(false);
    pane.setMaxWidth(Double.MAX_VALUE);
    return pane;
  }

  private void initializeBehavior() {
    publicCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> refresh());
    allowedGroups.textProperty().addListener((observable, oldValue, newValue) -> refresh());
    allowedUsers.textProperty().addListener((observable, oldValue, newValue) -> refresh());
    excludedGroups.textProperty().addListener((observable, oldValue, newValue) -> refresh());
    excludedUsers.textProperty().addListener((observable, oldValue, newValue) -> refresh());
  }

  private void refresh() {
    if (updating) {
      return;
    }

    ResourcePrivileges privileges = ResourcePrivileges.empty();
    if (publicCheckBox.isSelected()) {
      privileges.setPublic(true);
    } else {
      privileges.getAllowedGroups().addAll(tokenize(allowedGroups.getText()));
      privileges.getAllowedUsers().addAll(tokenize(allowedUsers.getText()));
    }

    privileges.getExcludedGroups().addAll(tokenize(excludedGroups.getText()));
    privileges.getExcludedUsers().addAll(tokenize(excludedUsers.getText()));

    permissions.set(encode(privileges));
    permissionsLabel.setText(permissions.get().isEmpty() ? "Only owner has access" : "");
  }

  private List<String> tokenize(String string) {
    List<String> ret = new ArrayList<>();
    if (string == null || string.isBlank()) {
      return ret;
    }

    for (String token : string.replace(',', ' ').split("\\s+")) {
      if (!token.isBlank()) {
        ret.add(token.trim());
      }
    }
    return ret;
  }

  public void setPermissions(String permissions) {
    ResourcePrivileges privileges = ResourcePrivileges.create(permissions);
    runOnFxThread(() -> load(privileges));
  }

  private void load(ResourcePrivileges privileges) {
    updating = true;
    try {
      publicCheckBox.setSelected(privileges.isPublic());
      allowedGroups.setText(join(privileges.getAllowedGroups()));
      allowedUsers.setText(join(privileges.getAllowedUsers()));
      excludedGroups.setText(join(privileges.getExcludedGroups()));
      excludedUsers.setText(join(privileges.getExcludedUsers()));
    } finally {
      updating = false;
    }
    refresh();
  }

  private void runOnFxThread(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }

  private String join(Set<String> strings) {
    return strings.stream().sorted(Comparator.naturalOrder()).reduce((a, b) -> a + ", " + b).orElse("");
  }

  private String encode(ResourcePrivileges privileges) {
    List<String> tokens = new ArrayList<>();
    if (privileges.isPublic()) {
      tokens.add("*");
    }
    addSorted(tokens, privileges.getAllowedGroups(), "");
    addSorted(tokens, privileges.getAllowedUsers(), "");
    addSorted(tokens, privileges.getExcludedGroups(), "!");
    addSorted(tokens, privileges.getExcludedUsers(), "!");
    return String.join(",", tokens);
  }

  private void addSorted(List<String> tokens, Collection<String> source, String prefix) {
    source.stream()
        .filter(token -> token != null && !token.isBlank())
        .sorted(Comparator.naturalOrder())
        .map(token -> prefix + token.trim())
        .forEach(tokens::add);
  }

  public String getPermissions() {
    return permissions.get();
  }

  public ReadOnlyStringProperty permissionsProperty() {
    return permissions.getReadOnlyProperty();
  }

  public CheckBox getPublicCheckBox() {
    return publicCheckBox;
  }

  public TextField getAllowedGroupsField() {
    return allowedGroups;
  }

  public TextField getAllowedUsersField() {
    return allowedUsers;
  }

  public TextField getExcludedGroupsField() {
    return excludedGroups;
  }

  public TextField getExcludedUsersField() {
    return excludedUsers;
  }
}

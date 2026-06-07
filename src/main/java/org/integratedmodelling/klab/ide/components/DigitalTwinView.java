package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.util.*;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.components.cards.DigitalTwinSmallViewComponent;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

public class DigitalTwinView extends BrowsablePage<DigitalTwinEditor, IDEContextScope> {

  //  private final Map<String, ContextScope> digitalTwins = new HashMap<>();
  private final Map<String, DigitalTwinEditor> openEditors = new HashMap<>();
  private String localServiceId;

  private List<Node> components = new ArrayList<>();
  private Node workspaceDialog;

  @Override
  public String getName() {
    return "Digital Twins";
  }

  @Override
  public Parent getView() {
    return this;
  }

  @Override
  public void reset() {}

  public List<RuntimeService> getServices() {
    return KlabIDEController.instance().user().getServices(RuntimeService.class).stream()
        /* .filter(
        s ->
            s.capabilities(KlabIDEController.modeler().user())
                .getPermissions()
                .contains(CRUDOperation.CREATE))*/
        .sorted(
            (s1, s2) ->
                Utils.URLs.isLocalHost(s1.getUrl()) && !Utils.URLs.isLocalHost(s2.getUrl())
                    ? -1
                    : (Utils.URLs.isLocalHost(s2.getUrl()) ? 0 : 1))
        .toList();
  }

  public List<ContextInfo> getContextList() {
    List<ContextInfo> ret = new ArrayList<>();
    for (var rService : getServices()) {
      ret.addAll(rService.getContextInfo(KlabIDEController.instance().user()));
    }
    return ret;
  }

  @Override
  protected void assetEditorSelected(DigitalTwinEditor asset) {
    if (KlabIDEController.instance().getFocalScope() != null
        && asset != null
        && KlabIDEController.instance()
            .getFocalScope()
            .getId()
            .equals(asset.getEditedAsset().getId())) {
      return;
    }
    KlabIDEController.instance()
        .setFocalScope(
            asset.getEditedAsset(), Utils.URLs.isLocalHost(asset.getEditedAsset().getUrl()));
  }

  @Override
  protected void assetEditorClosed(DigitalTwinEditor asset) {
    Logging.INSTANCE.info("Closing scope " + asset);
    if (openEditors.containsKey(asset.getId())) {
      openEditors.get(asset.getId()).close();
      removeEditor(openEditors.get(asset.getId()));
    }
    openEditors.remove(asset.getId());
    if (openEditors.isEmpty()) {
      hideBrowser();
    } else {
      updateBrowser();
    }
    // select whatever remains in the editor. When closing X with just X in the editor, X for some
    // reason remains selected, hence the check
    var nextEditor = getSelectedEditor();
    if (nextEditor instanceof DigitalTwinEditor digitalTwinEditor
        && !digitalTwinEditor.getEditedAsset().getId().equals(asset.getEditedAsset().getId())) {
      assetEditorSelected(digitalTwinEditor);
    } else {
      KlabIDEController.instance().setFocalScope(null, false);
    }
  }

  @Override
  protected void defineBrowser(VBox browserComponents) {

    browserComponents.getChildren().removeAll(components);
    components.clear();
    components.add(makeHeader("Digital Twins", this::addDigitalTwin));
    if (workspaceDialog != null) {
      components.add(workspaceDialog);
    }
    for (var dt : getContextList()) {
      //  skip the opened ones
      if (openEditors.containsKey(dt.getConfiguration().getId())) {
        continue;
      }
      var isLocal = Utils.URLs.isLocalHost(dt.getConfiguration().getUrl());
      var dtComponent =
          new DigitalTwinSmallViewComponent(dt, this::showDigitalTwin, this::removeDigitalTwin, isLocal);
      components.add(dtComponent);
      dtComponent.createContent();
    }
    browserComponents.getChildren().addAll(components);
  }

  private void addDigitalTwin() {
    this.workspaceDialog = createDigitalTwinDialog();
    updateBrowser();
  }

  private Node createDigitalTwinDialog() {

    var availableServices =
        KlabIDEController.instance().user().getServices(RuntimeService.class).stream()
            .filter(
                s ->
                    s.capabilities(KlabIDEController.instance().user())
                        .getPermissions()
                        .contains(CRUDOperation.CREATE))
            .toList();

    GridPane grid = new GridPane();
    grid.setHgap(6);
    grid.setVgap(6);
    grid.setStyle("-fx-background-color: -color-neutral-muted;");
    grid.setPadding(new Insets(6, 6, 6, 6));

    TextField workspaceTitle = new TextField();
    workspaceTitle.setPromptText("DT name");
    TextArea description = new TextArea();
    description.setPromptText("Description");
    description.setPrefRowCount(3);
    final ComboBox<String> serviceSelector = new ComboBox<>();
    serviceSelector
        .getItems()
        .addAll(availableServices.stream().map(RuntimeService::serviceName).toList());
    serviceSelector.setMaxWidth(Double.MAX_VALUE);
    var ok = new Button("Create");
    var cancel = new Button("Cancel");
    var service = (ResourcesService) null;
    ok.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS, Styles.SMALL);
    cancel.setOnAction(
        event -> {
          workspaceDialog = null;
          updateBrowser();
        });
    var buttons = new HBox(ok, cancel);
    buttons.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
    buttons.setSpacing(4);
    cancel.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER, Styles.SMALL);
    grid.add(new FontIcon(Theme.WORKSPACE_ICON), 0, 0);
    grid.add(workspaceTitle, 1, 0);
    grid.add(new FontIcon(Theme.EDIT_ICON), 0, 1);
    grid.add(description, 1, 1);
    GridPane.setFillWidth(serviceSelector, Boolean.TRUE);
    grid.getColumnConstraints().add(new ColumnConstraints());
    grid.getColumnConstraints()
        .add(new ColumnConstraints(200, 200, Double.MAX_VALUE, Priority.ALWAYS, HPos.LEFT, true));

    grid.add(new FontIcon(Theme.LOCAL_SERVICE_ICON), 0, 2);
    grid.add(serviceSelector, 1, 2);

    ComboBox<Persistence> persistenceCombo = new ComboBox<>();
    persistenceCombo.getItems().addAll(Persistence.values());
    persistenceCombo.setValue(Persistence.SERVICE_SHUTDOWN);
    persistenceCombo.setMaxWidth(Double.MAX_VALUE);
    persistenceCombo.setCellFactory(
        lv ->
            new ListCell<Persistence>() {
              @Override
              protected void updateItem(Persistence item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.description);
              }
            });
    persistenceCombo.setButtonCell(
        new ListCell<Persistence>() {
          @Override
          protected void updateItem(Persistence item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item.description);
          }
        });

    TextField accessField = new TextField();
    accessField.setEditable(false);
    accessField.setText(ResourcePrivileges.create(KlabIDEController.instance().user()).toString());
    Button accessChooser = new Button("", new FontIcon(Material2AL.LOCK_OPEN));
    accessChooser.setOnAction(
        e -> {
          Dialog<ButtonType> dialog = new Dialog<>();
          dialog.setTitle("Public Access Settings");

          Node genericContent = new VBox(); // Replace with your desired content

          dialog.getDialogPane().setContent(genericContent);
          dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

          dialog
              .showAndWait()
              .ifPresent(
                  result -> {
                    if (result == ButtonType.OK) {
                      // Handle OK button action
                    }
                  });
        });

    HBox publicAccessBox = new HBox(4, accessField, accessChooser);

    grid.add(new FontIcon(Material2AL.ACCESS_ALARMS), 0, 3);
    grid.add(persistenceCombo, 1, 3);
    grid.add(new FontIcon(Material2AL.ACCESSIBILITY), 0, 4, 2, 1);
    grid.add(publicAccessBox, 1, 4, 2, 1);
    grid.add(buttons, 0, 5, 2, 1);

    ok.setOnAction(
        event -> {
          var configuration =
              DigitalTwin.Configuration.builder()
                  .accessRights(ResourcePrivileges.create(accessField.getText()))
                  .name(workspaceTitle.getText())
                  .description(description.getText())
                  .persistence(persistenceCombo.getSelectionModel().getSelectedItem())
                  .build();
          createDigitalTwin(
              configuration,
              availableServices.get(serviceSelector.getSelectionModel().getSelectedIndex()));
          workspaceDialog = null;
          updateBrowser();
        });

    if (availableServices.isEmpty()) {
      grid.setDisable(true);
    } else {
      serviceSelector.getSelectionModel().select(0);
    }
    return grid;
  }

  private void createDigitalTwin(
      DigitalTwin.Configuration configuration, RuntimeService runtimeService) {
    var session = KlabIDEController.instance().user().getUserSession(runtimeService);
    Logging.INSTANCE.info(
        "Creating session " + session + " in service " + runtimeService.serviceName());
    if (session != null) {
      var context = session.createContext(configuration);
      if (context instanceof ClientContextScope clientContextScope) {
        showDigitalTwin(
            KlabIDEController.instance().requireDigitalTwinPeer(clientContextScope, null));
      }
    }
  }

  public void deselectDigitalTwin(IDEContextScope scope) {
    if (openEditors.containsKey(scope.getId())) {
      var editor = openEditors.get(scope.getId());
      editor.close();
      removeEditor(editor);
    }
  }

  public DigitalTwinEditor showDigitalTwin(ContextScope scope) {

    // FIXME need a deselectDigitalTwin, not a null here
    Logging.INSTANCE.info("Showing scope " + scope);

    DigitalTwinEditor ret = null;
    hideBrowser();
    var contextScope = KlabIDEController.instance().requireDigitalTwinPeer(scope, null);
    if (openEditors.containsKey(scope.getId())) {
      ret = openEditors.get(scope.getId());
      ret.requestFocus();
      selectEditor(ret);
    } else {
      ret =
          new DigitalTwinEditor(contextScope, contextScope.getService(RuntimeService.class), this);
      openEditors.put(scope.getId(), ret);
      addEditor(ret, scope.getName(), new FontIcon(Theme.DIGITAL_TWINS_ICON));
      ret.edit(ret.getRootAsset());
    }
    if (!(scope instanceof IDEContextScope)) {
      var fScope = KlabIDEController.instance().requireDigitalTwinPeer(scope, null);
      KlabIDEController.instance().setFocalScope(fScope, Utils.URLs.isLocalHost(scope.getUrl()));
    }
    return ret;
  }

  public void removeDigitalTwin(ContextScope scope) {
    hideBrowser();
    if (openEditors.containsKey(scope.getId())) {
      var editor = openEditors.get(scope.getId());
      editor.close();
      removeEditor(editor);
    }
  }
}

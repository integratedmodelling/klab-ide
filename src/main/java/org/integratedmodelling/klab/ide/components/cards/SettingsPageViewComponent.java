package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.ide.KlabIDEController;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

public abstract class SettingsPageViewComponent extends VBox {

  public SettingsPageViewComponent(Setting.Page settingPage) {
    super(10);
    setPadding(new Insets(0));
    VBox.setVgrow(this, Priority.ALWAYS);
    setMinHeight(360);

    var pageSettings = Arrays.stream(Setting.values()).filter(s -> s.page == settingPage).toList();

    TableView<Setting> table = new TableView<>();
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    table.setTableMenuButtonVisible(false);
    table
        .getStyleClass()
        .addAll(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    table.setMinHeight(360);

    TableColumn<Setting, Node> labelColumn = new TableColumn<>();
    labelColumn.setCellValueFactory(
        data -> {
          VBox labelBox = new VBox(2);
          Label nameLabel =
              new Label(
                  Utils.Strings.capitalize(data.getValue().name().replace("_", " ").toLowerCase()));
          nameLabel.setStyle("-fx-font-weight: bold");
          Label descLabel = new Label(data.getValue().description);
          descLabel.setStyle("-fx-font-size: 11px");
          descLabel.setWrapText(true);
          labelBox.getChildren().addAll(nameLabel, descLabel);
          return new SimpleObjectProperty<>(labelBox);
        });

    TableColumn<Setting, Node> inputColumn = new TableColumn<>();
    inputColumn.setCellValueFactory(
        data -> {
          Node input;
          if (data.getValue().valueClass == Boolean.class) {
            ToggleSwitch toggle = new ToggleSwitch();
            toggle.setSelected(
                KlabIDEController.instance()
                    .engine()
                    .getSettings()
                    .get(data.getValue(), Boolean.class));
            input = toggle;
            toggle.setOnMouseClicked(
                e -> {
                  onChangedSetting(data.getValue(), toggle.isSelected());
                });
          } else if (Integer.class.isAssignableFrom(data.getValue().valueClass)) {
            TextField field = new TextField();
            field.setText(
                KlabIDEController.instance()
                        .engine()
                        .getSettings()
                        .get(data.getValue(), Integer.class)
                    + "");
            field.setTextFormatter(
                new TextFormatter<>(
                    change ->
                        change.getControlNewText().matches("-?\\d*\\.?\\d*") ? change : null));
            field.setOnAction(
                e -> {
                  onChangedSetting(data.getValue(), Integer.parseInt(field.getText()));
                });
            input = field;
          } else if (Map.class.isAssignableFrom(data.getValue().valueClass)) {
            Button field = new Button("Execute");
            field.setOnAction(
                e -> {
                  // TODO use set/get interface to invoke the action; disable the button; set
                  //   a notification on result. This should be done on service settings
                  //                          KlabIDEController.modeler()
                  //                                           .engine());
                  onChangedSetting(data.getValue(), Map.of());
                });
            input = field;
          } else if (File.class.isAssignableFrom(data.getValue().valueClass)) {

            HBox fileBox = new HBox(10);
            TextField fileField = new TextField();
            fileField.setEditable(false);
            Button chooseButton = new Button("Choose...");
            fileBox.getChildren().addAll(fileField, chooseButton);
            HBox.setHgrow(fileBox, Priority.ALWAYS);
            HBox.setHgrow(fileField, Priority.ALWAYS);

            chooseButton.setOnAction(
                e -> {
                  FileChooser fileChooser = new FileChooser();
                  fileChooser.setTitle("Select File");
                  File selectedFile = fileChooser.showOpenDialog(getScene().getWindow());
                  if (selectedFile != null) {
                    fileField.setText(selectedFile.getAbsolutePath());
                    onChangedSetting(data.getValue(), selectedFile);
                  }
                });

            File currentValue =
                KlabIDEController.instance()
                    .engine()
                    .getSettings()
                    .get(data.getValue(), File.class);
            if (currentValue != null) {
              fileField.setText(currentValue.getAbsolutePath());
            }

            input = fileBox;

          } else {
            // TODO
            TextField field = new TextField();
            if (data.getValue().defaultValue != null) {
              field.setText(
                  KlabIDEController.instance()
                      .engine()
                      .getSettings()
                      .get(data.getValue(), Object.class)
                      .toString());
            }
            field.setOnAction(
                e -> {
                  onChangedSetting(data.getValue(), field.getText());
                });
            HBox.setHgrow(field, Priority.ALWAYS);
            input = field;
          }
          return new SimpleObjectProperty<>(input);
        });

    table.getColumns().addAll(labelColumn, inputColumn);
    table.getItems().addAll(pageSettings);
    table.setFixedCellSize(46);
    table
        .prefHeightProperty()
        .bind(table.fixedCellSizeProperty().multiply(Bindings.size(table.getItems())));

    ScrollPane scroll = new ScrollPane(table);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.setMinHeight(360);

    getChildren().add(scroll);
  }

  protected abstract void onChangedSetting(Setting setting, Object newValue);
}

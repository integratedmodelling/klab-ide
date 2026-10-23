package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.generic.UploadBox;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;

/** Input view for adapter batch imports and local legacy resource imports. */
public final class BatchResourceInput extends VBox {

  public record AdapterOption(
      ResourcesService service, AdapterDescriptor adapter, String serviceName) {
    public boolean legacy() {
      return adapter == null;
    }
  }

  public record Input(
      ResourcesService service, AdapterDescriptor adapter, String url, List<File> files) {}

  private final UploadBox uploadBox;

  public BatchResourceInput(
      List<AdapterOption> adapters,
      Function<Input, ? extends CompletionStage<Boolean>> onAccept,
      Runnable onFinished,
      Runnable onCancel) {
    super(12);
    setPadding(new Insets(24));
    setFillWidth(true);

    var title = new Label("Batch resource upload");
    title.getStyleClass().add(Styles.TITLE_2);

    var description =
        new Label(
            "Choose a batch-capable adapter, or import a folder of legacy resources into a local service.");
    description.setWrapText(true);
    description.getStyleClass().add(Styles.TEXT_MUTED);

    var adapterSelector = new ComboBox<AdapterOption>();
    adapterSelector.getItems().setAll(adapters);
    adapterSelector.setMaxWidth(Double.MAX_VALUE);
    adapterSelector.setCellFactory(ignored -> adapterCell());
    adapterSelector.setButtonCell(adapterCell());
    adapterSelector.getSelectionModel().selectFirst();

    var adapterLabel = new Label("Adapter");
    adapterLabel.setLabelFor(adapterSelector);

    var url = new TextField();
    url.setPromptText("https://example.org/dataset");
    url.setAccessibleText("Batch input URL");
    url.setMaxWidth(Double.MAX_VALUE);

    var urlLabel = new Label("URL");
    urlLabel.setLabelFor(url);

    uploadBox =
        new UploadBox(
            Configuration.INSTANCE.getTemporaryDataPath().toString(),
            "Drop a folder or one or more files",
            ignored -> {},
            (message, throwable) ->
                KlabIDEController.instance()
                    .handleNotification(Notification.error("Batch input failed: " + message)));
    uploadBox.setMaxWidth(Double.MAX_VALUE);
    VBox.setVgrow(uploadBox, Priority.ALWAYS);

    // Local legacy imports retain each resource folder rather than flattening uploaded filenames.
    var folders = FXCollections.<File>observableArrayList();
    var folderPrompt = new Label("Drop a folder containing legacy resource subfolders");
    var chooseFolder = new Button("Choose folder…");
    var folderBox = new VBox(12, folderPrompt, chooseFolder);
    folderBox.setAlignment(Pos.CENTER);
    folderBox.setPadding(new Insets(24));
    folderBox.setStyle("-fx-border-color: -color-border-muted; -fx-border-style: dashed; -fx-border-width: 3;");
    folderBox.setOnDragOver(event -> {
      if (event.getDragboard().hasFiles()
          && event.getDragboard().getFiles().stream().allMatch(File::isDirectory)) {
        event.acceptTransferModes(TransferMode.COPY);
      }
      event.consume();
    });
    folderBox.setOnDragDropped(event -> {
      var dropped = event.getDragboard().getFiles();
      boolean valid = !dropped.isEmpty() && dropped.stream().allMatch(File::isDirectory);
      if (valid) {
        folders.setAll(dropped);
        folderPrompt.setText(dropped.stream().map(File::getAbsolutePath)
            .collect(java.util.stream.Collectors.joining("\n")));
      }
      event.setDropCompleted(valid);
      event.consume();
    });
    chooseFolder.setOnAction(event -> {
      var chooser = new DirectoryChooser();
      chooser.setTitle("Choose legacy resources folder");
      var folder = chooser.showDialog(getScene().getWindow());
      if (folder != null) {
        folders.setAll(folder);
        folderPrompt.setText(folder.getAbsolutePath());
      }
    });
    var legacy = Bindings.createBooleanBinding(
        () -> adapterSelector.getValue() != null && adapterSelector.getValue().legacy(),
        adapterSelector.valueProperty());
    folderBox.visibleProperty().bind(legacy);
    folderBox.managedProperty().bind(folderBox.visibleProperty());
    for (var node : List.of(urlLabel, url, uploadBox)) {
      node.visibleProperty().bind(legacy.not());
      node.managedProperty().bind(node.visibleProperty());
    }

    var ok = new WaitButton("OK");
    var cancel = new Button("Cancel");
    ok.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
    cancel.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
    ok.disableProperty()
        .bind(
            Bindings.createBooleanBinding(
                () -> !canAccept(adapterSelector.getValue(), url.getText(),
                    legacy.get() ? !folders.isEmpty() : uploadBox.hasContent()),
                adapterSelector.valueProperty(),
                url.textProperty(),
                uploadBox.hasContentProperty(), folders));
    ok.setOnActionAsync(
        () -> {
          var option = adapterSelector.getValue();
          return onAccept.apply(
              new Input(
                  option.service(),
                  option.adapter(),
                  url.getText() == null ? "" : url.getText().strip(),
                  List.copyOf(option.legacy() ? folders : uploadBox.getUploadedFiles())));
        });
    ok.stateProperty()
        .addListener(
            (observable, oldState, newState) -> {
              if (newState == WaitButton.State.SUCCEEDED
                  || newState == WaitButton.State.FAILED) {
                onFinished.run();
              }
            });
    cancel
        .disableProperty()
        .bind(ok.stateProperty().isEqualTo(WaitButton.State.WAITING));
    cancel.setOnAction(event -> onCancel.run());
    var waiting = ok.stateProperty().isEqualTo(WaitButton.State.WAITING);
    adapterSelector.disableProperty().bind(waiting);
    url.disableProperty().bind(waiting);
    uploadBox.disableProperty().bind(waiting);
    folderBox.disableProperty().bind(waiting);

    var buttons = new HBox(6, ok, cancel);
    buttons.setAlignment(Pos.CENTER_RIGHT);

    getChildren()
        .addAll(title, description, adapterLabel, adapterSelector, urlLabel, url, uploadBox, folderBox, buttons);
  }

  public void dispose() {
    uploadBox.dispose();
  }

  static boolean canAccept(AdapterOption adapter, String url, boolean hasUploadContent) {
    return adapter != null
        && (!adapter.legacy() || adapter.service() != null && adapter.service().isLocal())
        && (hasUploadContent || !adapter.legacy() && url != null && !url.isBlank());
  }

  private static ListCell<AdapterOption> adapterCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(AdapterOption option, boolean empty) {
        super.updateItem(option, empty);
        setText(
            empty || option == null
                ? null
                : (option.legacy() ? "Bulk legacy import" : option.adapter().getName())
                    + " — " + option.serviceName());
      }
    };
  }
}

package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
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
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.generic.UploadBox;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;

/** Input view for starting a batch resource import with a batch-capable adapter. */
public final class BatchResourceInput extends VBox {

  public record AdapterOption(
      ResourcesService service, AdapterDescriptor adapter, String serviceName) {}

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
            "Choose a batch-capable adapter and provide either a URL or one or more files.");
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

    var ok = new WaitButton("OK");
    var cancel = new Button("Cancel");
    ok.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
    cancel.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
    ok.disableProperty()
        .bind(
            Bindings.createBooleanBinding(
                () -> !canAccept(adapterSelector.getValue(), url.getText(), uploadBox.hasContent()),
                adapterSelector.valueProperty(),
                url.textProperty(),
                uploadBox.hasContentProperty()));
    ok.setOnActionAsync(
        () -> {
          var option = adapterSelector.getValue();
          return onAccept.apply(
              new Input(
                  option.service(),
                  option.adapter(),
                  url.getText() == null ? "" : url.getText().strip(),
                  List.copyOf(uploadBox.getUploadedFiles())));
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

    var buttons = new HBox(6, ok, cancel);
    buttons.setAlignment(Pos.CENTER_RIGHT);

    getChildren()
        .addAll(title, description, adapterLabel, adapterSelector, urlLabel, url, uploadBox, buttons);
  }

  public void dispose() {
    uploadBox.dispose();
  }

  static boolean canAccept(AdapterOption adapter, String url, boolean hasUploadContent) {
    return adapter != null && ((url != null && !url.isBlank()) || hasUploadContent);
  }

  private static ListCell<AdapterOption> adapterCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(AdapterOption option, boolean empty) {
        super.updateItem(option, empty);
        setText(
            empty || option == null
                ? null
                : option.adapter().getName() + " — " + option.serviceName());
      }
    };
  }
}

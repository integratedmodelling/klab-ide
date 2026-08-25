package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.generic.LogViewer;
import org.integratedmodelling.klab.ide.components.generic.UploadBox;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ServicePageViewComponent extends VBox {
  private final KlabService service;
  private TabPane tabPane;
  //    private VBox exportPane;
  private ComboBox<String> schemaSelector;
  private VBox parameterForm;
  private VBox dropTarget;

  public ServicePageViewComponent(KlabService service) {
    this.service = service;
    createContent();
  }

  protected void createContent() {
    var card = new Card();
    VBox content = new VBox(10);
    content.setPadding(new Insets(10));

    Label nameLabel = new Label("Service: " + service.serviceName());
    nameLabel.setStyle("-fx-font-weight: bold");

    Hyperlink hostLink = new Hyperlink("Capabilities");
    hostLink.setOnAction(
        e ->
            KlabIDEApplication.instance()
                .getHostServices()
                .showDocument(service.getUrl() + "/public/capabilities"));

    Hyperlink statusLink = new Hyperlink("Status");
    statusLink.setOnAction(
        e ->
            KlabIDEApplication.instance()
                .getHostServices()
                .showDocument(service.getUrl() + "/public/status"));

    Hyperlink apiLink = new Hyperlink("API Documentation");
    apiLink.setOnAction(
        e ->
            KlabIDEApplication.instance()
                .getHostServices()
                .showDocument(service.getUrl() + "/api.html"));

    tabPane = new TabPane();
    tabPane.setBorder(Border.EMPTY);
    Tab infoTab = new Tab("Info");
    infoTab.setClosable(false);
    VBox infoContent = new VBox(10, nameLabel, hostLink, statusLink, apiLink);
    //      infoContent.setPadding(new Insets(10));
    infoTab.setContent(infoContent);
    parameterForm = new VBox(2);
    ScrollPane scrollPane = new ScrollPane();
    scrollPane.setContent(parameterForm);
    scrollPane.setFitToWidth(true);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scrollPane.setMinHeight(360);
    VBox.setVgrow(scrollPane, Priority.ALWAYS);
    Tab importTab = new Tab("Import");
    importTab.setClosable(false);
    ScrollPane importScroll = new ScrollPane();
    importScroll.setFitToWidth(true);
    VBox importPane = new VBox(10);
    importPane.setPadding(new Insets(10));
    importPane.setMinHeight(360);

    Tab settingsTab = new Tab("Settings");
    settingsTab.setClosable(false);
    ScrollPane settingsScroll = new ScrollPane();
    settingsScroll.setFitToWidth(true);
    VBox settingsPane = new VBox(10);
    settingsPane.setPadding(new Insets(10));
    settingsPane.setMinHeight(360);

    ComboBox<String> importSchemaSelector = new ComboBox<>();
    importSchemaSelector.setPromptText("Select Import Schema");
    var capabilities = service.capabilities(KlabIDEController.instance().user());
    var importSchemata = capabilities.getImportSchemata();
    final var schemaKey = new HashMap<String, ResourceTransport.Schema>();
    for (var schemaName : importSchemata.keySet()) {
      for (var schema : importSchemata.get(schemaName)) {
        var description =
            schema.getProperties().isEmpty()
                ? " (" + Utils.Strings.join(schema.getMediaTypes(), ", ") + ")"
                : " (parameters)";
        var name = schemaName + description;
        importSchemaSelector.getItems().add(name);
        schemaKey.put(name, schema);
      }
    }
    importSchemaSelector.setOnAction(
        e -> updateImportForm(schemaKey.get(importSchemaSelector.getValue())));

    importPane.getChildren().addAll(importSchemaSelector, scrollPane);
    importScroll.setContent(importPane);
    importTab.setContent(importScroll);

    Tab logTab = null;
    var instance = KlabIDEController.instance().getInstance(service);
    if (instance != null) {
      var configurationPath = instance.getConfigurationPath();
      var logFile =
          new File(
              configurationPath
                  + File.separator
                  + "logs"
                  + File.separator
                  + instance.getProduct().getType().relativeConfigurationPath()
                  + ".log");

      //        if (logFile.exists()) {
      var logView =
          new LogViewer(
              logFile.toPath(),
              EnumSet.of(LogViewer.Column.TIME, LogViewer.Column.LEVEL, LogViewer.Column.MESSAGE));

      // make another tab and set the logView in it
      logTab = new Tab("Logs", logView);
      //          settingsPane.getTabs().add(logTab);

      //        }
    }

    if (ServiceDashboard.hasAdministerPermission(capabilities)) {
      settingsPane
          .getChildren()
          .add(
              new SettingsPageViewComponent(
                switch (service.status().getServiceType()) {
                  case REASONER -> Setting.Page.REASONER;
                  case RESOURCES -> Setting.Page.RESOURCES;
                  case RESOLVER -> Setting.Page.RESOLVER;
                  case RUNTIME -> Setting.Page.RUNTIME;
                  default -> null; // won't happen
                },
                service.settings()) {
              @Override
              protected void onChangedSetting(Setting setting, Object newValue) {
                service.settings().set(setting, newValue);
              }
            });
      settingsScroll.setContent(settingsPane);
      settingsTab.setContent(settingsScroll);
    }

    tabPane.getTabs().addAll(infoTab, /*exportTab, */ importTab);
    if (ServiceDashboard.hasAdministerPermission(capabilities)) tabPane.getTabs().add(settingsTab);
    if (logTab != null) tabPane.getTabs().add(logTab);

    content.getChildren().addAll(tabPane);
    VBox.setVgrow(tabPane, Priority.ALWAYS);

    card.setBody(content);
    card.setMinHeight(440);
    this.getChildren().add(card);
  }

  private void updateImportForm(ResourceTransport.Schema schema) {

    parameterForm.getChildren().clear();

    Map<String, Object> userInput = new HashMap<>();
    AtomicReference<File> file = new AtomicReference<>();

    if (schema.getType() == ResourceTransport.Schema.Type.PROPERTIES) {
      var parameters = schema.getProperties();
      for (var parameter : parameters.entrySet()) {
        Label label = new Label(parameter.getKey());
        if (parameter.getValue().optional()) {
          label.setStyle("-fx-font-weight: bold;");
        } else {
          label.setStyle("-fx-text-fill: #dd0000; -fx-font-weight: bold;");
        }
        TextField input = new TextField();
        input.setPromptText(parameter.getValue().defaultValue());
        input
            .textProperty()
            .addListener(
                (observable, oldValue, newValue) -> {
                  userInput.put(parameter.getKey(), newValue);
                });
        parameterForm.getChildren().addAll(label, input);
      }
    } else {

      final var targetDir = Configuration.INSTANCE.getTemporaryDataPath();

      // Create callback for successful uploads
      Consumer<File> onSuccess =
          (uploadedFile) -> {
            file.set(uploadedFile);
            //              KlabIDEController.instance()
            //                  .handleNotifications(List.of(Notification.info("File upload
            // successful")));
          };

      // Create error handler
      BiConsumer<String, Throwable> onError =
          (message, throwable) -> {
            KlabIDEController.instance()
                .handleNotifications(List.of(Notification.error("Upload error: " + message)));
          };

      // Create the upload box
      UploadBox uploadBox =
          new UploadBox(targetDir.toString(), "Drop file or URL to upload", onSuccess, onError);

      parameterForm.getChildren().add(uploadBox);
    }

    var submitButton = new WaitButton("Submit");
    submitButton.setOnAction(
        () -> {
          var asset = file.get() == null ? schema.asset(userInput) : schema.asset(file.get());
          if (asset.isEmpty()) {
            KlabIDEController.instance()
                .handleNotifications(
                    List.of(Notification.error("Import failed: specifications are incomplete")));
            return false;
          }
          AtomicBoolean success = new AtomicBoolean(false);
          service
              .importAsset(schema, asset, Urn.UNDEFINED_URN, KlabIDEController.instance().user())
              .thenAccept(
                  resourceSet -> {
                    // TODO register the new resource, possibly open it
                    KlabIDEController.instance()
                        .handleNotifications(resourceSet.getNotifications());
                    success.set(true);

                    // Reset form fields
                    // Reset form after delay
                    // TODO use an executor
                    new Thread(
                            () -> {
                              try {
                                Thread.sleep(1500);
                                Platform.runLater(
                                    () -> {
                                      parameterForm.getChildren().clear();
                                      updateImportForm(schema);
                                    });
                              } catch (InterruptedException e) {
                                // Ignore
                              }
                            })
                        .start();
                  })
              .exceptionally(
                  t -> {
                    KlabIDEController.instance().handleNotification(Notification.error(t));
                    success.set(false);
                    return null;
                  })
              .join();
          return success.get();
        });
    HBox buttonBox = new HBox(10, submitButton);
    buttonBox.setAlignment(Pos.CENTER_RIGHT);
    buttonBox.setPadding(new Insets(10, 0, 0, 0));

    parameterForm.getChildren().add(buttonBox);
  }
}

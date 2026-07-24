package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.cards.*;
import org.integratedmodelling.klab.ide.components.generic.Notebook;
import org.integratedmodelling.klab.ide.components.generic.cli.DashboardLineReader;
import org.integratedmodelling.klab.ide.components.generic.cli.DashboardTerminal;
import org.integratedmodelling.klab.ide.components.generic.cli.REPLTextField;
import org.integratedmodelling.klab.ide.pages.Page;

public class NotebookViewer extends BorderPane implements Page {

  private final REPLTextField inputBox;
  private final Notebook notebook;
  private final Label messageLabel;
  private final Label descriptionLabel;
  private final Map<AssetViewComponent.Type, AssetViewComponent> componentMap = new LinkedHashMap<>();
  private DashboardTerminal terminal;
  private final DashboardLineReader lineReader;

  public NotebookViewer() {
    this("Notebook view", "Here you can...");
  }

  public NotebookViewer(String message) {
    this(message, "");
  }

  public NotebookViewer(String message, String description) {

    this.notebook = new Notebook();
    this.messageLabel = new Label(message == null ? "" : message);
    this.messageLabel.getStyleClass().add(Styles.TITLE_2);
    this.messageLabel.setMaxWidth(Double.MAX_VALUE);
    this.messageLabel.setAlignment(Pos.CENTER);
    this.messageLabel.setPadding(new Insets(10, 10, 0, 10));
    this.messageLabel.setStyle("-fx-text-fill: -color-fg-subtle; -fx-opacity: 0.65;");
    this.messageLabel.visibleProperty().bind(this.messageLabel.textProperty().isNotEmpty());
    this.messageLabel.managedProperty().bind(this.messageLabel.visibleProperty());

    this.descriptionLabel = new Label(description == null ? "" : description);
    this.descriptionLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    this.descriptionLabel.setMaxWidth(Double.MAX_VALUE);
    this.descriptionLabel.setAlignment(Pos.CENTER);
    this.descriptionLabel.setPadding(new Insets(0, 10, 10, 10));
    this.descriptionLabel.setWrapText(true);
    this.descriptionLabel.setStyle("-fx-opacity: 0.65;");
    this.descriptionLabel.visibleProperty().bind(this.descriptionLabel.textProperty().isNotEmpty());
    this.descriptionLabel.managedProperty().bind(this.descriptionLabel.visibleProperty());

    var messageOverlay = new VBox(6, messageLabel, descriptionLabel);
    messageOverlay.setAlignment(Pos.CENTER);
    messageOverlay.setMouseTransparent(true);
    messageOverlay
        .visibleProperty()
        .bind(
            messageLabel
                .visibleProperty()
                .or(descriptionLabel.visibleProperty())
                .and(notebook.emptyProperty()));
    messageOverlay.managedProperty().bind(messageOverlay.visibleProperty());
    this.setCenter(new StackPane(this.notebook, messageOverlay));
    var inputArea = new HBox();
    // Path for temporary history
    var historyPath = new File(Configuration.INSTANCE.getDataPath(), "history.txt").toPath();
    this.inputBox =
        new REPLTextField(
            this::executeCommand, KlabIDEController.instance().getCLI().getCommands(), historyPath);
    this.inputBox.setPromptText(
        "Enter a command or a valid URN. Type 'help' for command assistance");
    var contextPane = new Pane();
    contextPane.setStyle(
        "-fx-background: -color-bg-subtle; -fx-background-color: -color-bg-subtle;");
    contextPane.setPrefWidth(120);
    HBox.setHgrow(inputBox, Priority.ALWAYS);
    var inputBoxContainer = new HBox(this.inputBox);
    inputBoxContainer.setPadding(new Insets(24, 10, 10, 10));
    HBox.setHgrow(inputBoxContainer, Priority.ALWAYS);
    inputArea.getChildren().addAll(inputBoxContainer, contextPane);
    HBox.setHgrow(inputArea, Priority.ALWAYS);

    this.setBottom(inputArea);

    this.lineReader =
        new DashboardLineReader(
            this.inputBox,
            new DashboardLineReader.PrintCallback() {
              @Override
              public void onPrint(String text) {
                Logging.INSTANCE.info(text);
              }

              @Override
              public void onPrintAbove(String text) {
                Logging.INSTANCE.info(text);
              }
            });

    addComponent(new AboutViewComponent());
  }

  public String getMessage() {
    return messageLabel.getText();
  }

  public void setMessage(String message) {
    messageLabel.setText(message == null ? "" : message);
  }

  public String getDescription() {
    return descriptionLabel.getText();
  }

  public void setDescription(String description) {
    descriptionLabel.setText(description == null ? "" : description);
  }

  public void addComponent(BaseAssetViewComponent component) {
    notebook.addCard(
        component.getType().name(),
        component.getIcon(),
        component.getTitle(),
        component.getDescription(),
        component);
  }

  public void toggle(AssetViewComponent.Type type) {

    if (notebook.hasCard(type.name())) {
      notebook.focusCard(type.name());
    } else {
      var card =
          switch (type) {
            case Distribution -> new DistributionViewComponent();
            case UserInfo -> new UserViewComponent(KlabIDEController.instance().user());
            case ReasonerService ->
                new ServiceViewComponent(KlabService.Type.REASONER);
            case ResourcesService ->
                new ServiceViewComponent(KlabService.Type.RESOURCES);
            case ResolverService ->
                new ServiceViewComponent(KlabService.Type.RESOLVER);
            case RuntimeService ->
                new ServiceViewComponent(KlabService.Type.RUNTIME);
            case About -> new AboutViewComponent();
            case Settings -> new SettingsViewComponent();
            default -> throw new KlabInternalErrorException("unexpected component " + type);
          };
      notebook.addCard(type.name(), card.getIcon(), card.getTitle(), card.getDescription(), card);
      notebook.focusCard(type.name());
    }
  }

  private void executeCommand(String input) {
    var object = KlabIDEController.instance().getCLI().submit(input);
    notebook.collapseAll();
    var card = new CommandResult(object, input);
    notebook.addCard(
        Utils.Names.fastName(),
        card.getIcon(),
        input,
        "Output of command: " + input,
        card.createContent());
  }

  @Override
  public String getName() {
    return "Notebook";
  }

  @Override
  public Parent getView() {
    return this;
  }

  @Override
  public void reset() {}
}

package org.integratedmodelling.klab.ide.components;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.*;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.generic.Notebook;
import org.integratedmodelling.klab.ide.components.generic.cli.DashboardLineReader;
import org.integratedmodelling.klab.ide.components.generic.cli.DashboardTerminal;
import org.integratedmodelling.klab.ide.components.generic.cli.REPLTextField;
import org.integratedmodelling.klab.ide.pages.Page;

public class NotebookViewer extends BorderPane implements Page {

  private final REPLTextField inputBox;
  private final Notebook notebook;
  private final Map<Components.Type, Components.Component> componentMap = new LinkedHashMap<>();
  private DashboardTerminal terminal;
  private final DashboardLineReader lineReader;

  public NotebookViewer() {

    this.notebook = new Notebook();
    this.setCenter(this.notebook);
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
    this.setCenter(this.notebook);

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

    addComponent(new Components.About());
  }

  public void addComponent(Components.BaseComponent component) {
    notebook.addCard(
        component.getType().name(),
        component.getIcon(),
        component.getTitle(),
        component.getDescription(),
        component);
  }

  public void toggle(Components.Type type, Object... arguments) {

    if (notebook.hasCard(type.name())) {
      notebook.focusCard(type.name());
    } else {
      var card =
          switch (type) {
            case Distribution -> new Components.DistributionComponent();
            case UserInfo -> new Components.User(KlabIDEController.instance().user());
            case ServiceInfo -> new Components.Services();
            case About -> new Components.About();
            case Settings -> new Components.Settings();
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

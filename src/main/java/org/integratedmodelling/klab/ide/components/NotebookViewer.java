package org.integratedmodelling.klab.ide.components;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.*;
import org.integratedmodelling.common.commandline.KlabCommandLine;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.cli.Command;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.cli.DashboardLineReader;
import org.integratedmodelling.klab.ide.components.generic.cli.DashboardTerminal;
import org.integratedmodelling.klab.ide.components.generic.AutoCompleteTextField;
import org.integratedmodelling.klab.ide.components.generic.Notebook;
import org.integratedmodelling.klab.ide.components.generic.cli.REPLTextField;
import org.integratedmodelling.klab.ide.pages.Page;

public class NotebookViewer extends BorderPane implements Page {

  private final REPLTextField inputBox;
  private final Notebook notebook;
  private DashboardTerminal terminal;
  private DashboardLineReader lineReader;
  private final Map<Components.Type, Components.Component> componentMap = new LinkedHashMap<>();

  public NotebookViewer() {

    this.notebook = new Notebook();
    this.setCenter(this.notebook);
    var inputArea = new HBox();
    // Path for temporary history
    var historyPath = new File(Configuration.INSTANCE.getDataPath(), "history.txt").toPath();
    this.inputBox =
        new REPLTextField(
            this::executeCommand, KlabIDEController.instance().getCLI().getCommands(), historyPath);
    this.inputBox.setPromptText("Enter a command; 'help' for assistance");
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
        Components.Type.About.name(),
        Theme.DIGITAL_TWINS_ICON,
        component.getTitle(),
        "Subtitle TODO",
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
      notebook.addCard(
          type.name(), Theme.LOCAL_SERVICE_ICON, card.getTitle(), "Subtitle TODO", card);
      notebook.focusCard(type.name());
    }
  }

  private void executeCommand(String input) {
    var object = KlabIDEController.instance().getCLI().submit(input);
    notebook.collapseAll();
    notebook.addCard(
        Utils.Names.fastName(),
        Theme.LOCAL_SERVICE_ICON,
        input,
        "Output of command " + input,
        new CommandResult(object, input));
  }

  // FIXME substitute with CLI
  private List<Command> createMockCommands() {

    List<Command> commands = new ArrayList<>();

    commands.add(
        Command.builder("help", "Show help information for all commands", "Show help")
            .option(
                "--verbose", "-v", "Provide more context in the help output", "Show verbose help")
            .build());

    commands.add(
        Command.builder("list", "List resources", "List available resources")
            .option("--type", "-t", "Filter by type", "Specify the type of resources to list")
            .option("--all", "-a", "Show all items", "Include hidden or internal items in the list")
            .build());

    return commands;
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

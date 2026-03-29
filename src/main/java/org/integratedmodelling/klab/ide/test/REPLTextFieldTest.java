package org.integratedmodelling.klab.ide.test;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.cli.Command;
import org.integratedmodelling.klab.ide.components.generic.cli.REPLTextField;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** A small test application to test the REPLTextField features. */
public class REPLTextFieldTest extends Application {

  private TextArea outputArea;

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("REPLTextField Test Application");

    VBox root = new VBox(10);
    root.setPadding(new Insets(20));

    Label infoLabel =
        new Label(
            "REPLTextField Test. Try typing 'help', 'list', 'run', or use Up/Down for history.");
    infoLabel.setStyle("-fx-font-weight: bold;");

    outputArea = new TextArea();
    outputArea.setEditable(false);
    outputArea.setPrefHeight(300);
    outputArea.setFocusTraversable(false);

    // Define mock commands
    List<Command> commands = createMockCommands();

    // Path for temporary history
    Path historyPath = Paths.get(System.getProperty("user.home"), ".klab", "test_history.txt");

    // Create REPLTextField
    REPLTextField replField = new REPLTextField(this::executeCommand, commands, historyPath);
    replField.setPromptText("Enter command...");

    root.getChildren().addAll(infoLabel, outputArea, replField);

    Scene scene = new Scene(root, 600, 450);
    primaryStage.setScene(scene);
    primaryStage.show();

    log("REPL initialized. Commands: help, list, run, exit.");
    log("History stored in: " + historyPath.toAbsolutePath());
  }

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

  private void executeCommand(String input) {
    log("> " + input);

    String[] parts = input.split("\\s+");
    String cmd = parts[0].toLowerCase();

    switch (cmd) {
      case "help":
        log("Available commands: help, list, run, exit.");
        if (input.contains("--verbose")) {
          log("Detailed help: Use Up/Down keys to navigate history, Tab for autocomplete.");
        }
        break;
      case "list":
        log("Listing resources...");
        if (input.contains("--type")) {
          log("Filtering by type...");
        }
        break;
      case "run":
        log("Running task...");
        break;
      case "exit":
        log("Exiting...");
        System.exit(0);
        break;
      default:
        log("Unknown command: " + cmd);
    }
  }

  private void log(String message) {
    outputArea.appendText(message + "\n");
  }

  public static void main(String[] args) {
    launch(args);
  }
}

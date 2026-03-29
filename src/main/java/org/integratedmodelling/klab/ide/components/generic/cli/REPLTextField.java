package org.integratedmodelling.klab.ide.components.generic.cli;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.integratedmodelling.klab.api.cli.Command;
import org.integratedmodelling.klab.ide.components.generic.AutoCompleteTextField;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/** A specialized TextField for implementing a REPL, with history and autocompletion. */
public class REPLTextField extends AutoCompleteTextField {

  /** Marker used to separate the suggestion from its description in the string list. */
  private static final String DESCRIPTION_SEPARATOR = " \u2014 "; // EM DASH

  @FunctionalInterface
  public interface CommandExecutor {
    void execute(String command);
  }

  private final REPLHistory history;
  private final CommandExecutor executor;
  private final List<Command> commands;

  public REPLTextField(CommandExecutor executor, List<Command> commands, Path historyFile) {
    super(text -> getSuggestions(text, commands));
    this.executor = executor;
    this.commands = commands != null ? commands : new ArrayList<>();
    this.history = new REPLHistory(historyFile, 500);
    setupHandlers();
  }

  private static List<String> getSuggestions(String text, List<Command> commands) {
    if (text == null || text.trim().isEmpty()) {
      return Collections.emptyList();
    }

    String[] parts = text.split("\\s+", -1);
    if (parts.length == 0) {
      return Collections.emptyList();
    }

    // Handle navigation through subcommands
    Command currentCommand = null;
    List<Command> currentCommandList = commands;
    int lastCommandPartIndex = -1;

    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      if (part.isEmpty()) continue;

      final String p = part;
      Command next =
          currentCommandList.stream()
              .filter(c -> c.getName().equalsIgnoreCase(p))
              .findFirst()
              .orElse(null);

      if (next != null) {
        currentCommand = next;
        currentCommandList = next.getSubcommands();
        lastCommandPartIndex = i;
      } else {
        // Not a subcommand, might be an option or just text
        // Stop searching for commands
        break;
      }
    }

    String lastPart = parts[parts.length - 1].toLowerCase();

    // If we haven't found any command yet, suggest from the top level
    if (currentCommand == null && lastCommandPartIndex == -1) {
      return commands.stream()
          .filter(c -> c.getName().toLowerCase().startsWith(lastPart))
          .map(
              c ->
                  c.getName()
                      + (c.getShortDescription() != null
                          ? (DESCRIPTION_SEPARATOR + c.getShortDescription())
                          : ""))
          .collect(Collectors.toList());
    }

    // Suggestions could be subcommands or options of the current command
    List<String> suggestions = new ArrayList<>();

    // Add subcommands of the current command if they match
    if (currentCommand != null) {
      currentCommand.getSubcommands().stream()
          .filter(c -> c.getName().toLowerCase().startsWith(lastPart))
          .forEach(
              c -> {
                String suggestion = reconstructPath(parts, c.getName());
                if (c.getShortDescription() != null) {
                  suggestion += DESCRIPTION_SEPARATOR + c.getShortDescription();
                }
                suggestions.add(suggestion);
              });

      // Add options of the current command if they match
      currentCommand.getOptions().stream()
          .filter(o -> o.getName().toLowerCase().startsWith(lastPart))
          .forEach(
              o -> {
                String suggestion = reconstructPath(parts, o.getName());
                if (o.getShortDescription() != null) {
                  suggestion += DESCRIPTION_SEPARATOR + o.getShortDescription();
                }
                suggestions.add(suggestion);
              });
    }

    return suggestions;
  }

  private static String reconstructPath(String[] parts, String suggestion) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length - 1; i++) {
      sb.append(parts[i]).append(" ");
    }
    sb.append(suggestion);
    return sb.toString();
  }

  @Override
  protected String getReplacement(String suggestion) {
    int sepIndex = suggestion.indexOf(DESCRIPTION_SEPARATOR);
    if (sepIndex != -1) {
      return suggestion.substring(0, sepIndex) + " ";
    }
    return suggestion + " ";
  }

  @Override
  protected void populatePopup(List<String> searchResult, String text) {
    List<CustomMenuItem> menuItems = new LinkedList<>();
    int count = Math.min(searchResult.size(), getMaxEntries());
    for (int i = 0; i < count; i++) {
      final String rawResult = searchResult.get(i);
      String result = rawResult;
      String description = null;

      int sepIndex = rawResult.indexOf(DESCRIPTION_SEPARATOR);
      if (sepIndex != -1) {
        result = rawResult.substring(0, sepIndex);
        description = rawResult.substring(sepIndex + DESCRIPTION_SEPARATOR.length());
      }

      final String finalResult = result;

      int occurence;
      String lastPart = text;
      if (text.contains(" ")) {
        lastPart = text.substring(text.lastIndexOf(" ") + 1);
      }

      if (isCaseSensitive()) {
        occurence = result.indexOf(lastPart);
      } else {
        occurence = result.toLowerCase().indexOf(lastPart.toLowerCase());
      }

      // We use the whole result for the display if occurence is not found in the last part
      // (which shouldn't happen with our provider)
      TextFlow entryFlow = new TextFlow();
      if (occurence >= 0) {
        Text pre = new Text(result.substring(0, occurence));
        Text in = new Text(result.substring(occurence, occurence + lastPart.length()));
        in.setStyle(getTextOccurenceStyle());
        Text post = new Text(result.substring(occurence + lastPart.length()));
        entryFlow.getChildren().addAll(pre, in, post);
      } else {
        entryFlow.getChildren().add(new Text(result));
      }

      if (description != null) {
        Text descText = new Text(" (" + description + ")");
        descText.setStyle("-fx-fill: gray; -fx-font-style: italic;");
        entryFlow.getChildren().add(descText);
      }

      CustomMenuItem item = new CustomMenuItem(entryFlow, true);
      item.setOnAction(
          new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
              String replacement = getReplacement(rawResult);
              setText(replacement);
              positionCaret(replacement.length());
              entriesPopup.hide();
            }
          });
      menuItems.add(item);
    }
    entriesPopup.getItems().clear();
    entriesPopup.getItems().addAll(menuItems);
  }

  private void setupHandlers() {
    // Handle ENTER for execution
    this.addEventHandler(
        KeyEvent.KEY_PRESSED,
        event -> {
          if (event.getCode() == KeyCode.ENTER) {
            String text = getText();
            if (text != null && !text.trim().isEmpty()) {
              history.add(text);
              if (executor != null) {
                executor.execute(text);
              }
              clear();
            }
            event.consume();
          } else if (event.getCode() == KeyCode.UP) {
            String prev = history.previous();
            if (prev != null) {
              setText(prev);
              positionCaret(prev.length());
            }
            event.consume();
          } else if (event.getCode() == KeyCode.DOWN) {
            String next = history.next();
            if (next != null) {
              setText(next);
              positionCaret(next.length());
            }
            event.consume();
          }
        });

    // Ensure we reset history cursor when text is manually cleared
    this.textProperty()
        .addListener(
            (obs, oldText, newText) -> {
              if (newText == null || newText.isEmpty()) {
                history.resetCursor();
              }
            });

    // Hide popup on execution
    this.setOnAction(
        event -> {
          // AutoCompleteTextField uses setOnAction for selection from popup sometimes.
          // If the user selects a suggestion, this might trigger.
        });
  }

  /**
   * Set a new set of available commands.
   *
   * @param newCommands the new catalog of commands.
   */
  public void setCommands(List<Command> newCommands) {
    this.commands.clear();
    if (newCommands != null) {
      this.commands.addAll(newCommands);
    }
  }
}

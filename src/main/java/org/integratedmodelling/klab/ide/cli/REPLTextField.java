package org.integratedmodelling.klab.ide.cli;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.integratedmodelling.klab.ide.components.generic.AutoCompleteTextField;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A specialized TextField for implementing a REPL, with history and autocompletion.
 */
public class REPLTextField extends AutoCompleteTextField {

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

        String firstPart = parts[0].toLowerCase();

        // If we are on the first part, suggest commands
        if (parts.length == 1) {
            return commands.stream()
                    .map(Command::getName)
                    .filter(name -> name.toLowerCase().startsWith(firstPart))
                    .collect(Collectors.toList());
        }

        // If we have a command, suggest options
        Command command = commands.stream()
                .filter(c -> c.getName().equalsIgnoreCase(parts[0]))
                .findFirst()
                .orElse(null);

        if (command != null) {
            String lastPart = parts[parts.length - 1].toLowerCase();
            return command.getOptions().stream()
                    .map(Option::getName)
                    .filter(name -> name.toLowerCase().startsWith(lastPart))
                    .map(optionName -> {
                        // Reconstruct the command line with the suggested option
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length - 1; i++) {
                            sb.append(parts[i]).append(" ");
                        }
                        sb.append(optionName);
                        return sb.toString();
                    })
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private void setupHandlers() {
        // Handle ENTER for execution
        this.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
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
        this.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isEmpty()) {
                history.resetCursor();
            }
        });

        // Hide popup on execution
        this.setOnAction(event -> {
            // AutoCompleteTextField uses setOnAction for selection from popup sometimes.
            // If the user selects a suggestion, this might trigger.
        });
    }

    /**
     * Set a new set of available commands.
     * @param newCommands the new catalog of commands.
     */
    public void setCommands(List<Command> newCommands) {
        this.commands.clear();
        if (newCommands != null) {
            this.commands.addAll(newCommands);
        }
    }
}

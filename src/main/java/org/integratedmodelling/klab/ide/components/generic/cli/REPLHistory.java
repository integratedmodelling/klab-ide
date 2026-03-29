package org.integratedmodelling.klab.ide.components.generic.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages REPL command history with persistence.
 */
public class REPLHistory {
    private final List<String> history;
    private int cursor;
    private final Path storagePath;
    private final int maxEntries;

    public REPLHistory(Path storagePath, int maxEntries) {
        this.storagePath = storagePath;
        this.maxEntries = maxEntries;
        this.history = loadHistory();
        this.cursor = history.size();
    }

    private List<String> loadHistory() {
        if (storagePath != null && Files.exists(storagePath)) {
            try {
                return Files.lines(storagePath)
                        .filter(line -> !line.trim().isEmpty())
                        .collect(Collectors.toCollection(ArrayList::new));
            } catch (IOException e) {
                // Silently handle or log
            }
        }
        return new ArrayList<>();
    }

    public void add(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return;
        }
        
        // Don't duplicate consecutive identical commands
        if (!history.isEmpty() && history.get(history.size() - 1).equals(entry)) {
            cursor = history.size();
            return;
        }

        history.add(entry);
        if (history.size() > maxEntries) {
            history.remove(0);
        }
        cursor = history.size();
        saveHistory();
    }

    private void saveHistory() {
        if (storagePath != null) {
            try {
                Files.createDirectories(storagePath.getParent());
                Files.write(storagePath, history, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                // Silently handle or log
            }
        }
    }

    public String previous() {
        if (cursor > 0) {
            cursor--;
            return history.get(cursor);
        }
        return null;
    }

    public String next() {
        if (cursor < history.size() - 1) {
            cursor++;
            return history.get(cursor);
        } else if (cursor == history.size() - 1) {
            cursor++;
            return "";
        }
        return null;
    }

    public void resetCursor() {
        cursor = history.size();
    }
}

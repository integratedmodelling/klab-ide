package org.integratedmodelling.klab.ide.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub class representing a command in the REPL.
 */
public class Command {
    private final String name;
    private final String description;
    private final List<Option> options;

    public Command(String name, String description) {
        this.name = name;
        this.description = description;
        this.options = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Option> getOptions() {
        return options;
    }

    public void addOption(Option option) {
        this.options.add(option);
    }
}

package org.integratedmodelling.klab.ide.cli;

/**
 * Stub class representing an option for a command.
 */
public class Option {
    private final String name;
    private final String description;
    private final boolean hasValue;

    public Option(String name, String description, boolean hasValue) {
        this.name = name;
        this.description = description;
        this.hasValue = hasValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasValue() {
        return hasValue;
    }
}

package com.frandm.advancedbackups.backup.model;

public enum BackupType {
    FULL("full", "FULL"),
    INCREMENTAL("incremental", "PARTIAL"),
    DIFFERENTIAL("differential", "DIFFERENTIAL");

    private final String commandName;
    private final String displayName;

    BackupType(String commandName, String displayName) {
        this.commandName = commandName;
        this.displayName = displayName;
    }

    public String commandName() {
        return commandName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static BackupType fromCommandName(String value) {
        for (BackupType type : values()) {
            if (type.commandName.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown backup type: " + value);
    }
}

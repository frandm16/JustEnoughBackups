package com.frandm.justenoughbackups.backup.model;

public enum BackupType {
    FULL("full", "FULL"),
    PARTIAL("partial", "PARTIAL"),
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

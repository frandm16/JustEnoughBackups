package com.frandm.advancedbackups.backup.model;

public enum BackupType {
    FULL("full"),
    INCREMENTAL("incremental"),
    DIFFERENTIAL("differential");

    private final String commandName;

    BackupType(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
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

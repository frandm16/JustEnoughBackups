package com.frandm.advancedbackups;

public enum BackupType {
    FULL_BACKUPS("full"),
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

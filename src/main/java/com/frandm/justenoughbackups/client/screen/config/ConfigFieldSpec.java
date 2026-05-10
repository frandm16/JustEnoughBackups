package com.frandm.justenoughbackups.client.screen.config;

record ConfigFieldSpec(ConfigFieldId id, int min, int max, int maxLength) {
    static ConfigFieldSpec of(ConfigFieldId id) {
        return new ConfigFieldSpec(id, Integer.MIN_VALUE, Integer.MAX_VALUE, 256);
    }

    static ConfigFieldSpec intField(ConfigFieldId id, int min, int max) {
        return new ConfigFieldSpec(id, min, max, 32);
    }

    static ConfigFieldSpec textField(ConfigFieldId id, int maxLength) {
        return new ConfigFieldSpec(id, Integer.MIN_VALUE, Integer.MAX_VALUE, maxLength);
    }

    static ConfigFieldSpec colorField(ConfigFieldId id) {
        return new ConfigFieldSpec(id, Integer.MIN_VALUE, Integer.MAX_VALUE, 11);
    }
}

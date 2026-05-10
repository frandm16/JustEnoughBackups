package com.frandm.justenoughbackups.client.screen.config;

import net.minecraft.network.chat.Component;

public record ValidationError(ConfigFieldId fieldId, Component message) {
}

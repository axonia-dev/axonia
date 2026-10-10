package dev.axonia.community.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dev.axonia.communication.api.ChatPlatformName;
import dev.axonia.community.domain.ChatDestinationLimits;

public record CreateChatDestinationRequest(@NotBlank @Size(max = ChatDestinationLimits.NAME_MAX) String name,
        @NotNull ChatPlatformName platform,
        @NotBlank @Size(max = ChatDestinationLimits.WEBHOOK_URL_MAX) String webhookUrl) {
}

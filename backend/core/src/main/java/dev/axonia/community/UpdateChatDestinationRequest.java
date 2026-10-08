package dev.axonia.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dev.axonia.communication.ChatPlatformName;

/**
 * {@code platform} is required only alongside a {@code webhookUrl}: an edit that merely renames sends neither, and a
 * destination whose stored URL no longer resolves to a platform would otherwise be unrenameable until its owner also
 * replaced the URL.
 */
public record UpdateChatDestinationRequest(@NotBlank @Size(max = ChatDestinationLimits.NAME_MAX) String name,
        ChatPlatformName platform, @Size(max = ChatDestinationLimits.WEBHOOK_URL_MAX) String webhookUrl) {
}

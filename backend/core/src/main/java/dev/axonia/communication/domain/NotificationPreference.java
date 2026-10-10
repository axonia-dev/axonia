package dev.axonia.communication.domain;

import dev.axonia.communication.api.MessageType;

/** One message type's delivery preference for the current user. An absent stored row still surfaces here as enabled. */
public record NotificationPreference(MessageType type, boolean enabled) {
}

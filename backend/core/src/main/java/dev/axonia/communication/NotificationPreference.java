package dev.axonia.communication;

/** One message type's delivery preference for the current user. An absent stored row still surfaces here as enabled. */
public record NotificationPreference(MessageType type, boolean enabled) {
}

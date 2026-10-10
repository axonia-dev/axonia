package dev.axonia.communication.domain;

/**
 * How one delivery leaves: to a person's inbox, into a community's chat room, or nowhere at all - an {@link #IN_APP}
 * row never leaves the product, and "sending" it is only the mark that makes it visible in that member's own panel.
 */
public enum DeliveryChannel {
    EMAIL, CHAT, IN_APP;

    /** Named positively, as the outbox's claim reads mail; a row with no channel is mail, as the column defaults. */
    public static boolean isMail(String channel) {

        return channel == null || EMAIL.name().equals(channel);
    }
}

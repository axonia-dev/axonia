package dev.axonia.events.internal;

/**
 * Shared between {@link dev.axonia.events.TopicService} and {@link dev.axonia.events.ActionItemService}: both
 * description columns are declared {@code NOT NULL}, so a caller that omits one gets the empty string rather than a
 * null.
 */
public final class Descriptions {

    private Descriptions() {
    }

    public static String orEmpty(String value) {

        return value == null ? "" : value;
    }
}

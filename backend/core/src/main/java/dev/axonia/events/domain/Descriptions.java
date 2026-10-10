package dev.axonia.events.domain;

/**
 * Shared between {@link dev.axonia.events.application.TopicService} and
 * {@link dev.axonia.events.application.ActionItemService}: both description columns are declared {@code NOT NULL}, so a
 * caller that omits one gets the empty string rather than a null.
 */
public final class Descriptions {

    private Descriptions() {
    }

    public static String orEmpty(String value) {

        return value == null ? "" : value;
    }
}

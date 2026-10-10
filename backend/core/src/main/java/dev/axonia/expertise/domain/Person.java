package dev.axonia.expertise.domain;

public record Person(String userId, String displayName, String avatarUrl, String orgUnit) {

    /** Matches identity's label for a departed member, so a person the directory cannot name reads the same here. */
    static final String UNRESOLVED_DISPLAY_NAME = "Deleted member";

    public static Person unresolved(String userId) {

        return new Person(userId, UNRESOLVED_DISPLAY_NAME, null, null);
    }
}

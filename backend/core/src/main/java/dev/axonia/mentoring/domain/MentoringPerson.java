package dev.axonia.mentoring.domain;

/** A member as the directory names them; {@code givenName} is null when the directory read none. */
public record MentoringPerson(String userId, String displayName, String givenName, String avatarUrl) {

    /** Matches identity's label for a departed member. */
    public static final String DELETED_MEMBER = "Deleted member";

    /** The name the person is greeted by: the given name, else the whole display name. */
    public String shortName() {

        return givenName == null ? displayName : givenName;
    }

    /** A member the directory no longer names; an erased side has no id at all. */
    public static MentoringPerson deleted(String userId) {

        return new MentoringPerson(userId, DELETED_MEMBER, null, null);
    }
}

package dev.axonia.mentoring.domain;

/** A member as the directory names them. */
public record MentoringPerson(String userId, String displayName, String avatarUrl) {

    /** Matches identity's label for a departed member. */
    public static final String DELETED_MEMBER = "Deleted member";

    /** A member the directory no longer names; an erased side has no id at all. */
    public static MentoringPerson deleted(String userId) {

        return new MentoringPerson(userId, DELETED_MEMBER, null);
    }
}

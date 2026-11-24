package dev.axonia.mentoring.api;

/** A mentor listed among the community's mentors; {@code status} is MENTORING_NOW or MENTORED_HERE. */
public record MentorFace(String userId, String displayName, String avatarUrl, String status) {
}

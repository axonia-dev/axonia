package dev.axonia.mentoring.api;

import java.util.List;

import lombok.Builder;

/**
 * The guild's mentoring page. {@code enabled} and {@code member} are true in every page that is returned: a
 * switched-off guild or a non-member is refused instead, so nothing should branch on them. {@code canMentorOn} and
 * {@code wantsToGrowIn} are what the caller brings through each door here: the areas they are open to questions on, and
 * those they want to learn. {@code guidedStart} is true while both of the caller's switches are off and they have never
 * had a proposal or a pairing in this guild. {@code pairings} are the guild's ongoing, shown pairings, whatever the
 * caller's own doors.
 */
@Builder
public record MentoringPage(boolean enabled, boolean member, boolean archived, Availability mine,
        List<DirectoryPerson> mentors, List<DirectoryPerson> mentees, List<String> canMentorOn,
        List<String> wantsToGrowIn, boolean guidedStart, List<ShownPairing> pairings) {
}

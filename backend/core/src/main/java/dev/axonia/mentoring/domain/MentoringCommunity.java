package dev.axonia.mentoring.domain;

import dev.axonia.shared.api.error.ConflictException;

/** A community resolved from its slug, with whether it is archived and whether mentoring is switched on. */
public record MentoringCommunity(long id, String slug, String name, boolean archived, boolean mentoringEnabled) {

    /** A proposal is sent or accepted only while mentoring is on and the community is not archived. */
    public void requireTakingProposals() {

        if (!mentoringEnabled) {
            throw new ConflictException("MENTORING_OFF", "Mentoring is switched off in this community");
        }
        if (archived) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "An archived community takes no new proposals");
        }
    }
}

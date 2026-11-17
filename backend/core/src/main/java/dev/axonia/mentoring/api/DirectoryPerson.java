package dev.axonia.mentoring.api;

import java.time.LocalDate;
import java.util.List;

/**
 * A member listed on the mentoring page; {@code proposalState} is null, PROPOSAL_SENT, PROPOSAL_RECEIVED,
 * DECLINED_RECENTLY or PAIRED. {@code proposalsOpenOn} is, with DECLINED_RECENTLY only, the viewer's day from which
 * they may propose to this person again.
 */
public record DirectoryPerson(String userId, String displayName, String avatarUrl, List<String> areas,
        boolean proposable, String proposalState, LocalDate proposalsOpenOn) {
}

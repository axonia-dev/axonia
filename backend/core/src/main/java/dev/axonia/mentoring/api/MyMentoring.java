package dev.axonia.mentoring.api;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/**
 * A member's mentoring across every guild: proposals sent and received, ongoing pairings and ended ones. {@code today}
 * is the member's calendar day in their own zone.
 */
@Builder
public record MyMentoring(LocalDate today, boolean inAMentoringGuild, List<ProposalView> received,
        List<ProposalView> sent, List<PairingSummary> ongoing, List<PairingSummary> ended) {
}

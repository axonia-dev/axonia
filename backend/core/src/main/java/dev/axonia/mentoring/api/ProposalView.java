package dev.axonia.mentoring.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/**
 * A proposal as one of its two members reads it: the partner is the other one. {@code proposedBy} is MENTOR or MENTEE,
 * and {@code status} is OPEN, ACCEPTED, DECLINED, WITHDRAWN or LAPSED. {@code reopenableUntil} is, for the person who
 * declined it, when the chance to reopen it ends; null otherwise. {@code lapsesOn} is, while it is OPEN, the caller's
 * day on which it lapses; null otherwise. {@code pairsShown} says whether accepting it pairs shown: false for a
 * proposal sent before the community could see pairings, whose pairing then asks both people first.
 */
@Builder(toBuilder = true)
public record ProposalView(long id, String communitySlug, String communityName, String proposedBy, String partnerId,
        String partnerName, String partnerAvatarUrl, List<String> topics, String message, String status,
        Instant createdAt, Instant reopenableUntil, LocalDate lapsesOn, boolean pairsShown) {
}

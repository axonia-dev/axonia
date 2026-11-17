package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

/**
 * An invitation between two members; {@code id} is null until stored. {@code reopenedAt} is the last time the person
 * who declined it opened it again, null if never.
 */
@Builder(toBuilder = true)
public record Proposal(Long id, long communityId, String mentorUserId, String menteeUserId, Role proposedBy,
        List<String> topics, String message, ProposalStatus status, Instant createdAt, Instant answeredAt,
        Instant reopenedAt) {

    public String senderId() {

        return proposedBy == Role.MENTOR ? mentorUserId : menteeUserId;
    }

    public String receiverId() {

        return proposedBy == Role.MENTOR ? menteeUserId : mentorUserId;
    }
}

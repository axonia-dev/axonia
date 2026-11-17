package dev.axonia.mentoring.domain;

import java.time.Instant;

import lombok.Builder;

/** What a member offers or seeks in one community; {@code menteeLimit} is null for no limit. */
@Builder
public record Availability(long communityId, String userId, boolean takingMentees, Integer menteeLimit,
        boolean lookingForMentor, Instant updatedAt) {

    /** Whether a mentor with this many ongoing pairings as mentor still has room for one more mentee. */
    public boolean hasRoom(int ongoingAsMentor) {

        return menteeLimit == null || ongoingAsMentor < menteeLimit;
    }
}

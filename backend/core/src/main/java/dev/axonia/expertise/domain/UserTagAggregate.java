package dev.axonia.expertise.domain;

import java.time.Instant;

/** Signal totals per kind for one person in one area, plus the most recent occurrence. */
public record UserTagAggregate(String userId, String tag, int presented, int contributed, int offered,
        Instant lastOccurredAt) {
}

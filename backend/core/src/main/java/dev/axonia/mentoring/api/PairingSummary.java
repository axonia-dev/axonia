package dev.axonia.mentoring.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/**
 * A pairing as one of its two members reads it: {@code myRole} is MENTOR or MENTEE, the partner is the other one, and
 * {@code partnerGivenName} is the partner's given name for a greeting, null when the directory read none. {@code stops}
 * are its topics' states along the trail, Done first, then IN_PROGRESS, then TODO, each in its board order; empty when
 * it has no topic. {@code nowTopic} is the first In progress topic's label along the trail, and {@code nextMeeting} is
 * null when unset or once the pairing ended. {@code meetings} counts the distinct meeting days among its notes,
 * {@code lastMetOn} is the latest of them.
 */
@Builder
public record PairingSummary(long id, String communitySlug, String communityName, String myRole, String partnerId,
        String partnerName, String partnerGivenName, String partnerAvatarUrl, Instant startedAt, Instant endedAt,
        List<String> stops, String nowTopic, NextMeetingDay nextMeeting, int meetings, int notes, LocalDate lastMetOn) {
}

package dev.axonia.mentoring.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/**
 * {@code mine} is whether the caller wrote it, which is who may change or remove it; {@code meetingOn} is the day of
 * the meeting it is about and {@code topicIds} the topics it names.
 */
@Builder
public record NoteView(long id, String authorId, String authorName, String authorAvatarUrl, boolean mine, String body,
        LocalDate meetingOn, List<Long> topicIds, Instant writtenAt, Instant editedAt, List<PointerView> pointers) {
}

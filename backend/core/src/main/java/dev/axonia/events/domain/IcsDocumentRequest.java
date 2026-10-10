package dev.axonia.events.domain;

import java.time.Instant;
import java.util.List;

import dev.axonia.events.api.ScheduleEntry;
import lombok.Builder;

/**
 * Everything one rendered calendar document needs. A parameter record rather than five arguments, per this repository's
 * five-parameter ceiling.
 */
@Builder
public record IcsDocumentRequest(String calendarName, String host, String sessionBaseUrl, Instant stamp,
        List<ScheduleEntry> entries) {

    public IcsDocumentRequest {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}

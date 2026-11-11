package dev.axonia.events.domain;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class FeedbackImportRecord {

    private Long id;
    private String organizationId;
    private Long sessionId;
    private int scale;
    private String source;
    private List<ImportedAnswer> answers;
    private Instant createdAt;
}

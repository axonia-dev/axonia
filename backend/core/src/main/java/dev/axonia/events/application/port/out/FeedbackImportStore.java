package dev.axonia.events.application.port.out;

import java.util.Optional;

import dev.axonia.events.domain.FeedbackImportRecord;
import dev.axonia.events.domain.FeedbackTally;

public interface FeedbackImportStore {

    Optional<FeedbackImportRecord> find(String organizationId, long sessionId);

    /** Drops the session's import, then stores this one unless it holds no answers. */
    void replace(FeedbackImportRecord feedbackImport);

    /** Imported values converted to the ten-point range. */
    FeedbackTally tally(String organizationId, long sessionId);
}

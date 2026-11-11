package dev.axonia.events.application.port.out;

import java.util.Optional;

import dev.axonia.events.domain.FeedbackRecord;
import dev.axonia.events.domain.FeedbackTally;

public interface SessionFeedbackStore {

    Optional<FeedbackRecord> findMine(String organizationId, long sessionId, String userId);

    FeedbackRecord save(FeedbackRecord feedback);

    FeedbackTally tally(String organizationId, long sessionId);

    boolean anyForSession(String organizationId, long sessionId);
}

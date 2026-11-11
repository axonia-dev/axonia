package dev.axonia.events.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.SessionFeedbackStore;
import dev.axonia.events.domain.FeedbackRecord;
import dev.axonia.events.domain.FeedbackTally;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcSessionFeedbackStore implements SessionFeedbackStore {

    private final SessionFeedbackRepository sessionFeedbackRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<FeedbackRecord> findMine(String organizationId, long sessionId, String userId) {

        return sessionFeedbackRepository.findByOrganizationIdAndSessionIdAndUserId(organizationId, sessionId, userId)
                .map(JdbcSessionFeedbackStore::toRecord);
    }

    @Override
    @Transactional
    public FeedbackRecord save(FeedbackRecord feedback) {

        return toRecord(sessionFeedbackRepository.save(toEntity(feedback)));
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackTally tally(String organizationId, long sessionId) {

        return sessionFeedbackRepository.tally(organizationId, sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean anyForSession(String organizationId, long sessionId) {

        return sessionFeedbackRepository.existsByOrganizationIdAndSessionId(organizationId, sessionId);
    }

    static FeedbackRecord toRecord(SessionFeedbackEntity entity) {

        return FeedbackRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .sessionId(entity.getSessionId()).userId(entity.getUserId()).enjoyed(entity.getEnjoyed())
                .learned(entity.getLearned()).createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion()).build();
    }

    static SessionFeedbackEntity toEntity(FeedbackRecord feedback) {

        return SessionFeedbackEntity.builder().id(feedback.getId()).organizationId(feedback.getOrganizationId())
                .sessionId(feedback.getSessionId()).userId(feedback.getUserId()).enjoyed(feedback.getEnjoyed())
                .learned(feedback.getLearned()).createdAt(feedback.getCreatedAt()).updatedAt(feedback.getUpdatedAt())
                .version(feedback.getVersion()).build();
    }
}

package dev.axonia.events.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dev.axonia.events.domain.FeedbackTally;

public interface SessionFeedbackRepository extends CrudRepository<SessionFeedbackEntity, Long> {

    Optional<SessionFeedbackEntity> findByOrganizationIdAndSessionIdAndUserId(String organizationId, Long sessionId,
            String userId);

    boolean existsByOrganizationIdAndSessionId(String organizationId, Long sessionId);

    @Query("SELECT count(*) AS answers, count(*) AS enjoyed_answers, "
            + "coalesce(sum(enjoyed), 0)::float8 AS enjoyed_sum, count(*) AS learned_answers, "
            + "coalesce(sum(learned), 0)::float8 AS learned_sum FROM session_feedback "
            + "WHERE organization_id = :organizationId AND session_id = :sessionId")
    FeedbackTally tally(@Param("organizationId") String organizationId, @Param("sessionId") Long sessionId);
}

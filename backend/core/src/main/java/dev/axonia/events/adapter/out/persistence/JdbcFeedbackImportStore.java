package dev.axonia.events.adapter.out.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.FeedbackImportStore;
import dev.axonia.events.domain.FeedbackImportRecord;
import dev.axonia.events.domain.FeedbackRules;
import dev.axonia.events.domain.FeedbackTally;
import dev.axonia.events.domain.ImportedAnswer;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcFeedbackImportStore implements FeedbackImportStore {

    private static final String OF_SESSION = " WHERE organization_id = :org AND session_id = :session";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public Optional<FeedbackImportRecord> find(String organizationId, long sessionId) {

        return jdbc
                .query("SELECT id, organization_id, session_id, scale, source, created_at "
                        + "FROM session_feedback_import" + OF_SESSION, sessionParams(organizationId, sessionId),
                        (rs, row) -> FeedbackImportRecord.builder().id(rs.getLong("id"))
                                .organizationId(rs.getString("organization_id")).sessionId(rs.getLong("session_id"))
                                .scale(rs.getInt("scale")).source(rs.getString("source"))
                                .createdAt(rs.getTimestamp("created_at").toInstant()))
                .stream().findFirst().map(header -> {
                    var stored = header.build();
                    return header.answers(answersOf(stored.getId())).build();
                });
    }

    @Override
    @Transactional
    public void replace(FeedbackImportRecord feedbackImport) {

        var params = sessionParams(feedbackImport.getOrganizationId(), feedbackImport.getSessionId());
        jdbc.update("DELETE FROM session_feedback_import" + OF_SESSION, params);
        if (feedbackImport.getAnswers().isEmpty()) {
            return;
        }
        var keys = new GeneratedKeyHolder();
        jdbc.update(
                "INSERT INTO session_feedback_import (organization_id, session_id, scale, source, created_at) "
                        + "VALUES (:org, :session, :scale, :source, :createdAt)",
                params.addValue("scale", feedbackImport.getScale()).addValue("source", feedbackImport.getSource())
                        .addValue("createdAt", Timestamp.from(feedbackImport.getCreatedAt())),
                keys, new String[]{"id"});
        var importId = keys.getKeyAs(Long.class);
        var rows = feedbackImport.getAnswers().stream()
                .map(answer -> new MapSqlParameterSource().addValue("org", feedbackImport.getOrganizationId())
                        .addValue("importId", importId).addValue("enjoyed", answer.enjoyed())
                        .addValue("learned", answer.learned()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO session_feedback_import_answer (organization_id, import_id, enjoyed, learned) "
                + "VALUES (:org, :importId, :enjoyed, :learned)", rows);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackTally tally(String organizationId, long sessionId) {

        return find(organizationId, sessionId).map(JdbcFeedbackImportStore::tallyOf)
                .orElseGet(() -> new FeedbackTally(0, 0, 0, 0, 0));
    }

    private List<ImportedAnswer> answersOf(Long importId) {

        return jdbc.query(
                "SELECT enjoyed, learned FROM session_feedback_import_answer WHERE import_id = :id ORDER BY id",
                Map.of("id", importId), (rs, row) -> new ImportedAnswer(rs.getObject("enjoyed", Integer.class),
                        rs.getObject("learned", Integer.class)));
    }

    private static FeedbackTally tallyOf(FeedbackImportRecord stored) {

        var enjoyed = stored.getAnswers().stream().map(ImportedAnswer::enjoyed).filter(Objects::nonNull)
                .mapToDouble(v -> FeedbackRules.toTen(v, stored.getScale())).summaryStatistics();
        var learned = stored.getAnswers().stream().map(ImportedAnswer::learned).filter(Objects::nonNull)
                .mapToDouble(v -> FeedbackRules.toTen(v, stored.getScale())).summaryStatistics();
        return new FeedbackTally(stored.getAnswers().size(), enjoyed.getCount(), enjoyed.getSum(), learned.getCount(),
                learned.getSum());
    }

    private static MapSqlParameterSource sessionParams(String organizationId, long sessionId) {

        return new MapSqlParameterSource().addValue("org", organizationId).addValue("session", sessionId);
    }
}

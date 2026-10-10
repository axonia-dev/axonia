package dev.axonia.expertise.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.application.port.out.SignalProjection;
import dev.axonia.expertise.domain.SignalRow;
import dev.axonia.expertise.domain.SignalSource;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceType;
import lombok.RequiredArgsConstructor;

/**
 * Write-side projection writer for expertise signals.
 *
 * <p>
 * {@link #replaceSource} is the primary operation: it atomically swaps out all previously recorded signals for a given
 * (organization, sourceType, sourceId) with the new set derived from the current state of the source object (topic,
 * slot, or artifact). This delete-then-insert strategy keeps the projection fresh without requiring a merge algorithm.
 *
 * <p>
 * Each raw tag is normalised via {@link TagNormalizer} before insertion; blank results are dropped. Deduplication is
 * applied across the incoming rows so that the unique constraint
 * {@code (organization_id, source_type, source_id, user_id, tag)} is never violated.
 *
 * <p>
 * It also fronts the curation moves of members' hidden areas and dismissals ({@link TagPreferenceWriter}), which read
 * the signals and declarations as they stand and so must run before its own rewrites.
 */
@Component
@RequiredArgsConstructor
public class SignalWriter implements SignalProjection {

    private static final String DELETE_COLLIDING_SIGNAL_SQL = """
            DELETE FROM expertise_signal s
             WHERE s.organization_id = :org
               AND s.community_id = :communityId
               AND s.tag = :from
               AND EXISTS (SELECT 1 FROM expertise_signal o
                            WHERE o.organization_id = s.organization_id
                              AND o.source_type = s.source_type
                              AND o.source_id = s.source_id
                              AND o.user_id = s.user_id
                              AND o.tag = :to)
            """;

    private static final String RENAME_SIGNAL_SQL = """
            UPDATE expertise_signal
               SET tag = :to
             WHERE organization_id = :org AND community_id = :communityId AND tag = :from
            """;

    private static final String REMOVE_SIGNAL_SQL = """
            DELETE FROM expertise_signal
             WHERE organization_id = :org AND community_id = :communityId AND tag = :tag
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TagPreferenceWriter tagPreferenceWriter;

    @Override
    @Transactional
    public void carryOverSplit(String organizationId, long communityId, String variant, String canonical) {

        tagPreferenceWriter.splitHiddenArea(organizationId, communityId, variant, canonical);
        tagPreferenceWriter.carryDismissalsOnSplit(organizationId, communityId, variant, canonical);
    }

    @Override
    @Transactional
    public void carryHiddenArea(String organizationId, long communityId, String from, String to) {

        tagPreferenceWriter.renameHiddenArea(organizationId, communityId, from, to);
    }

    @Override
    @Transactional
    public void dropHiddenArea(String organizationId, long communityId, String tag) {

        tagPreferenceWriter.dropHiddenArea(organizationId, communityId, tag);
    }

    /**
     * The colliding row goes before the rename: uq_expertise_signal would otherwise reject the update for a person who
     * signalled both names on the same source.
     */
    @Override
    @Transactional
    public void renameTag(String organizationId, long communityId, String from, String to) {

        tagPreferenceWriter.renameDismissals(organizationId, communityId, from, to);

        var params = new MapSqlParameterSource("org", organizationId).addValue("communityId", communityId)
                .addValue("from", from).addValue("to", to);
        jdbc.update(DELETE_COLLIDING_SIGNAL_SQL, params);
        jdbc.update(RENAME_SIGNAL_SQL, params);
    }

    @Override
    @Transactional
    public void removeTag(String organizationId, long communityId, String tag) {

        tagPreferenceWriter.pruneDismissals(organizationId, communityId, tag);

        var params = new MapSqlParameterSource("org", organizationId).addValue("communityId", communityId)
                .addValue("tag", tag);
        jdbc.update(REMOVE_SIGNAL_SQL, params);
    }

    @Override
    @Transactional
    public void replaceSource(SignalSource source, List<SignalRow> rows) {

        deleteSource(source.organizationId(), source.sourceType(), source.sourceId());

        var deduped = dedupe(rows);
        if (deduped.isEmpty()) {
            return;
        }

        var sql = """
                INSERT INTO expertise_signal
                    (organization_id, community_id, user_id, tag, signal_type, source_type, source_id, occurred_at)
                VALUES
                    (:organizationId, :communityId, :userId, :tag, :signalType, :sourceType, :sourceId, :occurredAt)
                ON CONFLICT DO NOTHING
                """;

        var batchParams = deduped.stream()
                .map(row -> new MapSqlParameterSource().addValue("organizationId", source.organizationId())
                        .addValue("communityId", source.communityId()).addValue("userId", row.userId())
                        .addValue("tag", row.tag()).addValue("signalType", row.signalType().name())
                        .addValue("sourceType", source.sourceType().name()).addValue("sourceId", source.sourceId())
                        .addValue("occurredAt", Timestamp.from(row.occurredAt())))
                .toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate(sql, batchParams);
    }

    @Override
    @Transactional
    public void deleteSource(String organizationId, SourceType sourceType, Long sourceId) {

        var sql = """
                DELETE FROM expertise_signal
                WHERE organization_id = :organizationId
                  AND source_type     = :sourceType
                  AND source_id       = :sourceId
                """;
        jdbc.update(sql, new MapSqlParameterSource().addValue("organizationId", organizationId)
                .addValue("sourceType", sourceType.name()).addValue("sourceId", sourceId));
    }

    /**
     * Normalises raw tags, drops blanks, and deduplicates by {@code (userId, normalizedTag, signalType)} - the fields
     * that determine uniqueness per the table constraint.
     */
    private List<NormalizedRow> dedupe(List<SignalRow> rows) {

        // LinkedHashMap preserves first-seen order (deterministic batch order)
        var seen = new LinkedHashMap<String, NormalizedRow>();
        for (var row : rows) {
            var tag = TagNormalizer.normalize(row.rawTag());
            if (tag.isBlank()) {
                continue;
            }
            var key = row.userId() + '\0' + tag + '\0' + row.signalType().name();
            seen.putIfAbsent(key, new NormalizedRow(row.userId(), tag, row.signalType(), row.occurredAt()));
        }
        return List.copyOf(seen.values());
    }

    private record NormalizedRow(String userId, String tag, SignalType signalType, Instant occurredAt) {
    }
}

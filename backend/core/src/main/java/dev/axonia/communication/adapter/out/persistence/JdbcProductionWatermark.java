package dev.axonia.communication.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.ProductionWatermark;
import dev.axonia.communication.application.port.out.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * {@link ProductionWatermark} backed by {@code communication_production_watermark}. {@link #since} carries the
 * identical {@code organization_id = current_setting('app.current_org', true)} predicate {@link JdbcOutbox}'s reads
 * use, for the identical reason - defence in depth on top of the table's own {@code organization_isolation} RLS policy,
 * and read from the GUC rather than {@link OrganizationContext} so an unscoped caller's predicate matches nothing
 * rather than throwing. {@link #advanceTo} writes through {@link OrganizationContext} instead, the same split
 * {@link JdbcOutbox#enqueue} makes between its own read and write paths.
 *
 * <p>
 * {@link #advanceTo} is an upsert, not an insert-then-update: {@code ON CONFLICT (organization_id, producer_key) DO
 * UPDATE} is what lets the very first tick for a given producer create its row and every later tick simply overwrite
 * {@code produced_through}, without the caller ever needing to know whether a row already exists. Both timestamps bind
 * {@code now}, the caller's own {@link java.time.Clock}-derived instant, as a plain {@code :now} parameter - never
 * {@code now()} in SQL, and never SpEL, which breaks the {@code Instant} converter elsewhere in this codebase.
 *
 * <p>
 * {@code produced_through = GREATEST(produced_through, :now)}, not a bare overwrite: two concurrent sweep instances (a
 * redeploy's briefly-overlapping old and new instance, the same overlap {@link JdbcOutbox#claim}'s own javadoc
 * describes) can race this upsert, and without {@code GREATEST} the one that happens to commit second - not necessarily
 * the one with the later {@code now} - would win, moving the watermark backwards. A backwards watermark never loses
 * work (the next tick's wider window still catches everything, and the outbox's unique key makes the overlap a no-op),
 * but it is pure waste, and {@code GREATEST} removes it for the cost of one word.
 */
@Repository
@RequiredArgsConstructor
public class JdbcProductionWatermark implements ProductionWatermark {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;

    @Override
    public Instant since(String producerKey, Instant floor) {

        var stored = jdbc.query("""
                SELECT produced_through FROM communication_production_watermark
                 WHERE organization_id = current_setting('app.current_org', true)
                   AND producer_key = :key
                """, new MapSqlParameterSource("key", producerKey),
                (rs, rowNum) -> rs.getObject("produced_through", OffsetDateTime.class).toInstant());

        return stored.stream().findFirst().orElse(floor);
    }

    @Override
    @Transactional
    public void advanceTo(String producerKey, Instant now) {

        var organizationId = organizationContext.organizationId();
        var timestamp = Timestamp.from(now);

        jdbc.update("""
                INSERT INTO communication_production_watermark
                    (organization_id, producer_key, produced_through, updated_at, version)
                VALUES (:org, :key, :now, :now, 0)
                ON CONFLICT (organization_id, producer_key)
                DO UPDATE SET produced_through = GREATEST(communication_production_watermark.produced_through, :now),
                              updated_at = :now,
                              version = communication_production_watermark.version + 1
                """, new MapSqlParameterSource().addValue("org", organizationId).addValue("key", producerKey)
                .addValue("now", timestamp));
    }
}

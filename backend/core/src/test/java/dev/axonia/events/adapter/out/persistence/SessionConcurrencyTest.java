package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SessionConcurrencyTest extends CoreJdbcTestSupport {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long seriesId;
    private Long communityId;

    @BeforeEach
    void seed() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'GuildC', now(), now(), 0) ON CONFLICT DO NOTHING");
        communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default','cc-' || nextval('communities_id_seq'),'CC', k.id,'','ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' AND k.label='GuildC' RETURNING id",
                Long.class);
        seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', ?, 'S', 1, 'WEEK', DATE '2026-06-03', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                Long.class, communityId);
    }

    @AfterEach
    void cleanup() {

        jdbc.update("DELETE FROM community_session WHERE series_id = ?", seriesId);
        jdbc.update("DELETE FROM event_series WHERE id = ?", seriesId);
    }

    @Test
    void twoMaterializationsOfTheSameSlot_onlyOneRowCreated() throws Exception {

        // given
        var at = Instant.parse("2099-12-12T18:00:00Z");
        Callable<Boolean> create = () -> {
            try {
                sessionRepository.save(
                        SessionEntity.builder().organizationId("default").seriesId(seriesId).communityId(communityId)
                                .status("SCHEDULED").scheduledAt(at).occurrenceDate(LocalDate.of(2099, 12, 12))
                                .notes("").recordingUrl("").sections(List.of()).build());
                return true;
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                return false;
            }
        };

        // when
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = pool.invokeAll(List.of(create, create));
        pool.shutdown();

        // then
        long winners = results.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return false;
            }
        }).count();
        assertThat(winners).isEqualTo(1L);
    }
}

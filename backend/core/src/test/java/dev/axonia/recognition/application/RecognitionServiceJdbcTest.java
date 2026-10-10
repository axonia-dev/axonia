package dev.axonia.recognition.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.recognition.adapter.out.persistence.JdbcLoveStore;
import dev.axonia.recognition.adapter.out.persistence.JdbcRecognitionStore;
import dev.axonia.recognition.api.ContributionSource;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.spi.ArtifactSourceAccess;
import dev.axonia.recognition.api.spi.ArtifactTypeAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;
import dev.axonia.recognition.application.port.out.CommunityAccess;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.OrganizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * The first love of a contribution racing another member's first love, each in its own real transaction against
 * Postgres. Rows are committed, so the test is not transactional and deletes what it wrote.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RecognitionService.class, JdbcRecognitionStore.class, JdbcLoveStore.class})
class RecognitionServiceJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final String LOVER = "racing-lover";
    private static final long COMMUNITY = 860_001L;
    private static final long ARTIFACT = 860_002L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-06-03T10:00:00Z");

    @Autowired
    private RecognitionService sut;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CommunityAccess communityAccess;

    @MockitoBean
    private SlotSourceAccess slotSourceAccess;

    @MockitoBean
    private ArtifactSourceAccess artifactSourceAccess;

    @MockitoBean
    private ArtifactTypeAccess artifactTypeAccess;

    @MockitoBean
    private CurrentUser currentUser;

    @MockitoBean
    private OrganizationContext organizationContext;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(LOVER);
        when(communityAccess.isMember(COMMUNITY, LOVER)).thenReturn(true);
        when(artifactSourceAccess.artifactSource(ORG, ARTIFACT))
                .thenReturn(Optional.of(ContributionSource.builder().communityId(COMMUNITY)
                        .contributorUserIds(List.of("author")).title("Runbook").occurredAt(OCCURRED_AT).build()));
    }

    @AfterEach
    void deleteRecognition() {

        jdbc.update("DELETE FROM recognized_contribution WHERE organization_id = :org AND contribution_id = :id",
                Map.of("org", ORG, "id", ARTIFACT));
    }

    @Test
    void aFirstLoveThatLosesTheRaceToCreateTheRecognitionLandsOnTheWinnersRow() throws Exception {

        // given
        var rivalInserted = new CountDownLatch(1);
        var rivalMayCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var rival = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.update("""
                        INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id,
                            community_id, title, occurred_at, created_at, updated_at, version)
                        VALUES (:org, :type, :id, :community, 'Runbook', now(), now(), now(), 0)
                        """, Map.of("org", ORG, "type", ContributionType.ARTIFACT_CAPTURED.name(), "id", ARTIFACT,
                        "community", COMMUNITY));
                rivalInserted.countDown();
                waitFor(rivalMayCommit);
                return null;
            }));
            waitFor(rivalInserted);

            // when
            var love = executor.submit(() -> sut.love(new LoveRequest(ContributionType.ARTIFACT_CAPTURED, ARTIFACT)));
            await().atMost(Duration.ofSeconds(10)).until(this::anInsertIsWaitingOnALock);
            rivalMayCommit.countDown();
            rival.get(10, TimeUnit.SECONDS);
            var actual = love.get(10, TimeUnit.SECONDS);

            // then
            assertThat(actual.loverUserId()).isEqualTo(LOVER);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM contribution_love l
                      JOIN recognized_contribution rc ON rc.id = l.recognized_contribution_id
                     WHERE rc.organization_id = :org AND rc.contribution_id = :id
                    """, Map.of("org", ORG, "id", ARTIFACT), Integer.class)).isEqualTo(1);
        }
    }

    private boolean anInsertIsWaitingOnALock() {

        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                                WHERE wait_event_type = 'Lock' AND query ILIKE 'INSERT INTO%recognized_contribution%')
                """, Map.of(), Boolean.class));
    }

    private static void waitFor(CountDownLatch latch) {

        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

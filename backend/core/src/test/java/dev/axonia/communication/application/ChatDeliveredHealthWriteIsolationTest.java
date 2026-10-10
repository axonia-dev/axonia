package dev.axonia.communication.application;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.communication.adapter.out.chat.ChatTransport;
import dev.axonia.communication.api.MessageType;
import dev.axonia.community.adapter.out.persistence.ChatDestinationEntity;
import dev.axonia.community.adapter.out.persistence.ChatDestinationRepository;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves, against real Spring transactional advice rather than a mock standing in for the seam, the exact reasoning
 * {@link OutboxRowProcessor#sendToDestination}'s own javadoc gives for why the delivered-chat health write does not
 * live there: {@code ChatHealthRecorder.recordDelivered} is itself {@code @Transactional}, and when it is reached from
 * inside {@link OutboxRowProcessor#process}'s own {@code REQUIRED} boundary it becomes a PARTICIPATING transaction -
 * Spring marks the whole ambient transaction rollback-only the instant that inner call throws, a mark a caught
 * exception one frame up does not undo.
 *
 * <p>
 * {@code ChatHealthRecorder} itself is deliberately left a REAL bean here, unlike a fully-mocked
 * {@code ChatDestinationsPort} (as {@code OutboxRowProcessorChatTest} and {@code DrainWorkersTest} both use, correctly,
 * for their own unit-level contracts): a Mockito mock is never wrapped in Spring's {@code TransactionInterceptor}, so a
 * test that replaces {@code ChatHealthRecorder} itself would never actually exercise the participating-transaction
 * mechanism this class exists to prove holds - it would pass identically whether the write sat inside or outside
 * {@code process()}'s transaction, exactly the blind spot the original, wrongly-placed guard's own test shipped with.
 * Only {@link ChatDestinationRepository} - {@code ChatHealthRecorder}'s dependency, one layer further down - is
 * replaced, so {@code ChatHealthRecorder.recordDelivered} keeps its own genuine {@code @Transactional} proxy and a
 * failure from underneath it is a real one, seen by real Spring transactional advice.
 *
 * <p>
 * Mocking the repository rather than seeding through it also means the destination itself is seeded by raw JDBC, the
 * same shape {@code ChatDestinationRepositoryTest.seedCommunity} and this package's other isolation tests already use -
 * {@code @Autowired ChatDestinationRepository} in this class would resolve to the very mock being stubbed, not a real
 * connection.
 *
 * <p>
 * Goes in through {@link SweepUnit#drain}, the same seam {@code OutboxDrainTransactionIsolationTest} uses and for the
 * identical reason given there: calling {@link OutboxDrain#drain} directly would skip past the transactional wiring
 * this class exists to prove holds.
 *
 * <p>
 * <b>Verified by mutation</b>, the same discipline {@code OutboxDrainTransactionIsolationTest} holds itself to:
 * temporarily moving the {@code recordDelivered} call back inside {@link OutboxRowProcessor#sendToDestination}'s own
 * guarded {@code try}/{@code catch} turns {@link #aThrowingHealthWriteDoesNotRollBackTheRowsOwnCommit} red: the row
 * reads {@code FAILED}, not {@code SENT}, because the ambient transaction is marked rollback-only before that local
 * catch ever runs. Restoring the current shape - the write made from {@link DrainWorkers} instead, after
 * {@code process()}'s transaction has already committed - turns it green again. That perturbation is not committed, in
 * keeping with the same convention.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ChatDeliveredHealthWriteIsolationTest {

    private static final String ORGANIZATION_ID = "org-chat-delivered-isolation";
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String URL = "https://hooks.slack.com/services/x";

    @Autowired
    private SweepUnit unit;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private DeliveryGate gate;

    @MockitoBean
    private ChatTransport chatTransport;

    @MockitoBean
    private ChatDestinationRepository chatDestinationRepository;

    /**
     * Same precondition {@code OutboxDrainTransactionIsolationTest} guards with, for the same reason: {@code
     * cleanup.sql} runs {@code AFTER_TEST_CLASS}, so this catches a previous run's cleanup never having happened rather
     * than assuming a fresh organization id makes it impossible.
     */
    private void assertNoClaimableOutboxRows() {

        var claimable = jdbc.queryForObject(
                "SELECT count(*) FROM communication_delivery WHERE status IN ('PENDING', 'FAILED', 'SENDING') "
                        + "AND organization_id = :org",
                Map.of("org", ORGANIZATION_ID), Long.class);
        assertThat(claimable).isZero();
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    /**
     * Raw JDBC, not {@link ChatDestinationRepository#save}: that repository is the very bean this class mocks, so going
     * through it here would insert into the mock rather than the database. The mocked repository is then stubbed to
     * answer {@code findByOrganizationIdAndId} with an in-memory entity of the same shape - what
     * {@code ChatDestinationService.statusFor} (real, reached the same way as the health write) needs to read
     * {@code Ready} - and to fail every {@code save}, which is the one call {@code ChatHealthRecorder.recordDelivered}
     * makes.
     */
    private long seedDestination() {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Guild', now(), now(), 0) RETURNING id",
                Map.of("org", ORGANIZATION_ID), Long.class);
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, 'chat-isolation-guild', 'Chat Isolation Guild', :kindId, "
                        + "'', 'ACTIVE', now(), now(), 0) RETURNING id",
                Map.of("org", ORGANIZATION_ID, "kindId", kindId), Long.class);
        var destinationId = jdbc.queryForObject("""
                INSERT INTO community_chat_destination
                    (organization_id, community_id, name, webhook_url, broken, consecutive_failures,
                     created_at, updated_at, version)
                VALUES (:org, :communityId, '#chat-isolation', :url, false, 0, :now, :now, 0)
                RETURNING id
                """, Map.of("org", ORGANIZATION_ID, "communityId", communityId, "url", URL, "now", Timestamp.from(NOW)),
                Long.class);

        var entity = ChatDestinationEntity.builder().id(destinationId).organizationId(ORGANIZATION_ID)
                .communityId(communityId).name("#chat-isolation").webhookUrl(URL).broken(false).build();
        when(chatDestinationRepository.findByOrganizationIdAndId(ORGANIZATION_ID, destinationId))
                .thenReturn(Optional.of(entity));
        when(chatDestinationRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("simulated database failure"));

        return destinationId;
    }

    /**
     * Already expanded - {@code chat_destination_id} set directly - so the row drives
     * {@link OutboxRowProcessor#sendToDestination} rather than {@link OutboxRowProcessor#expand}, which is not what
     * this class tests.
     */
    private long seedChatRow(long destinationId) {

        var params = new MapSqlParameterSource().addValue("org", ORGANIZATION_ID)
                .addValue("type", MessageType.SESSION_WRAP_UP.code()).addValue("subject", "chat-delivered-isolation")
                .addValue("now", Timestamp.from(NOW));
        var messageId = jdbc.queryForObject("""
                INSERT INTO communication_message
                    (organization_id, message_type, subject_ref, template, facts, created_at, updated_at, version)
                VALUES (:org, :type, :subject, 'session-wrap-up', '{}'::jsonb, :now, :now, 0)
                RETURNING id
                """, params, Long.class);

        return jdbc.queryForObject("""
                INSERT INTO communication_delivery
                    (organization_id, message_id, channel, chat_destination_id, status, created_at, updated_at, version)
                VALUES (:org, :message, 'CHAT', :destinationId, 'PENDING', :now, :now, 0)
                RETURNING id
                """, params.addValue("message", messageId).addValue("destinationId", destinationId), Long.class);
    }

    private String statusOf(long deliveryId) {

        return jdbc.queryForObject("SELECT status FROM communication_delivery WHERE id = :id", Map.of("id", deliveryId),
                String.class);
    }

    /**
     * The claim this whole class exists to settle: {@code ChatHealthRecorder.recordDelivered} genuinely throwing - here
     * because its own repository dependency does, the same real {@code DataIntegrityViolationException} shape an
     * {@code OptimisticLockingFailureException} from two concurrent deliveries to one destination would also produce -
     * must not roll back the row's own {@code markSent}. See this class's own javadoc for what turns this test red.
     */
    @Test
    void aThrowingHealthWriteDoesNotRollBackTheRowsOwnCommit() {

        assertNoClaimableOutboxRows();
        var destinationId = seedDestination();
        var deliveryId = seedChatRow(destinationId);
        when(gate.admit(any())).thenReturn(Optional.empty());

        OrganizationScope.runAs(ORGANIZATION_ID,
                () -> unit.drain(ORGANIZATION_ID, Instant.now().plus(Duration.ofMinutes(1))));

        assertThat(statusOf(deliveryId)).isEqualTo("SENT");
    }
}

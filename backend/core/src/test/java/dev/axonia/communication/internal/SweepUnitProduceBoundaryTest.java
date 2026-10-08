package dev.axonia.communication.internal;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.OrganizationResolver;
import dev.axonia.OrganizationScope;
import dev.axonia.communication.MessageProducer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The produce half of what {@link SweepUnitScopeBoundaryTest} pins for the drain half, and the higher-consequence one.
 * {@link SweepUnit#produce} must carry no {@code @Transactional} of its own (see its class javadoc): a transaction
 * opened here - below {@code OrganizationScope.runAs} but above each producer's own - would acquire the connection
 * while the scope is set, but every producer would then JOIN it rather than open its own, so nothing after this point
 * could ever be scoped differently and, worse, a producer reached from a caller that had not entered a scope would run
 * its whole read set on a connection whose {@code app.current_org} was never chosen. Under the {@code axonia_app} role
 * that is not an error: the row-level-secured tables answer empty lists and zeros, so a roundup would report a quarter
 * in which nothing happened, advance its watermark past it and go quiet for good.
 *
 * <p>
 * Nothing else in the suite catches that. {@code CommunicationSweepTest} proves the scope is open around
 * {@link SweepUnit#produce} but mocks the unit itself, so it cannot see what the real one opens; the unit test proves
 * the fan-out but constructs {@link SweepUnit} directly, where an added {@code @Transactional} is un-proxied and
 * therefore inert. Only the real bean, reached through the real container, can tell the difference - which is why this
 * is a {@code @SpringBootTest} rather than another unit test.
 *
 * <p>
 * It needs no RLS harness to do it. The two facts a producer needs are both readable from inside one:
 * {@link OrganizationResolver} answers the scope exactly as {@code RoundupProducer} reads it in production, and
 * {@link TransactionSynchronizationManager#isActualTransactionActive()} answers whether anything above it already
 * opened a transaction. {@link RecordingProducer} records both and asserts nothing itself, so a failure reads as the
 * state that was wrong rather than as an exception from inside a callback. Its fields start at the FAILING values - no
 * scope seen, a transaction active - so a producer the sweep never reaches at all fails this test rather than passing
 * it by default.
 *
 * <p>
 * Registering that producer as an extra bean is only possible because {@link SweepUnit} collects
 * {@link MessageProducer} as a list; it joins the real ones rather than replacing them, and they run against this
 * organization id too. That is deliberate - it is the production fan-out, not a stub of it - and it is why this class
 * carries the {@code cleanup.sql} hook: {@code UpcomingSessionProducer} advances its watermark unconditionally, which
 * commits a {@code communication_production_watermark} row for this organization on the shared container.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Import(SweepUnitProduceBoundaryTest.RecordingProducerConfig.class)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class SweepUnitProduceBoundaryTest {

    private static final String ORGANIZATION_ID = "org-produce-boundary";

    @Autowired
    private SweepUnit unit;

    @Autowired
    private RecordingProducer recordingProducer;

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void everyProducerIsReachedInsideTheScopeAndOutsideAnyTransactionOfTheSweepsOwn() {

        OrganizationScope.runAs(ORGANIZATION_ID, () -> unit.produce(ORGANIZATION_ID));

        assertThat(recordingProducer.scopeSeen).contains(ORGANIZATION_ID);
        assertThat(recordingProducer.transactionActive).isFalse();
    }

    @TestConfiguration
    static class RecordingProducerConfig {

        @Bean
        RecordingProducer recordingProducer(OrganizationResolver organizationResolver) {

            return new RecordingProducer(organizationResolver);
        }
    }

    static final class RecordingProducer implements MessageProducer {

        private final OrganizationResolver organizationResolver;

        private Optional<String> scopeSeen = Optional.empty();
        private boolean transactionActive = true;

        RecordingProducer(OrganizationResolver organizationResolver) {

            this.organizationResolver = organizationResolver;
        }

        @Override
        public void produce(Instant now) {

            scopeSeen = organizationResolver.currentOrganization();
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        }
    }
}

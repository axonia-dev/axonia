package dev.axonia.communication.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.OrganizationIds;
import dev.axonia.SweepHeartbeat;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code scope} is not one of this class's collaborators: {@link CommunicationSweep} wraps each organization's work in
 * the real {@code OrganizationScope.runAs}, a static utility (see its own javadoc on why the wrap must be around the
 * transactional entry point, never inside it), not through a mockable seam. So
 * {@link #theProduceScopeWrapsTheTransactionalUnitRatherThanSittingInsideIt} and
 * {@link #theDrainScopeWrapsTheTransactionalUnitRatherThanSittingInsideIt} prove the same thing a provisioner's test
 * already proves for a provisioner: by inspecting the real {@code SecurityContext} from inside the mocked
 * {@link SweepUnit#produce}/{@link SweepUnit#drain} call, rather than verifying a call order on a fabricated
 * collaborator that would not exist in production.
 *
 * <p>
 * The single {@code tick} method this class used to expose is split into {@link CommunicationSweep#produceTick} and
 * {@link CommunicationSweep#drainTick}, one per cadence; a later change added {@link CommunicationSweep#retentionTick}
 * beside them for the ninety-day pass. Every behaviour this class pins - sweeping every organization, one
 * organization's failure not aborting the rest, the scope wrap - applies identically to all three, so each scenario
 * below is asserted against all three methods rather than once against a single shared one.
 */
@ExtendWith(MockitoExtension.class)
class CommunicationSweepTest {

    @Mock
    private OrganizationIds organizationIds;
    @Mock
    private SweepUnit unit;
    @Captor
    private ArgumentCaptor<Instant> deadlines;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T04:00:00Z"), ZoneOffset.UTC);

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private CommunicationSweep sweep;

    @BeforeEach
    void buildTheSweep() {

        sweep = new CommunicationSweep(organizationIds, unit, new SweepHeartbeat(meters, CLOCK), CLOCK,
                CommunicationPropertiesMother.any());
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void everyOrganizationIsSweptInsideItsOwnScopeOnTheProduceTick() {

        when(organizationIds.all()).thenReturn(List.of("org-1", "org-2"));

        sweep.produceTick();

        verify(unit).produce("org-1");
        verify(unit).produce("org-2");
    }

    @Test
    void everyOrganizationIsSweptInsideItsOwnScopeOnTheDrainTick() {

        when(organizationIds.all()).thenReturn(List.of("org-1", "org-2"));

        sweep.drainTick();

        verify(unit).drain(eq("org-1"), any());
        verify(unit).drain(eq("org-2"), any());
    }

    /**
     * The deadline is one reading of the clock for the whole tick, not a fresh one per organization: both calls must
     * receive the identical {@link Instant}, not merely two instants that happen to be close together.
     */
    @Test
    void everyOrganizationInTheDrainTickSharesTheSameDeadline() {

        when(organizationIds.all()).thenReturn(List.of("org-1", "org-2"));

        sweep.drainTick();

        verify(unit, times(2)).drain(any(), deadlines.capture());
        assertThat(deadlines.getAllValues()).hasSize(2).containsOnly(deadlines.getAllValues().getFirst());
    }

    @Test
    void everyOrganizationIsSweptInsideItsOwnScopeOnTheRetentionTick() {

        when(organizationIds.all()).thenReturn(List.of("org-1", "org-2"));

        sweep.retentionTick();

        verify(unit).retain("org-1");
        verify(unit).retain("org-2");
    }

    @Test
    void oneOrganizationFailingDoesNotAbortTheProduceTick() {

        when(organizationIds.all()).thenReturn(List.of("org-1", "org-2"));
        doThrow(new IllegalStateException("boom")).when(unit).produce("org-1");

        sweep.produceTick();

        verify(unit).produce("org-2");
    }

    @Test
    void oneOrganizationFailingDoesNotAbortTheDrainTick() {

        when(organizationIds.all()).thenReturn(List.of("org-1", "org-2"));
        doThrow(new IllegalStateException("boom")).when(unit).drain(eq("org-1"), any());

        sweep.drainTick();

        verify(unit).drain(eq("org-2"), any());
    }

    @Test
    void oneOrganizationFailingDoesNotAbortTheRetentionTick() {

        when(organizationIds.all()).thenReturn(List.of("org-1", "org-2"));
        doThrow(new IllegalStateException("boom")).when(unit).retain("org-1");

        sweep.retentionTick();

        verify(unit).retain("org-2");
    }

    @Test
    void theProduceScopeWrapsTheTransactionalUnitRatherThanSittingInsideIt() {

        when(organizationIds.all()).thenReturn(List.of("org-1"));
        doAnswer(invocation -> {
            assertThat(currentScope()).isEqualTo("org-1");
            return null;
        }).when(unit).produce("org-1");

        sweep.produceTick();

        verify(unit).produce("org-1");
    }

    @Test
    void theDrainScopeWrapsTheTransactionalUnitRatherThanSittingInsideIt() {

        when(organizationIds.all()).thenReturn(List.of("org-1"));
        doAnswer(invocation -> {
            assertThat(currentScope()).isEqualTo("org-1");
            return null;
        }).when(unit).drain(eq("org-1"), any());

        sweep.drainTick();

        verify(unit).drain(eq("org-1"), any());
    }

    @Test
    void theRetentionScopeWrapsTheTransactionalUnitRatherThanSittingInsideIt() {

        when(organizationIds.all()).thenReturn(List.of("org-1"));
        doAnswer(invocation -> {
            assertThat(currentScope()).isEqualTo("org-1");
            return null;
        }).when(unit).retain("org-1");

        sweep.retentionTick();

        verify(unit).retain("org-1");
    }

    private static String currentScope() {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        return ((OrganizationBoundAuthentication) auth).organizationId();
    }

    /**
     * Which name each of the three ticks reports itself under, not merely that it reports. The three share one bean and
     * differ only in the constant they pass, so a copied-and-adjusted method that keeps its neighbour's name would go
     * entirely unnoticed: two ticks would refresh one deadman and the third would alert forever.
     */
    @Test
    void eachTickIsHeartbeatedUnderItsOwnSweepName() {

        when(organizationIds.all()).thenReturn(List.of());

        sweep.produceTick();
        sweep.drainTick();
        sweep.retentionTick();

        assertThat(successesOf("communication-produce")).isEqualTo(1.0);
        assertThat(successesOf("communication-drain")).isEqualTo(1.0);
        assertThat(successesOf("communication-retention")).isEqualTo(1.0);
    }

    private double successesOf(String sweep) {

        return meters.get("axonia.sweep.runs").tag("sweep", sweep).tag("outcome", "success").counter().count();
    }
}

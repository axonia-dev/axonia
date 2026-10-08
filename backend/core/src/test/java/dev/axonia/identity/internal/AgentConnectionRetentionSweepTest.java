package dev.axonia.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.OrganizationIds;
import dev.axonia.SweepHeartbeat;
import dev.axonia.identity.connection.AgentConnections;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The clustered scheduler never starts in a {@code @SpringBootTest} context, so nothing wired through Spring ever calls
 * {@link AgentConnectionRetentionSweep#tick}. This class is the only thing that does, mirroring
 * {@code SuccessionRetentionSweepTest}: a mocked {@link AgentConnections}, the real bean built by hand around it, and
 * the scope proven by reading the real {@code SecurityContext} from inside the mocked
 * {@link AgentConnections#sweep(String)} call rather than by verifying a call order on a fabricated collaborator.
 */
@ExtendWith(MockitoExtension.class)
class AgentConnectionRetentionSweepTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T04:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OrganizationIds organizationIds;

    @Mock
    private AgentConnections connections;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private AgentConnectionRetentionSweep sut;

    @BeforeEach
    void buildTheSweep() {

        sut = new AgentConnectionRetentionSweep(organizationIds, connections, new SweepHeartbeat(meters, CLOCK));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void theTickIsHeartbeatedUnderItsOwnSweepName() {

        when(organizationIds.all()).thenReturn(List.of("acme"));

        sut.tick();

        assertThat(successesOf("agent-connection-retention")).isEqualTo(1.0);
    }

    @Test
    void tick_sweepsEveryActiveOrganizationInsideItsOwnScope() {

        // given
        when(organizationIds.all()).thenReturn(List.of("acme", "default"));
        when(connections.sweep(anyString())).thenAnswer(invocation -> {
            assertThat(((OrganizationBoundAuthentication) SecurityContextHolder.getContext().getAuthentication())
                    .organizationId()).isEqualTo(invocation.getArgument(0));
            return 1;
        });

        // when
        sut.tick();

        // then
        verify(connections).sweep("acme");
        verify(connections).sweep("default");
    }

    @Test
    void tick_carriesOnPastOneOrganizationsFailure() {

        when(organizationIds.all()).thenReturn(List.of("acme", "default"));
        when(connections.sweep("acme")).thenThrow(new IllegalStateException("boom"));

        sut.tick();

        verify(connections).sweep("default");
    }

    /**
     * The authentication is captured into an {@link AtomicReference} inside the mocked call and asserted only after
     * {@link AgentConnectionRetentionSweep#tick} returns, for the reason {@code SuccessionRetentionSweepTest} states at
     * length: asserting inside the answer would let the very mutation being guarded against - deleting
     * {@code OrganizationScope.runAs} so the sweep ran unscoped - throw a {@code NullPointerException} that the tick's
     * per-organization {@code try/catch} swallows as just another failing unit, leaving the test green.
     */
    @Test
    void theScopeWrapsTheSweepRatherThanSittingInsideIt() {

        when(organizationIds.all()).thenReturn(List.of("acme"));
        var captured = new AtomicReference<Authentication>();
        when(connections.sweep("acme")).thenAnswer(invocation -> {
            captured.set(SecurityContextHolder.getContext().getAuthentication());
            return 0;
        });

        sut.tick();

        assertThat(captured.get()).isInstanceOfSatisfying(OrganizationBoundAuthentication.class,
                auth -> assertThat(auth.organizationId()).isEqualTo("acme"));
    }

    private double successesOf(String sweep) {

        return meters.get("axonia.sweep.runs").tag("sweep", sweep).tag("outcome", "success").counter().count();
    }
}

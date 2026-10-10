package dev.axonia.communication.adapter.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.communication.adapter.out.security.JwtOrganizationContext;
import dev.axonia.communication.api.OutboundMessageMother;
import dev.axonia.communication.api.spi.ChatDestinationsPort;
import dev.axonia.communication.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reproduces, without Spring or Testcontainers, the exact failure a producer's first real tick would hit: a
 * community-wide notice's {@code SessionProducers.produce -> ... -> CommunicationPort#enqueue} call runs inside the
 * communication sweep's {@code OrganizationScope.runAs}, which installs a {@code ProvisioningAuthentication} - not a
 * {@code JwtAuthenticationToken}. {@code communication}'s {@link OrganizationContext} used to resolve the organization
 * from a JWT only ({@link CurrentToken#organization()}), which throws {@link IllegalStateException} whenever the
 * {@code SecurityContext}'s authentication is not a JWT - exactly what
 * {@code events.adapter.out.communication.MessageSubjectPolicyAdapterRunsWithNoJwtTest} already proved for the delivery
 * gate's own path, and the same shape of regression test.
 *
 * <p>
 * This wires the REAL {@link JwtOrganizationContext} - not a mock - into the REAL {@link JdbcOutbox}, with only the
 * leaf {@code NamedParameterJdbcTemplate} mocked (no real database needed to prove organization resolution). A
 * regression that reverts {@link JwtOrganizationContext} back to JWT-only fails this test with the same exception
 * production would throw, rather than passing vacuously against a mock that never touches {@code OrganizationContext}
 * at all.
 */
class JdbcOutboxRunsWithNoJwtTest {

    private static final String ORGANIZATION_ID = "acme";

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    // No type is chat-eligible here - this fixture is about organization resolution, not chat.
    private final ChatDestinationsPort chatDestinationsPort = mock(ChatDestinationsPort.class);
    // The expiry guard now lives entirely inside upsertMessage's own SQL (see its javadoc), so this mock is never
    // called on the path this fixture exercises - kept only because the constructor requires it.
    private final ExpiredMessages expiredMessages = mock(ExpiredMessages.class);

    private final JdbcOutbox outbox = new JdbcOutbox(jdbc, new JwtOrganizationContext(), clock, jsonMapper,
            chatDestinationsPort, expiredMessages);

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    /**
     * {@code inserted} is 2, not 1, and {@code batchUpdate} is called twice, not once: {@code enqueue} now batches once
     * for the recipient's mail row and once more for their in-app row (see {@link JdbcOutboxInAppRowTest}), and the
     * mocked {@code jdbc} answers 1 (an actual insert) for each call. Both calls are captured - not just the last one
     * {@code ArgumentCaptor#getValue()} would return - and the captured SQL text is asserted to genuinely differ by
     * channel, so a regression that wrote {@code EMAIL} twice (or any other single channel twice) would fail here
     * rather than pass unchanged.
     */
    @Test
    void enqueueRunsInsideTheSweepWithNoJwtInTheSecurityContext() {

        // The message upsert's RETURNING id, which the delivery fan-out below needs before it can run at all -
        // upsertMessage now reads it back via jdbc.query(...RowMapper...), not queryForObject, since its guard clause
        // means an expired subject's INSERT ... SELECT can legitimately return no row at all.
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of(7L));
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class))).thenReturn(new int[]{1});
        when(chatDestinationsPort.channelTypes()).thenReturn(Set.of());
        var message = OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build();

        var inserted = OrganizationScope.runAs(ORGANIZATION_ID, () -> outbox.enqueue(message));

        assertThat(inserted).isEqualTo(2);
        var messageParams = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), messageParams.capture(), any(RowMapper.class));
        assertThat(messageParams.getValue().getValue("org")).isEqualTo(ORGANIZATION_ID);

        var deliverySql = ArgumentCaptor.forClass(String.class);
        var deliveryParams = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbc, times(2)).batchUpdate(deliverySql.capture(), deliveryParams.capture());
        assertThat(deliverySql.getAllValues()).hasSize(2).anyMatch(sql -> sql.contains("'EMAIL'"))
                .anyMatch(sql -> sql.contains("'IN_APP'"));
        assertThat(deliveryParams.getAllValues())
                .allSatisfy(params -> assertThat(params[0].getValue("org")).isEqualTo(ORGANIZATION_ID));
    }

    /**
     * Proves the fixture itself carries no JWT during the sweep, so the test above is not passing by accident: the
     * implicit-organization path genuinely throws in this exact {@code SecurityContext} state.
     */
    @Test
    void theFixtureCarriesNoJwtSoTheJwtOnlyPathStillThrows() {

        OrganizationScope.runAs(ORGANIZATION_ID,
                () -> assertThatThrownBy(CurrentToken::organization).isInstanceOf(IllegalStateException.class));
    }
}

package dev.axonia.communication.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.OrganizationIds;
import dev.axonia.TenantDiscoveryPool;
import dev.axonia.communication.MessageType;
import dev.axonia.config.TimeConfig;

/**
 * Shared @DataJdbcTest base for the communication persistence tests. A single cached Spring context (one Hikari pool)
 * for every test in this package, rather than one context per @Import set, is what keeps the shared Testcontainers
 * Postgres from running out of connections during the full suite. {@link JdbcOutbox}, {@link JwtOrganizationContext}
 * and {@link OrganizationIds} are plain {@code @Component}/{@code @Repository} beans, so the @DataJdbcTest slice does
 * not register them automatically; TimeConfig and JacksonAutoConfiguration are imported because {@link JdbcOutbox}
 * injects a {@code Clock} and a {@code JsonMapper}, neither of which the slice provides on its own.
 * {@link TenantDiscoveryPool} supplies {@link OrganizationIds}' own privileged datasource - see its javadoc for why it
 * is never wrapped in the primary, RLS-scoped one. {@link JdbcProductionWatermark} joins the same list for the
 * identical reason {@link JdbcOutbox} is here: a plain {@code @Repository}, not something the slice registers on its
 * own. {@link StubChatDestinationsPort} joins it for the same reason again: {@link JdbcOutbox} now also injects a
 * {@code ChatDestinationsPort}, and this package owns no real implementation of it - {@code events.internal} does.
 * {@link ExpiredMessages} joins the list once more for the same reason as {@link JdbcOutbox} itself, which now injects
 * it. {@link OutboxRetention} joins it for its own retention pass, the same plain {@code @Component} reason as every
 * other entry here. {@link JdbcNotificationCentre} and {@link NotificationEntryBuilders} join it for the panel's own
 * reads and writes, for the identical reason - a plain {@code @Repository}/{@code @Component} the slice does not
 * register on its own - and because a distinct {@code @Import} set per test class exhausts the shared Testcontainers
 * Postgres only in the full gate.
 */
@Import({JdbcOutbox.class, JwtOrganizationContext.class, TimeConfig.class, TenantDiscoveryPool.class,
        OrganizationIds.class, JdbcProductionWatermark.class, StubChatDestinationsPort.class, ExpiredMessages.class,
        OutboxRetention.class, JdbcNotificationCentre.class, NotificationEntryBuilders.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
abstract class CommunicationJdbcTestSupport extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private StubChatDestinationsPort chatDestinationsPort;

    /**
     * Resets the stub back to its default - no type posts to a channel - after every test. Necessary, not cosmetic:
     * {@link StubChatDestinationsPort} is a singleton in this class's cached context, so a test that opts a type in and
     * forgot to reset it back out would leak that into whichever test - in this class or another - happens to run next.
     */
    @AfterEach
    void resetChatDestinationsPort() {

        chatDestinationsPort.useChannelTypes(Set.of());
    }

    protected void whenChannelTypesAre(Set<MessageType> types) {

        chatDestinationsPort.useChannelTypes(types);
    }

    /**
     * Scoped to {@code acme} for the identical reason {@link JdbcOutboxTest#countRows} is: the shared {@code jdbc:tc}
     * container also hosts plain {@code @SpringBootTest} classes elsewhere in this package that commit real rows under
     * their own organization ids and never roll back, and this class's connection is the table owner, so RLS does not
     * hide them from an unscoped query.
     */
    protected List<String> deliveryChannels(String subjectRef) {

        return jdbc.queryForList("""
                SELECT d.channel
                  FROM communication_delivery d
                  JOIN communication_message m ON m.id = d.message_id
                 WHERE m.subject_ref = :subject
                   AND d.organization_id = 'acme'
                """, Map.of("subject", subjectRef), String.class);
    }
}

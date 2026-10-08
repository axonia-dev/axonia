package dev.axonia.roundup.internal;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.BeforeTransaction;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.JdbcTestSupport;
import dev.axonia.roundup.RoundupSettings;
import dev.axonia.roundup.RoundupSettingsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoundupSettingsService} against real Postgres rather than mocks, because what needs proving is the half a mock
 * cannot reach: that an assigned-String-key entity actually INSERTs the first time and UPDATEs afterwards, that
 * {@code roundup_settings}' NOT NULL audit columns are filled by {@code @CreatedDate}/{@code @LastModifiedDate}, and
 * that a missing row is genuinely off rather than an error.
 *
 * <p>
 * Runs as the Testcontainers superuser (table owner), which bypasses {@code roundup_settings}' row-level security by
 * ownership - the convention every {@code JdbcTestSupport} integration test here follows. The half that convention
 * cannot cover is in {@code RlsStackIsolationTest}, which connects as {@code axonia_app} and proves that one tenant's
 * stored flag is invisible to another. {@code authenticate()} is {@code @BeforeTransaction}, not {@code @BeforeEach}:
 * {@code @DataJdbcTest}'s test-managed transaction is opened before a class's own {@code @BeforeEach} methods run, and
 * {@code OrganizationAwareDataSource} reads the organization only once, when that transaction's connection is first
 * acquired.
 */
@Import({RoundupSettingsService.class, JwtOrganizationContext.class})
class RoundupSettingsServiceJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "acme";

    @Autowired
    private RoundupSettingsService service;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeTransaction
    void authenticate() {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void anOrganizationThatNeverTouchedTheSettingIsOff() {

        var settings = service.forCurrentOrganization();

        assertThat(settings.organizationId()).isEqualTo(ORG);
        assertThat(settings.enabled()).isFalse();
    }

    @Test
    void forCurrentOrganizationReadsTheStoredFlag() {

        seed(true);

        assertThat(service.forCurrentOrganization().enabled()).isTrue();
    }

    /**
     * The org-explicit read the delivery gate uses from inside the drain's sweep, where there is no JWT at all. What it
     * has to answer without one is its argument's stored flag, which is why the security context is cleared before the
     * call: {@code forCurrentOrganization()} would throw here, and this must not.
     *
     * <p>
     * It deliberately does not hand the method a <em>different</em> organization from the one the connection is scoped
     * to. That assertion would pass here and prove nothing: this test connects as the table owner, which bypasses
     * {@code roundup_settings}' row-level security by ownership, while under {@code axonia_app} the policy would hide
     * the row and the answer would be false. Production never asks for a cross-tenant read either -
     * {@code CommunicationSweep} wraps each organization's drain in {@code OrganizationScope.runAs}, so the argument
     * always equals the connection's {@code app.current_org} - so the real guarantee is "answers from its argument
     * rather than from a JWT", and that is what is asserted.
     */
    @Test
    void enabledForAnswersFromItsArgumentWithNoJwtToRead() {

        seed(true);
        SecurityContextHolder.clearContext();

        assertThat(service.enabledFor(ORG)).isTrue();
        assertThat(service.enabledFor("never-heard-of-it")).isFalse();
    }

    @Test
    void switchingOnCreatesTheRowAndSwitchingOffUpdatesTheSameOne() {

        assertThat(service.setEnabled(true)).isEqualTo(new RoundupSettings(ORG, true));

        var inserted = row();
        assertThat(inserted).containsEntry("enabled", true).containsEntry("version", 0L);
        assertThat(inserted.get("created_at")).isNotNull();
        assertThat(inserted.get("updated_at")).isNotNull();

        assertThat(service.setEnabled(false).enabled()).isFalse();

        assertThat(rowCount()).isOne();
        assertThat(row()).containsEntry("enabled", false).containsEntry("version", 1L);
    }

    private void seed(boolean enabled) {

        jdbc.update("INSERT INTO roundup_settings (organization_id, enabled, created_at, updated_at, version) "
                + "VALUES (:org, :enabled, now(), now(), 0)", Map.of("org", ORG, "enabled", enabled));
    }

    private Map<String, Object> row() {

        return jdbc.queryForMap("SELECT * FROM roundup_settings WHERE organization_id = :org", Map.of("org", ORG));
    }

    private long rowCount() {

        return jdbc.queryForObject("SELECT count(*) FROM roundup_settings WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }
}

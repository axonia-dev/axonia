package dev.axonia.roundup.application;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.JdbcTestSupport;
import dev.axonia.roundup.adapter.out.persistence.JdbcRoundupSettingsStore;
import dev.axonia.roundup.application.port.out.OrganizationContext;
import dev.axonia.roundup.domain.RoundupSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
 * stored flag is invisible to another. The organization context is a double: which organization the caller acts in is
 * the token adapter's concern, not this service's.
 */
@Import({RoundupSettingsService.class, JdbcRoundupSettingsStore.class})
class RoundupSettingsServiceJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "acme";

    @Autowired
    private RoundupSettingsService service;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private OrganizationContext organizationContext;

    @BeforeEach
    void actInTheOrganization() {

        when(organizationContext.organizationId()).thenReturn(ORG);
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
     * The org-explicit read the delivery gate uses from inside the drain's sweep, where no caller is signed in. It
     * answers from its argument and never asks the organization context.
     *
     * <p>
     * It deliberately does not hand the method a <em>different</em> organization from the one the connection is scoped
     * to. That assertion would pass here and prove nothing: this test connects as the table owner, which bypasses
     * {@code roundup_settings}' row-level security by ownership, while under {@code axonia_app} the policy would hide
     * the row and the answer would be false. Production never asks for a cross-tenant read either -
     * {@code CommunicationSweep} wraps each organization's drain in {@code OrganizationScope.runAs}, so the argument
     * always equals the connection's {@code app.current_org}.
     */
    @Test
    void enabledForAnswersFromItsArgumentWithoutAskingTheOrganizationContext() {

        seed(true);

        assertThat(service.enabledFor(ORG)).isTrue();
        assertThat(service.enabledFor("never-heard-of-it")).isFalse();
        verifyNoInteractions(organizationContext);
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

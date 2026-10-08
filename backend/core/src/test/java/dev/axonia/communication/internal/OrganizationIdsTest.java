package dev.axonia.communication.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.axonia.OrganizationIds;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the QUERY: {@link OrganizationIds#all} returns the ids of every {@code ACTIVE} organization, read through
 * {@code TenantDiscoveryPool}'s own datasource. Seeded and read through the same {@code tenantDiscoveryJdbcClient} pool
 * - as any test of a privileged pool must - because it commits outside this test's primary-datasource transaction, so a
 * row inserted via the primary connection would not yet be visible to a query on this separate one. The ROLE's actual
 * privilege boundary - SELECT on nothing but {@code organization.id} and {@code organization.lifecycle_state}, across
 * every tenant with no scope needed - is proven separately by {@code CommunicationReadRoleIsolationTest}: this shared
 * {@code jdbc:tc} container's driver ignores the configured credentials and connects as the container superuser (see
 * {@code application-DEV.properties}), so it cannot prove the role itself.
 *
 * <p>
 * Cleaned up both before and each test, not only after: this table is shared with every other {@code @SpringBootTest}
 * class in the suite, most of which do not wire {@code cleanup.sql}, so a row left committed by an earlier class would
 * otherwise make {@code allExcludesSuspendedAndClosedOrganizations}' emptiness check order-dependent. Assertions below
 * use {@code contains}/{@code doesNotContain} rather than an exact match for the same reason, in case a leaked row
 * still slips through.
 */
class OrganizationIdsTest extends CommunicationJdbcTestSupport {

    @Autowired
    private OrganizationIds organizationIds;

    @Autowired
    @Qualifier("tenantDiscoveryJdbcClient")
    private JdbcClient jdbc;

    @BeforeEach
    @AfterEach
    void cleanUp() {

        jdbc.sql("DELETE FROM organization").update();
    }

    private void seed(String id, String lifecycleState) {

        seed(id, "HUB", lifecycleState);
    }

    private void seed(String id, String kind, String lifecycleState) {

        jdbc.sql("INSERT INTO organization (id, name, kind, lifecycle_state, created_at, updated_at) "
                + "VALUES (:id, :id, :kind, :lifecycleState, now(), now())").param("id", id).param("kind", kind)
                .param("lifecycleState", lifecycleState).update();
    }

    @Test
    void allReturnsEveryActiveOrganizationId() {

        seed("org-1", "ACTIVE");
        seed("org-2", "ACTIVE");

        assertThat(organizationIds.all()).contains("org-1", "org-2");
    }

    @Test
    void allExcludesSuspendedAndClosedOrganizations() {

        seed("org-active", "ACTIVE");
        seed("org-suspended", "SUSPENDED");
        seed("org-closed", "CLOSED");

        var ids = organizationIds.all();

        assertThat(ids).contains("org-active");
        assertThat(ids).doesNotContain("org-suspended", "org-closed");
    }

    @Test
    void allReturnsAnEmptyListWhenNoOrganizationExists() {

        assertThat(organizationIds.all()).isEmpty();
    }

    @Test
    void ofKindReturnsOnlyActiveOrganizationsOfTheAskedForKind() {

        seed("org-ent-active", "ENTERPRISE", "ACTIVE");
        seed("org-ent-suspended", "ENTERPRISE", "SUSPENDED");
        seed("org-hub-active", "HUB", "ACTIVE");

        var ids = organizationIds.ofKind("ENTERPRISE");

        assertThat(ids).contains("org-ent-active");
        assertThat(ids).doesNotContain("org-ent-suspended", "org-hub-active");
    }

    @Test
    void kindOfReportsTheRawEnumNameOfAnEnterpriseOrganization() {

        seed("org-ent", "ENTERPRISE", "ACTIVE");

        assertThat(organizationIds.kindOf("org-ent")).contains("ENTERPRISE");
    }

    @Test
    void kindOfReportsTheRawEnumNameOfAHubOrganization() {

        seed("org-hub", "HUB", "ACTIVE");

        assertThat(organizationIds.kindOf("org-hub")).contains("HUB");
    }

    @Test
    void kindOfReportsNothingForAnOrganizationThatDoesNotExist() {

        assertThat(organizationIds.kindOf("ghost")).isEmpty();
    }
}

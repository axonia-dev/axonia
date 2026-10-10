package dev.axonia.selfhosted.adapter.out.identity;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.identity.api.spi.OrganizationMembershipSource.OrganizationMembership;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.Organizations;
import dev.axonia.selfhosted.configuration.SelfHostedPropertiesMother;
import dev.axonia.shared.api.tenancy.OrganizationBoundAuthentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class SingleOrganizationMembershipTest {

    @Mock
    private Organizations organizations;

    private SingleOrganizationMembership source() {
        return new SingleOrganizationMembership(SelfHostedPropertiesMother.any(), organizations);
    }

    /** Membership is the issuer's to decide: a subject axonia holds nothing about belongs all the same. */
    @Test
    void everySubjectBelongsToTheOneOrganization() {

        // given
        givenRegistered("Acme");

        // when
        var actual = source().membershipsFor("never-seen-before");

        // then
        assertThat(actual).containsExactly(new OrganizationMembership("acme", "Acme"));
    }

    /** The configured name only seeds the founding; the switcher shows what every other page shows. */
    @Test
    void theNameIsTheRegistrysNotTheConfigurations() {

        // given
        givenRegistered("Acme Corporation");

        // when
        var actual = source().membershipsFor("sub-1");

        // then
        assertThat(actual).containsExactly(new OrganizationMembership("acme", "Acme Corporation"));
    }

    @Test
    void beforeTheFoundingNobodyBelongsToAnything() {

        // given
        when(organizations.byId("acme")).thenReturn(Optional.empty());

        // when
        var actual = source().membershipsFor("sub-1");

        // then
        assertThat(actual).isEmpty();
    }

    /** A background caller (SCIM, the erasure preflight) holds no Organization, so the read sets its own. */
    @Test
    void readsTheRegistryInsideTheOrganizationsOwnScope() {

        // given
        var seen = new AtomicReference<Authentication>();
        when(organizations.byId("acme")).thenAnswer(invocation -> {
            seen.set(SecurityContextHolder.getContext().getAuthentication());
            return Optional.of(acme("Acme"));
        });

        // when
        source().membershipsFor("sub-1");

        // then
        assertThat(seen.get()).isInstanceOfSatisfying(OrganizationBoundAuthentication.class,
                bound -> assertThat(bound.organizationId()).isEqualTo("acme"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void givenRegistered(String name) {
        when(organizations.byId("acme")).thenReturn(Optional.of(acme(name)));
    }

    private static Organization acme(String name) {
        return Organization.builder().id("acme").name(name).kind(OrganizationKind.ENTERPRISE).build();
    }
}

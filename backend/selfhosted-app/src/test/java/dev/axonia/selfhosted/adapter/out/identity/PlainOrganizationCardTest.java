package dev.axonia.selfhosted.adapter.out.identity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.Organizations;
import dev.axonia.shared.api.tenancy.OrganizationBoundAuthentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class PlainOrganizationCardTest {

    @Mock
    private Organizations organizations;

    private PlainOrganizationCard card() {
        return new PlainOrganizationCard(organizations);
    }

    /** No hub card exists in this edition, so the switcher falls back to initials. */
    @Test
    void noOrganizationHasALook() {

        // when
        var actual = card().lookFor(List.of("acme"));

        // then
        assertThat(actual).isEmpty();
        verifyNoInteractions(organizations);
    }

    @Test
    void theKindIsTheRegistrys() {

        // given
        when(organizations.byId("acme")).thenReturn(Optional.of(acme()));

        // when
        var actual = card().kindFor(List.of("acme"));

        // then
        assertThat(actual).isEqualTo(Map.of("acme", "ENTERPRISE"));
    }

    @Test
    void anOrganizationTheRegistryLacksHasNoKind() {

        // given
        when(organizations.byId("ghost")).thenReturn(Optional.empty());

        // when
        var actual = card().kindFor(List.of("ghost"));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void anOrganizationAskedTwiceIsReadOnce() {

        // given
        when(organizations.byId("acme")).thenReturn(Optional.of(acme()));

        // when
        var actual = card().kindFor(List.of("acme", "acme"));

        // then
        assertThat(actual).isEqualTo(Map.of("acme", "ENTERPRISE"));
        verify(organizations, times(1)).byId("acme");
    }

    @Test
    void readsTheRegistryInsideTheOrganizationsOwnScope() {

        // given
        var seen = new AtomicReference<Authentication>();
        when(organizations.byId("acme")).thenAnswer(invocation -> {
            seen.set(SecurityContextHolder.getContext().getAuthentication());
            return Optional.of(acme());
        });

        // when
        card().kindFor(List.of("acme"));

        // then
        assertThat(seen.get()).isInstanceOfSatisfying(OrganizationBoundAuthentication.class,
                bound -> assertThat(bound.organizationId()).isEqualTo("acme"));
    }

    private static Organization acme() {
        return Organization.builder().id("acme").name("Acme").kind(OrganizationKind.ENTERPRISE).build();
    }
}

package dev.axonia.organization.internal;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.NotFoundException;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationKind;
import dev.axonia.organization.OrganizationLifecycleState;
import dev.axonia.organization.OrganizationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
class OrganizationsAdapterTest {

    @Mock
    private OrganizationService service;

    @InjectMocks
    private OrganizationsAdapter sut;

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("admin").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void current_returnsTheRegistryRowForTheTokenOrganization() {

        // given
        var org = Organization.builder().id("acme").name("Acme Corp").build();
        when(service.organization("acme")).thenReturn(Optional.of(org));

        // when
        var actual = sut.current();

        // then — a pure read: no writes on the request path
        assertThat(actual.id()).isEqualTo("acme");
    }

    @Test
    void current_throwsNotFoundWhenTheRegistryRowIsAbsent() {

        // given — a stale token scoped to an organization the registry no longer (or never did) have
        when(service.organization("acme")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.current()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void byId_found_returnsFullyMappedOrganization() {

        // given — byId routes through the service so it returns the same fully populated shape as current()
        var org = Organization.builder().id("hub-a").name("Alpha").kind(OrganizationKind.HUB)
                .lifecycleState(OrganizationLifecycleState.ACTIVE).communicationsEnabled(true).build();
        when(service.organization("hub-a")).thenReturn(Optional.of(org));

        // when
        var actual = sut.byId("hub-a");

        // then
        assertThat(actual).isPresent();
        assertThat(actual.orElseThrow()).usingRecursiveComparison().isEqualTo(org);
    }

    @Test
    void byId_notFound_returnsEmpty() {

        // given
        when(service.organization("unknown")).thenReturn(Optional.empty());

        // when
        var actual = sut.byId("unknown");

        // then
        assertThat(actual).isEmpty();
    }
}

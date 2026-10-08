package dev.axonia.identity;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.identity.OrganizationMembershipSource.OrganizationMembership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyOrganizationsServiceTest {

    @Mock
    private OrganizationMembershipSource source;

    @Mock
    private OrganizationCardLookSource cardLookSource;

    @InjectMocks
    private MyOrganizationsService service;

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void myOrganizations_mapsAndSortsByNameThenId() {

        // given
        authenticate("alice-sub");
        when(source.membershipsFor("alice-sub")).thenReturn(
                List.of(new OrganizationMembership("zeta", "Zeta Corp"), new OrganizationMembership("acme", "Acme")));
        when(cardLookSource.lookFor(any())).thenReturn(Map.of());
        when(cardLookSource.kindFor(any())).thenReturn(Map.of());

        // when
        var actual = service.myOrganizations();

        // then
        assertThat(actual).containsExactly(new OrganizationMembershipView("acme", "Acme", null, null, null),
                new OrganizationMembershipView("zeta", "Zeta Corp", null, null, null));
        verify(source).membershipsFor("alice-sub");
    }

    @Test
    void myOrganizations_sortsByIdWhenNamesEqual() {

        // given
        authenticate("alice-sub");
        when(source.membershipsFor("alice-sub")).thenReturn(
                List.of(new OrganizationMembership("b-org", "Shared"), new OrganizationMembership("a-org", "Shared")));
        when(cardLookSource.lookFor(any())).thenReturn(Map.of());
        when(cardLookSource.kindFor(any())).thenReturn(Map.of());

        // when
        var actual = service.myOrganizations();

        // then
        assertThat(actual).containsExactly(new OrganizationMembershipView("a-org", "Shared", null, null, null),
                new OrganizationMembershipView("b-org", "Shared", null, null, null));
    }

    @Test
    void myOrganizations_emptySource_returnsEmpty() {

        // given
        authenticate("alice-sub");
        when(source.membershipsFor("alice-sub")).thenReturn(List.of());
        when(cardLookSource.lookFor(any())).thenReturn(Map.of());
        when(cardLookSource.kindFor(any())).thenReturn(Map.of());

        // when
        var actual = service.myOrganizations();

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void myOrganizations_decoratesVisibleCards() {

        // given
        authenticate("alice-sub");
        when(source.membershipsFor("alice-sub")).thenReturn(
                List.of(new OrganizationMembership("rust", "Rust"), new OrganizationMembership("secret", "Secret")));
        when(cardLookSource.lookFor(List.of("rust", "secret")))
                .thenReturn(Map.of("rust", new OrganizationCardLookSource.CardLook("https://cdn/x.png", "#1AA6A0")));
        when(cardLookSource.kindFor(List.of("rust", "secret"))).thenReturn(Map.of("rust", "HUB"));

        // when
        var actual = service.myOrganizations();

        // then — visible card decorated, kind included; genuinely invisible one stays fully null
        assertThat(actual).containsExactly(
                new OrganizationMembershipView("rust", "Rust", "https://cdn/x.png", "#1AA6A0", "HUB"),
                new OrganizationMembershipView("secret", "Secret", null, null, null));
    }

    @Test
    void myOrganizations_carriesHubKindEvenWithNoCardLookConfigured() {

        // given — no badge, no accent colour: kind must not be gated by look presence
        authenticate("alice-sub");
        when(source.membershipsFor("alice-sub"))
                .thenReturn(List.of(new OrganizationMembership("split-check", "Split Check Hub")));
        when(cardLookSource.lookFor(List.of("split-check"))).thenReturn(Map.of());
        when(cardLookSource.kindFor(List.of("split-check"))).thenReturn(Map.of("split-check", "HUB"));

        // when
        var actual = service.myOrganizations();

        // then
        assertThat(actual)
                .containsExactly(new OrganizationMembershipView("split-check", "Split Check Hub", null, null, "HUB"));
    }

    @Test
    void myOrganizations_carriesEnterpriseKindEvenWithNoCardLookConfigured() {

        // given
        authenticate("alice-sub");
        when(source.membershipsFor("alice-sub")).thenReturn(List.of(new OrganizationMembership("acme", "Acme")));
        when(cardLookSource.lookFor(List.of("acme"))).thenReturn(Map.of());
        when(cardLookSource.kindFor(List.of("acme"))).thenReturn(Map.of("acme", "ENTERPRISE"));

        // when
        var actual = service.myOrganizations();

        // then
        assertThat(actual).containsExactly(new OrganizationMembershipView("acme", "Acme", null, null, "ENTERPRISE"));
    }

    @Test
    void myOrganizations_leavesKindNullForAGenuinelyInvisibleRegistryRow() {

        // given — the registry row does not exist at all, so even kindFor cannot resolve it
        authenticate("alice-sub");
        when(source.membershipsFor("alice-sub")).thenReturn(List.of(new OrganizationMembership("ghost", "Ghost")));
        when(cardLookSource.lookFor(List.of("ghost"))).thenReturn(Map.of());
        when(cardLookSource.kindFor(List.of("ghost"))).thenReturn(Map.of());

        // when
        var actual = service.myOrganizations();

        // then
        assertThat(actual).containsExactly(new OrganizationMembershipView("ghost", "Ghost", null, null, null));
    }

    private static void authenticate(String subject) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}

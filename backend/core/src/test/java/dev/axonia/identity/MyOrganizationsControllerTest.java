package dev.axonia.identity;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.identity.OrganizationMembershipSource.OrganizationMembership;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@CoreWebMvcTest(controllers = UserController.class)
@Import(MyOrganizationsService.class)
class MyOrganizationsControllerTest extends ControllerTestSupport {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OrganizationMembershipSource source;

    @MockitoBean
    private OrganizationCardLookSource cardLookSource;

    @MockitoBean
    private AvatarOverrideService avatarOverrideService;

    @Test
    void myOrganizations_withOrgClaim_returnsSortedList() throws JacksonException {

        // given
        when(source.membershipsFor(DEFAULT_TEST_USER)).thenReturn(
                List.of(new OrganizationMembership("zeta", "Zeta Corp"), new OrganizationMembership("acme", "Acme")));
        when(cardLookSource.lookFor(any())).thenReturn(Map.of());
        when(cardLookSource.kindFor(any())).thenReturn(Map.of());

        // when
        var actual = mvc.get().uri("/axonia/me/organizations");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(
                jsonMapper.writeValueAsString(List.of(new OrganizationMembershipView("acme", "Acme", null, null, null),
                        new OrganizationMembershipView("zeta", "Zeta Corp", null, null, null))));
        verify(source).membershipsFor(DEFAULT_TEST_USER);
    }

    @Test
    void myOrganizations_withoutOrgClaim_stillReturns200() throws JacksonException {

        // given — a JWT carrying a subject but NO org claim: the endpoint ignores the org claim
        when(source.membershipsFor("no-org-user")).thenReturn(List.of(new OrganizationMembership("acme", "Acme")));
        when(cardLookSource.lookFor(any())).thenReturn(Map.of());
        when(cardLookSource.kindFor(any())).thenReturn(Map.of());

        // when
        var actual = mvc.get().uri("/axonia/me/organizations").with(jwt().jwt(token -> token.subject("no-org-user")));

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper
                .writeValueAsString(List.of(new OrganizationMembershipView("acme", "Acme", null, null, null))));
        verify(source).membershipsFor("no-org-user");
    }

    @Test
    void myOrganizations_decoratesRowsWithKindIndependentlyOfLook() throws JacksonException {

        // given — rust has both look and kind; acme (ENTERPRISE) has kind but no look configured; secret's registry
        // row genuinely does not exist, so it gets neither
        when(source.membershipsFor(DEFAULT_TEST_USER)).thenReturn(List.of(new OrganizationMembership("rust", "Rust"),
                new OrganizationMembership("acme", "Acme"), new OrganizationMembership("secret", "Secret")));
        when(cardLookSource.lookFor(any()))
                .thenReturn(Map.of("rust", new OrganizationCardLookSource.CardLook("https://cdn/rust.png", "#1AA6A0")));
        when(cardLookSource.kindFor(any())).thenReturn(Map.of("rust", "HUB", "acme", "ENTERPRISE"));

        // when
        var actual = mvc.get().uri("/axonia/me/organizations");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper
                .writeValueAsString(List.of(new OrganizationMembershipView("acme", "Acme", null, null, "ENTERPRISE"),
                        new OrganizationMembershipView("rust", "Rust", "https://cdn/rust.png", "#1AA6A0", "HUB"),
                        new OrganizationMembershipView("secret", "Secret", null, null, null))));
    }
}

package dev.axonia.organization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.organization.internal.MemberTally;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class OrganizationAdminServiceTest {

    @Mock
    Organizations organizations;

    @Mock
    OrganizationService organizationService;

    @Mock
    MemberTally memberTally;

    @InjectMocks
    OrganizationAdminService sut;

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
    void current_returnsDomainObject() {

        // given
        var org = Organization.builder().id("acme").name("Acme Corp").kind(OrganizationKind.ENTERPRISE).build();
        when(organizations.current()).thenReturn(org);

        // when
        var result = sut.current();

        // then
        assertThat(result.id()).isEqualTo("acme");
        assertThat(result.name()).isEqualTo("Acme Corp");
    }

    @Test
    void current_reportsTheOrganizationsMemberTallyWhateverItsKind() {

        // given
        var org = Organization.builder().id("acme").name("Acme Corp").kind(OrganizationKind.ENTERPRISE).build();
        when(organizations.current()).thenReturn(org);
        when(memberTally.of("acme")).thenReturn(4L);

        // when
        var result = sut.current();

        // then
        assertThat(result.memberCount()).isEqualTo(4L);
    }

    @Test
    void setCommunicationsEnabled_delegatesToOrganizationService() {

        // given
        var org = Organization.builder().id("acme").name("Acme Corp").communicationsEnabled(false).build();
        when(organizationService.setCommunicationsEnabled(false)).thenReturn(org);

        // when
        var result = sut.setCommunicationsEnabled(false);

        // then
        assertThat(result.communicationsEnabled()).isFalse();
        assertThat(result.id()).isEqualTo("acme");
    }

    @Test
    void setCommunicationsEnabled_answersWithTheSameMemberTallyAsCurrent() {

        // given - the admin console replaces its cached Organization with this answer, so a tally missing here would
        // blank the enterprise's headcount until the next reload
        var org = Organization.builder().id("acme").name("Acme Corp").kind(OrganizationKind.ENTERPRISE)
                .communicationsEnabled(true).build();
        when(organizationService.setCommunicationsEnabled(true)).thenReturn(org);
        when(memberTally.of("acme")).thenReturn(4L);

        // when
        var result = sut.setCommunicationsEnabled(true);

        // then
        assertThat(result.memberCount()).isEqualTo(4L);
        assertThat(result.communicationsEnabled()).isTrue();
    }
}

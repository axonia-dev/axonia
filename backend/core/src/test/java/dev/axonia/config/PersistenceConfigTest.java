package dev.axonia.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.CurrentToken;
import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.SubjectAuthentication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test of the {@code organizationResolver} bean itself — no Spring container needed, it's a plain lambda-producing
 * method. Pins the PROD regression: an org-less {@link JwtAuthenticationToken} (fresh social user, no {@code org}
 * claim, {@code allow-default-fallback} off everywhere except DEV) must resolve to {@link java.util.Optional#empty()} —
 * never throw. A throw here 401s every DB-touching request for that caller, including {@code permitAll} routes that
 * still resolve a JWT principal, which drove the login-loop on the social funnel.
 */
class PersistenceConfigTest {

    private final PersistenceConfig config = new PersistenceConfig();

    @AfterEach
    void clear() {

        SecurityContextHolder.clearContext();
        CurrentToken.setAllowDefaultFallback(false);
    }

    @Test
    void orgLessJwt_fallbackDisabled_resolvesToEmpty_insteadOfThrowing() {

        // given — PROD shape: fallback disabled, JWT carries no org claim
        CurrentToken.setAllowDefaultFallback(false);
        authenticate(Jwt.withTokenValue("t").header("alg", "none").subject("alice").build());

        // when
        var actual = config.organizationResolver().currentOrganization();

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void orgClaimPresent_resolvesToIt() {

        // given
        authenticate(Jwt.withTokenValue("t").header("alg", "none").subject("alice").claim("org", "acme").build());

        // when
        var actual = config.organizationResolver().currentOrganization();

        // then
        assertThat(actual).contains("acme");
    }

    @Test
    void orgLessJwt_fallbackEnabled_resolvesToDefault() {

        // given — DEV shape: fallback enabled
        CurrentToken.setAllowDefaultFallback(true);
        authenticate(Jwt.withTokenValue("t").header("alg", "none").subject("alice").build());

        // when
        var actual = config.organizationResolver().currentOrganization();

        // then
        assertThat(actual).contains("default");
    }

    @Test
    void organizationBoundAuthentication_resolvesToItsOrganizationId() {

        // given — e.g. a SCIM bearer token, which carries its own Organization
        SecurityContextHolder.getContext().setAuthentication(new TestOrganizationBoundAuthentication("scim-org"));

        // when
        var actual = config.organizationResolver().currentOrganization();

        // then
        assertThat(actual).contains("scim-org");
    }

    @Test
    void noAuthentication_resolvesToEmpty() {

        // when
        var actual = config.organizationResolver().currentOrganization();

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void jwtSubjectClaim_resolvesToIt() {

        // given
        authenticate(Jwt.withTokenValue("t").header("alg", "none").subject("subject-alice").build());

        // when
        var actual = config.subjectResolver().currentSubject();

        // then
        assertThat(actual).contains("subject-alice");
    }

    @Test
    void subjectAuthentication_resolvesToItsSubject() {

        // given — SubjectScope.runAs, e.g. a scheduled job acting on a person's own row
        SecurityContextHolder.getContext().setAuthentication(new SubjectAuthentication("subject-bob"));

        // when
        var actual = config.subjectResolver().currentSubject();

        // then
        assertThat(actual).contains("subject-bob");
    }

    @Test
    void noAuthentication_subjectResolvesToEmpty() {

        // when
        var actual = config.subjectResolver().currentSubject();

        // then
        assertThat(actual).isEmpty();
    }

    private static void authenticate(Jwt jwt) {

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static final class TestOrganizationBoundAuthentication
            extends
                org.springframework.security.authentication.AbstractAuthenticationToken
            implements
                OrganizationBoundAuthentication {

        private final String organizationId;

        TestOrganizationBoundAuthentication(String organizationId) {

            super(java.util.List.of());
            this.organizationId = organizationId;
            setAuthenticated(true);
        }

        @Override
        public String organizationId() {

            return organizationId;
        }

        @Override
        public Object getCredentials() {

            return "";
        }

        @Override
        public Object getPrincipal() {

            return organizationId;
        }
    }
}

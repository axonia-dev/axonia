package dev.axonia.privacy;

import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import dev.axonia.CurrentToken;
import dev.axonia.OwnSignInRequiredException;
import dev.axonia.health.HealthController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A plain unit test, not a {@code @WebMvcTest} slice: {@link PrivacyControllerTest} and
 * {@link OrganizationExportControllerTest} already prove the guard actually refuses a delegated session's call to each
 * controller end to end. What those slices cannot exercise - since each loads only its own controller - is the guard's
 * other job: registered globally rather than path-scoped, it must stay a no-op for every handler outside this module's
 * package, delegated or not, or everything else a delegated session may do (viewing a member's saved artifacts, their
 * Organizations, ...) would break.
 */
class PrivacyOwnSignInGuardTest {

    private final PrivacyOwnSignInGuard guard = new PrivacyOwnSignInGuard();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void registersItselfAsTheOneInterceptor() {

        var registry = mock(InterceptorRegistry.class);

        guard.addInterceptors(registry);

        verify(registry).addInterceptor(guard);
    }

    @Test
    void letsThroughAHandlerThatIsNotAControllerMethodAtAll() {

        assertThat(guard.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void letsThroughADelegatedSessionCallingAControllerOutsideThisModule() throws NoSuchMethodException {

        authenticateAsDelegated();

        assertThat(guard.preHandle(request, response, otherModuleControllerHandler())).isTrue();
    }

    @Test
    void letsThroughAnOrdinarySessionCallingPrivacyController() throws NoSuchMethodException {

        authenticate("dev-user");

        assertThat(guard.preHandle(request, response, privacyControllerHandler())).isTrue();
    }

    @Test
    void refusesADelegatedSessionCallingPrivacyController() throws NoSuchMethodException {

        authenticateAsDelegated();

        assertThatThrownBy(() -> guard.preHandle(request, response, privacyControllerHandler()))
                .isInstanceOf(OwnSignInRequiredException.class)
                .hasMessage("This action needs the person's own sign-in.");
    }

    /**
     * Proves the generalisation from a single class equality check to a package check actually widened the net rather
     * than moved it: {@link OrganizationExportController} is a second controller in this module, added after the guard
     * originally shipped, and it is refused with no change to the guard itself.
     */
    @Test
    void refusesADelegatedSessionCallingOrganizationExportController() throws NoSuchMethodException {

        authenticateAsDelegated();

        assertThatThrownBy(() -> guard.preHandle(request, response, organizationExportControllerHandler()))
                .isInstanceOf(OwnSignInRequiredException.class)
                .hasMessage("This action needs the person's own sign-in.");
    }

    private static HandlerMethod privacyControllerHandler() throws NoSuchMethodException {

        return new HandlerMethod(new PrivacyController(null, null, null, null),
                PrivacyController.class.getDeclaredMethod("preflight"));
    }

    private static HandlerMethod organizationExportControllerHandler() throws NoSuchMethodException {

        return new HandlerMethod(new OrganizationExportController(null, null, null),
                OrganizationExportController.class.getDeclaredMethod("export", HttpServletResponse.class));
    }

    /** A stand-in for any controller outside this module's package - the guard must never act on it. */
    private static HandlerMethod otherModuleControllerHandler() throws NoSuchMethodException {

        return new HandlerMethod(new HealthController(null),
                HealthController.class.getDeclaredMethod("snapshot", String.class, String.class));
    }

    private static void authenticate(String subject) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static void authenticateAsDelegated() {

        var jwt = Jwt.withTokenValue("delegated-session").header("alg", "none").subject("dev-user").claim("org", "acme")
                .claim(CurrentToken.ACTOR_CLAIM, Map.of("sub", "actor-1")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}

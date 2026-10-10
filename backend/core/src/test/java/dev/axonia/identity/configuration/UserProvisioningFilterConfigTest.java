package dev.axonia.identity.configuration;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.SteppableClock;
import dev.axonia.identity.api.ClaimsReader;
import dev.axonia.identity.api.IdentityClaims;
import dev.axonia.identity.api.spi.SignInObserver;
import dev.axonia.identity.application.UserService;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class UserProvisioningFilterConfigTest {

    private static final IdentityClaims ALICE = IdentityClaims.builder().userId("alice-sub").organizationId("default")
            .displayName("Alice Anderson").email("alice@axonia.example").build();

    @Mock
    private UserService userService;

    @Mock
    private ClaimsReader claimsReader;

    @Mock
    private SignInObserver observer;

    private final SteppableClock clock = new SteppableClock(Instant.parse("2026-10-01T08:00:00Z"));

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void handsTheFilterTheApplicationClock() throws Exception {

        // given
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "default")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(claimsReader.read()).thenReturn(ALICE);
        var sut = new UserProvisioningFilterConfig().userProvisioningFilter(userService, observer, claimsReader, clock)
                .getFilter();

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        verify(userService, times(1)).provision(ALICE);
        clock.advance(SignInObserver.RESEEN_AFTER);
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        verify(userService, times(2)).provision(ALICE);
    }
}

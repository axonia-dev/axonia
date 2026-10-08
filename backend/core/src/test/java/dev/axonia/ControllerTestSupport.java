package dev.axonia;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.axonia.config.SecurityConfig;
import dev.axonia.config.WebConfig;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("DEV")
@Import({SecurityConfig.class, WebConfig.class, DefaultJwtTestConfig.class})
public abstract class ControllerTestSupport {

    /**
     * Subject forged onto every request by default - matches the legacy stub id so existing tests and Mothers keep
     * working. Act as someone else per request with
     * {@code .with(jwt().jwt(token -> token.subject("other").claim("org", "acme")))}.
     */
    public static final String DEFAULT_TEST_USER = "dev-user";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JsonMapper jsonMapper;

    /** Keeps the resource-server wiring alive without a real issuer; never invoked because jwt() pre-authenticates. */
    @MockitoBean
    private JwtDecoder jwtDecoder;
}

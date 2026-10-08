package dev.axonia.identity.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.axonia.identity.GraphConsent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EntraGraphTokenSourceTest {

    private static final String LOGIN_BASE = "https://login.microsoftonline.com";
    private static final String TENANT = "test-aad-tenant";
    private static final String TOKEN_ENDPOINT = LOGIN_BASE + "/" + TENANT + "/oauth2/v2.0/token";

    private MockRestServiceServer loginServer;
    private EntraGraphTokenSource sut;

    @BeforeEach
    void setUp() {
        var loginBuilder = RestClient.builder().baseUrl(LOGIN_BASE);
        loginServer = MockRestServiceServer.bindTo(loginBuilder).build();
        sut = new EntraGraphTokenSource(loginBuilder.build(), new EntraGraphProperties("app-client-id", "app-secret"));
    }

    @Test
    void probe_sendsAClientCredentialsFormWithTheGraphScope() {

        // given
        loginServer.expect(requestTo(TOKEN_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=app-client-id")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=app-secret")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("scope=https%3A%2F%2Fgraph.microsoft.com%2F.default")))
                .andRespond(withSuccess(tokenJson("tok-1", 3600), MediaType.APPLICATION_JSON));

        // when
        var result = sut.probe(TENANT);

        // then
        loginServer.verify();
        assertThat(result.accessToken()).isEqualTo("tok-1");
    }

    @Test
    void probe_whenTheTokenBodyCarriesNoAccessToken_throwsIllegalStateException() {

        // given
        loginServer.expect(requestTo(TOKEN_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // then
        assertThatThrownBy(() -> sut.probe(TENANT)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
    }

    @Test
    void probe_whenTheTenantHasNoServicePrincipal_isAbsent() {

        // given - what Entra answers when nobody ever granted admin consent
        loginServer.expect(requestTo(TOKEN_ENDPOINT)).andRespond(withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error":"unauthorized_client",
                         "error_description":"AADSTS700016: Application with identifier 'app-client-id' was not found in the directory 'test-aad-tenant'."}
                        """));

        // when
        var result = sut.probe(TENANT);

        // then
        assertThat(result.status()).isEqualTo(GraphConsent.ABSENT);
        assertThat(result.accessToken()).isNull();
        assertThat(result.detail()).contains("AADSTS700016");
    }

    @Test
    void probe_whenTheTokenMints_isGrantedAndCarriesTheToken() {

        // given
        loginServer.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess(tokenJson("tok-probe", 3600), MediaType.APPLICATION_JSON));

        // when
        var result = sut.probe(TENANT);

        // then
        assertThat(result.status()).isEqualTo(GraphConsent.GRANTED);
        assertThat(result.accessToken()).isEqualTo("tok-probe");
    }

    @Test
    void probe_whenTheEndpointIsUnreachable_isUnknownRatherThanAbsent() {

        // given - a transport failure is not evidence that consent is missing
        loginServer.expect(requestTo(TOKEN_ENDPOINT)).andRespond(withServerError());

        // when
        var result = sut.probe(TENANT);

        // then
        assertThat(result.status()).isEqualTo(GraphConsent.UNKNOWN);
    }

    @Test
    void probe_mintsAFreshTokenEveryTime() {

        // given - a remembered token would report the state before the administrator acted
        loginServer.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess(tokenJson("tok-before", 3600), MediaType.APPLICATION_JSON));
        loginServer.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess(tokenJson("tok-after", 3600), MediaType.APPLICATION_JSON));

        // when
        var before = sut.probe(TENANT);
        var after = sut.probe(TENANT);

        // then
        loginServer.verify();
        assertThat(before.accessToken()).isEqualTo("tok-before");
        assertThat(after.accessToken()).isEqualTo("tok-after");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void probe_whenTenantIsBlank_isUnknownAndNeverCallsGraph(String blankTenant) {

        // when - no expectation is set on loginServer, so a call here would fail the test
        var result = sut.probe(blankTenant);

        // then
        loginServer.verify();
        assertThat(result.status()).isEqualTo(GraphConsent.UNKNOWN);
        assertThat(result.accessToken()).isNull();
        assertThat(result.detail()).isEqualTo("the connection carries no directory (tenant) ID");
    }

    @Test
    void probe_whenGraphAppIsNotConfigured_isUnknownAndNeverCallsGraph() {

        // given - a deployment with no axonia Graph app registration, which is every local and test context
        var unconfiguredBuilder = RestClient.builder().baseUrl(LOGIN_BASE);
        var unconfiguredServer = MockRestServiceServer.bindTo(unconfiguredBuilder).build();
        var unconfigured = new EntraGraphTokenSource(unconfiguredBuilder.build(), new EntraGraphProperties(null, null));

        // when - no expectation is set on unconfiguredServer, so a call here would fail the test
        var result = unconfigured.probe(TENANT);

        // then
        unconfiguredServer.verify();
        assertThat(result.status()).isEqualTo(GraphConsent.UNKNOWN);
        assertThat(result.accessToken()).isNull();
        assertThat(result.detail()).isEqualTo("axonia's Graph app registration is not configured");
    }

    private static String tokenJson(String token, int expiresIn) {
        return """
                {"access_token": "%s", "token_type": "Bearer", "expires_in": %d}
                """.formatted(token, expiresIn);
    }
}

package dev.axonia.agent.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.PublicUrlProperties;

@CoreWebMvcTest(controllers = McpOAuthMetadataController.class)
@Import({McpEndpoints.class, McpOAuthConfig.PublicMcpOAuthSecurityConfig.class})
class McpOAuthMetadataControllerTest extends ControllerTestSupport {

    private static final List<String> CATALOG_ORDER = List.of("read", "library:contribute", "topic:propose",
            "actionItem:raise", "session:signup", "attendance:signal", "community:join", "session:attach",
            "session:detach", "schedule:manage", "learning:declare", "members:read");

    @MockitoBean
    PublicUrlProperties publicUrl;

    @BeforeEach
    void issuer() {
        Mockito.when(publicUrl.publicBaseUrl()).thenReturn("https://axonia.test");
    }

    @Test
    void protectedResourceMetadata_isServedAnonymously_atBothPaths() {

        for (var path : new String[]{"/.well-known/oauth-protected-resource/mcp",
                "/.well-known/oauth-protected-resource"}) {
            var actual = mvc.get().uri(path).with(anonymous());

            actual.assertThat().hasStatusOk().bodyJson().satisfies(json -> {
                json.assertThat().extractingPath("$.resource").isEqualTo("https://axonia.test/mcp");
                json.assertThat().extractingPath("$.authorization_servers[0]").isEqualTo("https://axonia.test");
                json.assertThat().extractingPath("$.bearer_methods_supported[0]").isEqualTo("header");
                json.assertThat().extractingPath("$.resource_name").isEqualTo("axonia");
                json.assertThat().extractingPath("$.scopes_supported").asArray()
                        .containsExactlyElementsOf(CATALOG_ORDER);
            });
        }
    }

    @Test
    void authorizationServerMetadata_advertisesCimdAndNoRegistration() {

        var actual = mvc.get().uri("/.well-known/oauth-authorization-server").with(anonymous());

        actual.assertThat().hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.issuer").isEqualTo("https://axonia.test");
            json.assertThat().extractingPath("$.authorization_endpoint")
                    .isEqualTo("https://axonia.test/oauth/authorize");
            json.assertThat().extractingPath("$.token_endpoint").isEqualTo("https://axonia.test/oauth/token");
            json.assertThat().extractingPath("$.revocation_endpoint").isEqualTo("https://axonia.test/oauth/revoke");
            json.assertThat().extractingPath("$.response_types_supported").asArray().containsExactly("code");
            json.assertThat().extractingPath("$.grant_types_supported").asArray().containsExactly("authorization_code",
                    "refresh_token");
            json.assertThat().extractingPath("$.code_challenge_methods_supported").asArray().containsExactly("S256");
            json.assertThat().extractingPath("$.token_endpoint_auth_methods_supported").asArray()
                    .containsExactly("none");
            json.assertThat().extractingPath("$.revocation_endpoint_auth_methods_supported").asArray()
                    .containsExactly("none");
            json.assertThat().extractingPath("$.client_id_metadata_document_supported").isEqualTo(true);
            json.assertThat().extractingPath("$.authorization_response_iss_parameter_supported").isEqualTo(true);
            json.assertThat().extractingPath("$.scopes_supported").asArray().containsExactlyElementsOf(CATALOG_ORDER);
            json.assertThat().doesNotHavePath("$.registration_endpoint");
        });
    }

    private static RequestPostProcessor anonymous() {
        return SecurityMockMvcRequestPostProcessors.anonymous();
    }
}

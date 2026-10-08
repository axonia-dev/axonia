package dev.axonia.identity.scim;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.axonia.CoreWebMvcTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link ScimDiscoveryController}.
 *
 * <p>
 * Strategy: {@code @WebMvcTest} with filters disabled via {@code @AutoConfigureMockMvc(addFilters=false)}. Auth is
 * injected directly into the {@code SecurityContextHolder} via a custom {@link RequestPostProcessor}, mirroring the
 * pattern used in {@link ScimUserControllerTest}.
 */
@CoreWebMvcTest(controllers = ScimDiscoveryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ScimExceptionHandler.class})
class ScimDiscoveryControllerTest {

    private static final String SCIM_CONTENT_TYPE = "application/scim+json";
    private static final ScimPrincipal PRINCIPAL = new ScimPrincipal("default");

    @Autowired
    private MockMvc mvc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // GET /scim/v2/ServiceProviderConfig
    // ------------------------------------------------------------------

    @Test
    void serviceProviderConfig_returnsSupportedFlags() throws Exception {

        // then
        mvc.perform(get("/scim/v2/ServiceProviderConfig").with(scimAuth())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.patch.supported").value(true))
                .andExpect(jsonPath("$.bulk.supported").value(false))
                .andExpect(jsonPath("$.filter.supported").value(true)).andExpect(
                        jsonPath("$.schemas[0]").value("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
    }

    // ------------------------------------------------------------------
    // GET /scim/v2/ResourceTypes
    // ------------------------------------------------------------------

    @Test
    void resourceTypes_listsUserAndGroup() throws Exception {

        // then
        mvc.perform(get("/scim/v2/ResourceTypes").with(scimAuth())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.totalResults").value(2)).andExpect(jsonPath("$.Resources[0].name").value("User"))
                .andExpect(jsonPath("$.Resources[0].endpoint").value("/Users"))
                .andExpect(jsonPath("$.Resources[1].name").value("Group"))
                .andExpect(jsonPath("$.Resources[1].endpoint").value("/Groups"));
    }

    // ------------------------------------------------------------------
    // GET /scim/v2/Schemas
    // ------------------------------------------------------------------

    @Test
    void schemas_listsUserAndGroupSchemas() throws Exception {

        // then
        mvc.perform(get("/scim/v2/Schemas").with(scimAuth())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.totalResults").value(3))
                .andExpect(jsonPath("$.Resources[0].id").value("urn:ietf:params:scim:schemas:core:2.0:User"))
                .andExpect(jsonPath("$.Resources[1].id").value("urn:ietf:params:scim:schemas:core:2.0:Group"));
    }

    @Test
    void schemas_advertisesEnterpriseExtension() throws Exception {

        mvc.perform(get("/scim/v2/Schemas").with(scimAuth())).andExpect(status().isOk()).andExpect(
                jsonPath("$.Resources[?(@.id == " + "'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User')]")
                        .exists());
    }

    @Test
    void resourceTypes_userAdvertisesEnterpriseSchemaExtension() throws Exception {

        mvc.perform(get("/scim/v2/ResourceTypes").with(scimAuth())).andExpect(status().isOk())
                .andExpect(jsonPath("$.Resources[?(@.id == 'User')].schemaExtensions").exists());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static RequestPostProcessor scimAuth() {
        return new RequestPostProcessor() {
            @Override
            public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(new ScimAuthenticationToken(PRINCIPAL));
                SecurityContextHolder.setContext(ctx);
                return request;
            }
        };
    }
}

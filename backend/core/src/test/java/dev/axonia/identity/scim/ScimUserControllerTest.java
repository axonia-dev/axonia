package dev.axonia.identity.scim;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.axonia.CoreWebMvcTest;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link ScimUserController}.
 *
 * <p>
 * Strategy: {@code @WebMvcTest} with filters disabled via {@code @AutoConfigureMockMvc(addFilters=false)}. This avoids
 * re-testing the SCIM security filter chain (covered in {@link ScimSecurityChainTest}) and lets us inject a
 * {@link ScimAuthenticationToken} directly. Since the {@code SecurityContextHolderFilter} is not running (filters
 * disabled), we cannot use the session-based {@code authentication()} RequestPostProcessor — it stores auth in the
 * {@code HttpSession} which the filter would normally read and populate {@code SecurityContextHolder} from. Instead, we
 * use a custom {@code RequestPostProcessor} ({@link #scimAuth()}) that sets the security context directly on the
 * current thread via {@code SecurityContextHolder.setContext()} right before each request is dispatched. An
 * {@code @AfterEach} clears the context so tests do not bleed into each other.
 */
@CoreWebMvcTest(controllers = ScimUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ScimExceptionHandler.class})
class ScimUserControllerTest {

    private static final String SCIM_CONTENT_TYPE = "application/scim+json";
    private static final ScimPrincipal PRINCIPAL = new ScimPrincipal("default");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private ScimUserService scimUserService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // POST /scim/v2/Users → 201
    // ------------------------------------------------------------------

    @Test
    void createUser_returns201WithId() throws Exception {

        // given
        ScimUser created = scimUser("scim-id-1", "alice@acme.test");
        when(scimUserService.create(any(), any())).thenReturn(created);

        String body = jsonMapper.writeValueAsString(scimUser(null, "alice@acme.test"));

        // then
        mvc.perform(post("/scim/v2/Users").contentType(SCIM_CONTENT_TYPE).content(body).with(scimAuth()))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.id").value("scim-id-1"))
                .andExpect(jsonPath("$.userName").value("alice@acme.test"));
    }

    // ------------------------------------------------------------------
    // GET /scim/v2/Users/{id} → 200
    // ------------------------------------------------------------------

    @Test
    void getUser_returns200() throws Exception {

        // given
        ScimUser user = scimUser("scim-id-1", "alice@acme.test");
        when(scimUserService.get(any(), eq("scim-id-1"))).thenReturn(user);

        // then
        mvc.perform(get("/scim/v2/Users/scim-id-1").with(scimAuth())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.id").value("scim-id-1"));
    }

    // ------------------------------------------------------------------
    // GET /scim/v2/Users?filter= → 200 ListResponse
    // ------------------------------------------------------------------

    @Test
    void listUsers_withFilter_returnsListResponse() throws Exception {

        // given
        ScimUser alice = scimUser("scim-id-1", "alice@acme.test");
        ScimListResponse<ScimUser> listResponse = ScimListResponse.of(1, 1, 1, List.of(alice));
        when(scimUserService.list(any(), eq("userName eq \"alice@acme.test\""))).thenReturn(listResponse);

        // then
        mvc.perform(get("/scim/v2/Users").param("filter", "userName eq \"alice@acme.test\"").with(scimAuth()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.totalResults").value(1))
                .andExpect(jsonPath("$.Resources[0].id").value("scim-id-1"))
                .andExpect(jsonPath("$.Resources[0].userName").value("alice@acme.test"));
    }

    // ------------------------------------------------------------------
    // PUT /scim/v2/Users/{id} → 200
    // ------------------------------------------------------------------

    @Test
    void replaceUser_returns200() throws Exception {

        // given
        ScimUser updated = scimUser("scim-id-1", "alice@acme.test");
        when(scimUserService.replace(any(), eq("scim-id-1"), any())).thenReturn(updated);

        String body = jsonMapper.writeValueAsString(scimUser("scim-id-1", "alice@acme.test"));

        // then
        mvc.perform(put("/scim/v2/Users/scim-id-1").contentType(SCIM_CONTENT_TYPE).content(body).with(scimAuth()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.id").value("scim-id-1"));
    }

    // ------------------------------------------------------------------
    // PATCH /scim/v2/Users/{id} → 200
    // ------------------------------------------------------------------

    @Test
    void patchUser_activeFalse_returns200() throws Exception {

        // given
        ScimUser patched = scimUser("scim-id-1", "alice@acme.test");
        when(scimUserService.patch(any(), eq("scim-id-1"), any())).thenReturn(patched);

        ScimPatchRequest patchRequest = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "active", false)));
        String body = jsonMapper.writeValueAsString(patchRequest);

        // then
        mvc.perform(patch("/scim/v2/Users/scim-id-1").contentType(SCIM_CONTENT_TYPE).content(body).with(scimAuth()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.id").value("scim-id-1"));
    }

    // ------------------------------------------------------------------
    // DELETE /scim/v2/Users/{id} → 204
    // ------------------------------------------------------------------

    @Test
    void deleteUser_returns204() throws Exception {

        // given
        doNothing().when(scimUserService).delete(any(), eq("scim-id-1"));

        // then
        mvc.perform(delete("/scim/v2/Users/scim-id-1").with(scimAuth())).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // Error: not found → SCIM-shaped 404
    // ------------------------------------------------------------------

    @Test
    void getUser_notFound_returnsScim404() throws Exception {

        // given
        when(scimUserService.get(any(), eq("missing-id")))
                .thenThrow(ScimException.notFound("No SCIM user found: missing-id"));

        // then
        mvc.perform(get("/scim/v2/Users/missing-id").with(scimAuth())).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.status").value("404"))
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:Error"));
    }

    // ------------------------------------------------------------------
    // Content-Type: application/json input also accepted (IdP compat)
    // ------------------------------------------------------------------

    @Test
    void createUser_withApplicationJsonContentType_returns201() throws Exception {

        // given
        ScimUser created = scimUser("scim-id-2", "bob@acme.test");
        when(scimUserService.create(any(), any())).thenReturn(created);

        String body = jsonMapper.writeValueAsString(scimUser(null, "bob@acme.test"));

        // then
        mvc.perform(post("/scim/v2/Users").contentType(MediaType.APPLICATION_JSON).content(body).with(scimAuth()))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * {@link RequestPostProcessor} that injects a {@link ScimAuthenticationToken} directly into the thread-local
     * {@link SecurityContextHolder}. Needed because filters are disabled ({@code addFilters=false}), so the
     * session-based {@code authentication()} post-processor from Spring Security test support cannot propagate the
     * context.
     */
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

    private static ScimUser scimUser(String id, String userName) {
        return ScimUser.builder().id(id).userName(userName).displayName(userName).emails(List.of()).active(true)
                .build();
    }
}

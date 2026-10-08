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
 * Unit tests for {@link ScimGroupController}.
 *
 * <p>
 * Strategy: {@code @WebMvcTest} with filters disabled via {@code @AutoConfigureMockMvc(addFilters=false)}. Principal is
 * injected directly into {@link SecurityContextHolder} via a {@link RequestPostProcessor} since the security filter
 * chain is not running. An {@code @AfterEach} clears the context so tests do not bleed into each other.
 */
@CoreWebMvcTest(controllers = ScimGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ScimExceptionHandler.class})
class ScimGroupControllerTest {

    private static final String SCIM_CONTENT_TYPE = "application/scim+json";
    private static final ScimPrincipal PRINCIPAL = new ScimPrincipal("default");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private ScimGroupService scimGroupService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // POST /scim/v2/Groups → 201
    // ------------------------------------------------------------------

    @Test
    void createGroup_returns201WithId() throws Exception {

        // given
        ScimGroup created = scimGroup("scim-g-1", "Engineers");
        when(scimGroupService.create(any(), any())).thenReturn(created);

        String body = jsonMapper.writeValueAsString(scimGroup(null, "Engineers"));

        // then
        mvc.perform(post("/scim/v2/Groups").contentType(SCIM_CONTENT_TYPE).content(body).with(scimAuth()))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.id").value("scim-g-1")).andExpect(jsonPath("$.displayName").value("Engineers"));
    }

    // ------------------------------------------------------------------
    // GET /scim/v2/Groups/{id} → 200
    // ------------------------------------------------------------------

    @Test
    void getGroup_returns200() throws Exception {

        // given
        ScimGroup group = scimGroup("scim-g-1", "Ops");
        when(scimGroupService.get(any(), eq("scim-g-1"))).thenReturn(group);

        // then
        mvc.perform(get("/scim/v2/Groups/scim-g-1").with(scimAuth())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.id").value("scim-g-1"));
    }

    // ------------------------------------------------------------------
    // GET /scim/v2/Groups?filter= → 200 ListResponse
    // ------------------------------------------------------------------

    @Test
    void listGroups_withFilter_returnsListResponse() throws Exception {

        // given
        ScimGroup group = scimGroup("scim-g-1", "Platform");
        ScimListResponse<ScimGroup> listResponse = ScimListResponse.of(1, 1, 1, List.of(group));
        when(scimGroupService.list(any(), eq("displayName eq \"Platform\""))).thenReturn(listResponse);

        // then
        mvc.perform(get("/scim/v2/Groups").param("filter", "displayName eq \"Platform\"").with(scimAuth()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.totalResults").value(1))
                .andExpect(jsonPath("$.Resources[0].id").value("scim-g-1"))
                .andExpect(jsonPath("$.Resources[0].displayName").value("Platform"));
    }

    // ------------------------------------------------------------------
    // PUT /scim/v2/Groups/{id} → 200
    // ------------------------------------------------------------------

    @Test
    void replaceGroup_returns200() throws Exception {

        // given
        ScimGroup updated = scimGroup("scim-g-1", "Engineers");
        when(scimGroupService.replace(any(), eq("scim-g-1"), any())).thenReturn(updated);

        String body = jsonMapper.writeValueAsString(scimGroup("scim-g-1", "Engineers"));

        // then
        mvc.perform(put("/scim/v2/Groups/scim-g-1").contentType(SCIM_CONTENT_TYPE).content(body).with(scimAuth()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.id").value("scim-g-1"));
    }

    // ------------------------------------------------------------------
    // PATCH /scim/v2/Groups/{id} → 200
    // ------------------------------------------------------------------

    @Test
    void patchGroup_addMember_returns200() throws Exception {

        // given
        ScimGroup patched = scimGroup("scim-g-1", "QA");
        when(scimGroupService.patch(any(), eq("scim-g-1"), any())).thenReturn(patched);

        ScimPatchRequest patchRequest = ScimPatchRequest.of(
                List.of(new ScimPatchOperation("add", "members", List.of(java.util.Map.of("value", "user-scim-1")))));
        String body = jsonMapper.writeValueAsString(patchRequest);

        // then
        mvc.perform(patch("/scim/v2/Groups/scim-g-1").contentType(SCIM_CONTENT_TYPE).content(body).with(scimAuth()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.id").value("scim-g-1"));
    }

    // ------------------------------------------------------------------
    // DELETE /scim/v2/Groups/{id} → 204
    // ------------------------------------------------------------------

    @Test
    void deleteGroup_returns204() throws Exception {

        // given
        doNothing().when(scimGroupService).delete(any(), eq("scim-g-1"));

        // then
        mvc.perform(delete("/scim/v2/Groups/scim-g-1").with(scimAuth())).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // Error: not found → SCIM-shaped 404
    // ------------------------------------------------------------------

    @Test
    void getGroup_notFound_returnsScim404() throws Exception {

        // given
        when(scimGroupService.get(any(), eq("missing-id")))
                .thenThrow(ScimException.notFound("No SCIM group found: missing-id"));

        // then
        mvc.perform(get("/scim/v2/Groups/missing-id").with(scimAuth())).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE))
                .andExpect(jsonPath("$.status").value("404"))
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:Error"));
    }

    // ------------------------------------------------------------------
    // Content-Type: application/json input also accepted (IdP compat)
    // ------------------------------------------------------------------

    @Test
    void createGroup_withApplicationJsonContentType_returns201() throws Exception {

        // given
        ScimGroup created = scimGroup("scim-g-2", "Security");
        when(scimGroupService.create(any(), any())).thenReturn(created);

        String body = jsonMapper.writeValueAsString(scimGroup(null, "Security"));

        // then
        mvc.perform(post("/scim/v2/Groups").contentType(MediaType.APPLICATION_JSON).content(body).with(scimAuth()))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(SCIM_CONTENT_TYPE));
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

    private static ScimGroup scimGroup(String id, String displayName) {
        return ScimGroup.builder().id(id).displayName(displayName).members(List.of()).build();
    }
}

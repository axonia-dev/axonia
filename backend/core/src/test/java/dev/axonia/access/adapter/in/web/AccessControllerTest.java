package dev.axonia.access.adapter.in.web;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.access.application.Role;
import dev.axonia.access.application.RoleBinding;
import dev.axonia.access.application.RoleBindingService;
import dev.axonia.access.application.RoleService;
import dev.axonia.shared.api.error.ForbiddenException;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = AccessController.class)
class AccessControllerTest extends ControllerTestSupport {

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private RoleBindingService bindingService;

    @MockitoBean
    private AuthorizationGate gate;

    @Test
    void roles_returnsRoleList() throws JacksonException {

        // given
        var role = Role.builder().id(1L).name("Admin").permissions(Set.of()).build();
        when(roleService.list()).thenReturn(List.of(role));

        // when
        var actual = mvc.get().uri("/axonia/roles");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(List.of(role)));
    }

    @Test
    void roles_serializePermissionsAsCodes_notEnumNames() {

        // given a role carrying real permissions
        var role = Role.builder().id(1L).name("Admin").permissions(Set.of(Permission.SCIM_READ, Permission.ROLE_WRITE))
                .build();
        when(roleService.list()).thenReturn(List.of(role));

        // when
        var actual = mvc.get().uri("/axonia/roles");

        // then the wire format uses permission codes (what the UI and KnownPermissions read), never constant names
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$[0].permissions").asArray()
                .containsExactlyInAnyOrder("scim:read", "role:write");
    }

    @Test
    void roles_serializeSystemAndSlug() {

        // given a system role with a slug — the frontend reads role.system to lock system roles
        var role = Role.builder().id(1L).name("Owner").system(true).slug("owner").permissions(Set.of()).build();
        when(roleService.list()).thenReturn(List.of(role));

        // when
        var actual = mvc.get().uri("/axonia/roles");

        // then both fields must appear in the wire response (guards against accidental @JsonIgnore / exclusion)
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$[0].system").isEqualTo(true);
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$[0].slug").isEqualTo("owner");
    }

    @Test
    void bindings_returnsBindingList() throws JacksonException {

        // given
        var binding = RoleBinding.builder().id(1L).subjectType("USER").subjectId("u1").roleId(2L).build();
        when(bindingService.list()).thenReturn(List.of(binding));

        // when
        var actual = mvc.get().uri("/axonia/role-bindings");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(List.of(binding)));
    }

    @Test
    void grant_validBody_returns201() throws JacksonException {

        // given
        var binding = RoleBinding.builder().id(10L).subjectType("USER").subjectId("u1").roleId(1L).build();
        when(bindingService.create(any())).thenReturn(binding);

        // when
        var actual = mvc.post().uri("/axonia/role-bindings").contentType(MediaType.APPLICATION_JSON).content("""
                {"subjectType":"USER","subjectId":"u1","roleId":1}""");

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(binding));
    }

    @Test
    void grant_forbidden_returns403() {

        // given
        doThrow(new ForbiddenException("no permission")).when(bindingService).create(any());

        // when
        var actual = mvc.post().uri("/axonia/role-bindings").contentType(MediaType.APPLICATION_JSON).content("""
                {"subjectType":"USER","subjectId":"u1","roleId":1}""");

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void revoke_returns204() {

        // when
        var actual = mvc.delete().uri("/axonia/role-bindings/{id}", 5L);

        // then
        actual.assertThat().hasStatus(204);
        verify(bindingService).delete(5L);
    }

    @Test
    void confirmGroup_returns204() {

        // when
        var actual = mvc.post().uri("/axonia/role-bindings/{id}/confirm-group", 5L);

        // then
        actual.assertThat().hasStatus(204);
        verify(bindingService).confirmGroup(5L);
    }

    @Test
    void confirmGroup_forbidden_returns403() {

        // given - the release valve is gated on role:assign, the permission a SCIM writer must not need to hold
        doThrow(new ForbiddenException("no permission")).when(bindingService).confirmGroup(5L);

        // when
        var actual = mvc.post().uri("/axonia/role-bindings/{id}/confirm-group", 5L);

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void createRole_returns201() throws JacksonException {

        var role = Role.builder().id(7L).name("Curator").description(null).permissions(Set.of()).build();
        when(roleService.create(any())).thenReturn(role);

        var actual = mvc.post().uri("/axonia/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Curator","permissions":["library_type:read"]}""");

        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(role));
    }

    @Test
    void updateRole_returns200() throws JacksonException {

        var role = Role.builder().id(7L).name("Curator").description("x").permissions(Set.of()).build();
        when(roleService.update(org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(role);

        var actual = mvc.put().uri("/axonia/roles/{id}", 7L).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Curator","permissions":[]}""");

        actual.assertThat().hasStatus(200);
    }

    @Test
    void deleteRole_returns204() {

        var actual = mvc.delete().uri("/axonia/roles/{id}", 7L);

        actual.assertThat().hasStatus(204);
        verify(roleService).delete(7L);
    }

    @Test
    void deleteRole_inUse_returns409() {

        doThrow(new dev.axonia.shared.api.error.ConflictException("role_in_use", "in use")).when(roleService)
                .delete(any());

        var actual = mvc.delete().uri("/axonia/roles/{id}", 7L);

        actual.assertThat().hasStatus(409);
    }
}

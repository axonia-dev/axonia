package dev.axonia.access;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = AdminUserController.class)
class AdminUserControllerTest extends ControllerTestSupport {

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private RoleBindingService bindingService;

    @Test
    void users_returnsUserPage() {

        // given — one person on this page, but a roster of 42
        var effectiveRoles = List.of(EffectiveRole.builder().role("Admin").via("DIRECT").build());
        var user = AdminUser.builder().userId("u1").displayName("Alice").email("alice@acme.com")
                .effectiveRoles(effectiveRoles).build();
        when(adminUserService.list("al", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 42));

        // when
        var actual = mvc.get().uri("/axonia/admin/users").param("q", "al").param("page", "0").param("size", "20");

        // then — the PagedModel envelope carries the roster total, so the console can tally what it cannot show
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content[0].userId").isEqualTo("u1");
        actual.assertThat().bodyJson().extractingPath("$.page.totalElements").isEqualTo(42);
    }

    @Test
    void user_returnsUser() throws JacksonException {

        // given
        var user = AdminUser.builder().userId("u1").displayName("Alice").email("alice@acme.com")
                .effectiveRoles(List.of()).build();
        when(adminUserService.byId("u1")).thenReturn(user);

        // when
        var actual = mvc.get().uri("/axonia/admin/users/{userId}", "u1");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(user));
    }

    @Test
    void user_notFound_returns404() {

        // given
        when(adminUserService.byId("unknown")).thenThrow(new NotFoundException("User not found: unknown"));

        // when
        var actual = mvc.get().uri("/axonia/admin/users/{userId}", "unknown");

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void users_forbidden_returns403() {

        // given
        when(adminUserService.list(anyString(), any(Pageable.class)))
                .thenThrow(new ForbiddenException("missing member:read"));

        // when
        var actual = mvc.get().uri("/axonia/admin/users");

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void assignees_returnsBindingList() throws JacksonException {

        // given
        var binding = RoleBinding.builder().id(1L).subjectType("USER").subjectId("u1").roleId(5L).build();
        when(bindingService.assigneesOf(5L)).thenReturn(List.of(binding));

        // when
        var actual = mvc.get().uri("/axonia/roles/{id}/assignees", 5L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(List.of(binding)));
    }
}

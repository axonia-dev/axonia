package dev.axonia.access.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.access.application.GroupBindingService;
import dev.axonia.access.application.IdpGroupView;
import dev.axonia.access.application.IdpGroupsResponse;
import dev.axonia.access.application.RoleBinding;
import dev.axonia.shared.api.error.ForbiddenException;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = IdpGroupController.class)
class IdpGroupControllerTest extends ControllerTestSupport {

    @MockitoBean
    private GroupBindingService groupBindingService;

    @Test
    void groups_returnsViewList() throws JacksonException {

        // given
        var bindings = List.of(RoleBinding.builder().id(1L).subjectType("GROUP").subjectId("grp-admin")
                .subjectLabel("Admin Group").roleId(2L).build());
        var view = new IdpGroupView("grp-admin", "Admin Group", true, false, bindings);
        var response = new IdpGroupsResponse("SCIM", List.of(view));
        when(groupBindingService.list()).thenReturn(response);

        // when
        var actual = mvc.get().uri("/axonia/admin/idp-groups");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(response));
    }

    @Test
    void groups_forbidden_returns403() {

        // given
        when(groupBindingService.list()).thenThrow(new ForbiddenException("no permission"));

        // when
        var actual = mvc.get().uri("/axonia/admin/idp-groups");

        // then
        actual.assertThat().hasStatus(403);
    }
}

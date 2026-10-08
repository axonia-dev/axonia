package dev.axonia.agent;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;

import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = AgentAccessController.class)
class AgentAccessControllerTest extends ControllerTestSupport {

    @MockitoBean
    private AgentAccess access;

    @Test
    void reportsAvailable_forAnOrganizationThatMayConnectAgents() {

        // given
        when(access.isAvailableToCaller()).thenReturn(true);

        // when
        var actual = mvc.get().uri("/axonia/me/agent-access");

        // then
        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.available").isEqualTo(true);
    }

    @Test
    void reportsUnavailable_forAnOrganizationThatMayNot() {

        // given
        when(access.isAvailableToCaller()).thenReturn(false);

        // when
        var actual = mvc.get().uri("/axonia/me/agent-access");

        // then
        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.available").isEqualTo(false);
    }
}

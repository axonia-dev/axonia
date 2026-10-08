package dev.axonia.organization;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;

import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = OrganizationAdminController.class)
class OrganizationAdminControllerTest extends ControllerTestSupport {

    @MockitoBean
    private OrganizationAdminService service;

    @Test
    void current_answersEveryFieldTheAdminConsoleReads() {

        // given
        var org = CurrentOrganization.builder().id("acme").name("Acme Corp").kind(OrganizationKind.ENTERPRISE)
                .lifecycleState(OrganizationLifecycleState.ACTIVE).communicationsEnabled(true).memberCount(4L).build();
        when(service.current()).thenReturn(org);

        // when
        var actual = mvc.get().uri("/axonia/organizations/current");

        // then
        var body = actual.assertThat().hasStatus(200).bodyJson();
        body.extractingPath("$.id").isEqualTo("acme");
        body.extractingPath("$.name").isEqualTo("Acme Corp");
        body.extractingPath("$.kind").isEqualTo("ENTERPRISE");
        body.extractingPath("$.memberCount").isEqualTo(4);
        body.extractingPath("$.communicationsEnabled").isEqualTo(true);
    }

    @Test
    void setCommunicationsEnabled_returnsUpdatedOrganization() {

        // given
        var org = CurrentOrganization.builder().id("acme").name("Acme Corp").communicationsEnabled(false).build();
        when(service.setCommunicationsEnabled(false)).thenReturn(org);

        // when
        var actual = mvc.put().uri("/axonia/organizations/current/communications-enabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UpdateCommunicationsEnabledRequest(false)));

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.communicationsEnabled").isEqualTo(false);
    }

    @Test
    void setCommunicationsEnabled_rejectsMissingEnabled() {

        // when
        var actual = mvc.put().uri("/axonia/organizations/current/communications-enabled")
                .contentType(MediaType.APPLICATION_JSON).content("{}");

        // then
        actual.assertThat().hasStatus(400);
    }
}

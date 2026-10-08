package dev.axonia.knowledge;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.knowledge.mother.ArtifactMother;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ResourceController.class)
class ResourceControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ResourceService resourceService;

    @Test
    void resources_returnsList() throws JacksonException {

        // given
        var resource = SessionResource.builder().attachmentId(5L).artifact(ArtifactMother.any().id(9L).build())
                .segmentId(7L).attachedByUserId("alice").build();
        when(resourceService.resourcesForSession("frontend-guild", 100L)).thenReturn(List.of(resource));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/sessions/{sessionId}/resources", "frontend-guild", 100L);

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$[0].segmentId").isEqualTo(7);
    }

    @Test
    void attach_created() throws JacksonException {

        // given
        var attachment = Attachment.builder().id(42L).artifactId(9L).sessionId(100L).segmentId(7L)
                .attachedByUserId("dev-user").build();
        when(resourceService.attach(eq("frontend-guild"), eq(100L), eq(new AttachResourceRequest(7L, 9L))))
                .thenReturn(attachment);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/sessions/{sessionId}/resources", "frontend-guild", 100L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"segmentId\":7,\"artifactId\":9}");

        // then
        actual.assertThat().hasStatus(201).bodyJson().extractingPath("$.id").isEqualTo(42);
    }

    @Test
    void attach_sessionLevel_created() throws JacksonException {

        // given
        var attachment = Attachment.builder().id(42L).artifactId(9L).sessionId(100L).segmentId(null)
                .attachedByUserId("dev-user").build();
        when(resourceService.attach(eq("frontend-guild"), eq(100L), eq(new AttachResourceRequest(null, 9L))))
                .thenReturn(attachment);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/sessions/{sessionId}/resources", "frontend-guild", 100L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"artifactId\":9}");

        // then
        actual.assertThat().hasStatus(201).bodyJson().extractingPath("$.id").isEqualTo(42);
    }

    @Test
    void attach_rejectsMissingFields() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/sessions/{sessionId}/resources", "frontend-guild", 100L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"segmentId\":7}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void detach_talkRow_noContent() {

        // when
        var actual = mvc.delete().uri(
                "/axonia/communities/{slug}/sessions/{sessionId}/resources/{artifactId}?segmentId={segmentId}",
                "frontend-guild", 100L, 9L, 7L);

        // then
        actual.assertThat().hasStatus(204);
        verify(resourceService).detach("frontend-guild", 100L, 9L, 7L);
    }

    @Test
    void detach_sessionRow_noContent() {

        // when
        var actual = mvc.delete().uri("/axonia/communities/{slug}/sessions/{sessionId}/resources/{artifactId}",
                "frontend-guild", 100L, 9L);

        // then
        actual.assertThat().hasStatus(204);
        verify(resourceService).detach("frontend-guild", 100L, 9L, null);
    }
}

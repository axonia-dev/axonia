package dev.axonia.knowledge;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.knowledge.mother.ArtifactMother;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ArtifactController.class)
class ArtifactControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ArtifactService artifactService;

    @MockitoBean
    private ArtifactCreditService artifactCreditService;

    @Test
    void search() throws JacksonException {

        // given
        var artifact = ArtifactMother.any().build();
        when(artifactService.search(eq("frontend-guild"), any(ArtifactFilter.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(artifact), PageRequest.of(0, 20), 1));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/artifacts", "frontend-guild").queryParam("q", "retries")
                .queryParam("tag", "resilience").queryParam("type", "1");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void search_passesSessionFilter() throws JacksonException {

        // given
        var artifact = ArtifactMother.any().build();
        var captor = org.mockito.ArgumentCaptor.forClass(ArtifactFilter.class);
        when(artifactService.search(eq("frontend-guild"), captor.capture(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(artifact), PageRequest.of(0, 20), 1));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/artifacts", "frontend-guild").queryParam("session",
                "42");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().session()).isEqualTo(42L);
    }

    @Test
    void search_passesSortParamAndExposesLoveCount() throws JacksonException {

        // given
        var artifact = ArtifactMother.any().loveCount(12).build();
        var captor = org.mockito.ArgumentCaptor.forClass(ArtifactFilter.class);
        when(artifactService.search(eq("frontend-guild"), captor.capture(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(artifact), PageRequest.of(0, 20), 1));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/artifacts", "frontend-guild").queryParam("sort",
                "mostAppreciated");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.content[0].loveCount").isEqualTo(12);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sort()).isEqualTo("mostAppreciated");
    }

    @Test
    void sessions() throws JacksonException {

        // given
        var sessions = List.of(ArtifactSession.builder().sessionId(100L).count(3L).build());
        when(artifactService.sessions("frontend-guild")).thenReturn(sessions);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/artifacts/sessions", "frontend-guild");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(sessions));
    }

    @Test
    void stats() throws JacksonException {

        // given
        var stats = LibraryStats.builder().total(10L).sessionCount(4L).capturedThisMonth(2L).build();
        when(artifactService.stats("frontend-guild")).thenReturn(stats);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/artifacts/stats", "frontend-guild");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(stats));
    }

    @Test
    void capture() throws JacksonException {

        // given
        var artifact = ArtifactMother.any().build();
        when(artifactService.capture(eq("frontend-guild"), any())).thenReturn(artifact);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/artifacts", "frontend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"typeId\":1,\"url\":\"https://x.dev/a\"}");

        // then
        actual.assertThat().hasStatus(201);
    }

    @Test
    void capture_rejectsNonHttpUrl() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/artifacts", "frontend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"typeId\":1,\"url\":\"ftp://x.dev/a\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void get() throws JacksonException {

        // given
        var artifact = ArtifactMother.any().id(7L).build();
        when(artifactService.get(7L)).thenReturn(artifact);

        // when
        var actual = mvc.get().uri("/axonia/artifacts/{id}", 7L);

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void edit() throws JacksonException {

        // given
        var artifact = ArtifactMother.any().id(7L).build();
        when(artifactService.edit(eq(7L), any())).thenReturn(artifact);

        // when
        var actual = mvc.put().uri("/axonia/artifacts/{id}", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated\",\"typeId\":1,\"url\":\"https://x.dev/a\"}");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
    }

    @Test
    void remove() {

        // when
        var actual = mvc.delete().uri("/axonia/artifacts/{id}", 7L);

        // then
        actual.assertThat().hasStatus(204);
        verify(artifactService).remove(7L);
    }

    @Test
    void changeContributor() {

        // given
        var artifact = ArtifactMother.any().id(7L).contributorUserId("alice-id").build();
        when(artifactCreditService.changeContributor(eq(7L), eq(new ChangeContributorRequest("alice-id"))))
                .thenReturn(artifact);

        // when
        var actual = mvc.put().uri("/axonia/artifacts/{id}/contributor", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"contributor\":\"alice-id\"}");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.contributorUserId")
                .isEqualTo("alice-id");
    }

    @Test
    void changeContributor_blankContributorRejected() {

        // when
        var actual = mvc.put().uri("/axonia/artifacts/{id}/contributor", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"contributor\":\"  \"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void archive() throws JacksonException {

        // given
        var artifact = ArtifactMother.any().id(7L).build();
        when(artifactService.archive(7L)).thenReturn(artifact);

        // when
        var actual = mvc.post().uri("/axonia/artifacts/{id}/archive", 7L);

        // then
        actual.assertThat().hasStatus2xxSuccessful();
    }

    @Test
    void restore() throws JacksonException {

        // given
        var artifact = ArtifactMother.any().id(7L).build();
        when(artifactService.restore(7L)).thenReturn(artifact);

        // when
        var actual = mvc.post().uri("/axonia/artifacts/{id}/restore", 7L);

        // then
        actual.assertThat().hasStatus2xxSuccessful();
    }
}

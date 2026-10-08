package dev.axonia.knowledge;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.knowledge.mother.ArtifactTypeMother;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ArtifactTypeController.class)
class ArtifactTypeControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ArtifactTypeService artifactTypeService;

    @Test
    void list() throws JacksonException {

        // given
        var type = ArtifactTypeMother.any().id(1L).build();
        when(artifactTypeService.list()).thenReturn(List.of(type));

        // when
        var actual = mvc.get().uri("/axonia/artifact-types");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(List.of(type)));
    }

    @Test
    void create() throws JacksonException {

        // given
        var type = ArtifactTypeMother.any().id(2L).label("Slides").build();
        when(artifactTypeService.create(org.mockito.ArgumentMatchers.any())).thenReturn(type);

        // when
        var actual = mvc.post().uri("/axonia/artifact-types")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"label\":\"Slides\"}");

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.CREATED).bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(type));
    }

    @Test
    void update() throws JacksonException {

        // given
        var type = ArtifactTypeMother.any().id(3L).label("Decisions").icon("file-check").accentColor("#6e3ceb").build();
        when(artifactTypeService.update(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(type);

        // when
        var actual = mvc.put().uri("/axonia/artifact-types/{id}", 3L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Decisions\",\"icon\":\"file-check\",\"accentColor\":\"#6e3ceb\"}");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(type));
    }

    @Test
    void delete() {

        // when
        var actual = mvc.delete().uri("/axonia/artifact-types/{id}", 7L);

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(artifactTypeService, times(1)).delete(7L, null);
    }

    @Test
    void delete_withReassignTo() {

        // when
        var actual = mvc.delete().uri("/axonia/artifact-types/{id}?reassignTo=4", 7L);

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(artifactTypeService, times(1)).delete(7L, 4L);
    }
}

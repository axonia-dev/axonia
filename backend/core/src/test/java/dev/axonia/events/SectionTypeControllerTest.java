package dev.axonia.events;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.mother.SectionTypeMother;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = SectionTypeController.class)
class SectionTypeControllerTest extends ControllerTestSupport {

    @MockitoBean
    private SectionTypeService sectionTypeService;

    @Test
    void list() throws JacksonException {

        // given
        var sectionType = SectionTypeMother.any().id(1L).build();
        when(sectionTypeService.list()).thenReturn(List.of(sectionType));

        // when
        var actual = mvc.get().uri("/axonia/section-types");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(List.of(sectionType)));
    }

    @Test
    void create() throws JacksonException {

        // given
        var sectionType = SectionTypeMother.any().id(2L).label("Round Table").expectsPresenter(false).build();
        when(sectionTypeService.create(any())).thenReturn(sectionType);

        // when
        var actual = mvc.post().uri("/axonia/section-types")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Round Table\",\"expectsPresenter\":false}");

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.CREATED).bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(sectionType));
    }

    @Test
    void update() throws JacksonException {

        // given
        var sectionType = SectionTypeMother.any().id(3L).label("Updated").expectsPresenter(true).build();
        when(sectionTypeService.update(eq(3L), any())).thenReturn(sectionType);

        // when
        var actual = mvc.put().uri("/axonia/section-types/{id}", 3L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Updated\",\"expectsPresenter\":true}");

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.OK).bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(sectionType));
    }

    @Test
    void delete() {

        // when
        var actual = mvc.delete().uri("/axonia/section-types/{id}", 7L);

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(sectionTypeService, times(1)).delete(7L, null);
    }
}

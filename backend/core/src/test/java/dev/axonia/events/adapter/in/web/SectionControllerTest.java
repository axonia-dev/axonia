package dev.axonia.events.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.api.SessionMother;
import dev.axonia.events.application.SectionService;
import dev.axonia.shared.api.error.ConflictException;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = SectionController.class)
class SectionControllerTest extends ControllerTestSupport {

    @MockitoBean
    private SectionService sectionService;

    @Test
    void add_returns201() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(sectionService.addSection(eq(7L), any())).thenReturn(session);

        // when
        var actual = mvc.post().uri("/axonia/sessions/{s}/sections", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionTypeId\":3,\"title\":\"Lightning\",\"durationMinutes\":15}");

        // then
        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void edit_returns200() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(sectionService.updateSection(eq(7L), eq(21L), any())).thenReturn(session);

        // when
        var actual = mvc.put().uri("/axonia/sessions/{s}/sections/{sec}", 7L, 21L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionTypeId\":9,\"title\":\"Renamed\",\"durationMinutes\":25}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void remove_returns200() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(sectionService.removeSection(7L, 21L)).thenReturn(session);

        // when
        var actual = mvc.delete().uri("/axonia/sessions/{s}/sections/{sec}", 7L, 21L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void remove_nonEmptySectionMapsTo409() {

        // given
        when(sectionService.removeSection(7L, 21L)).thenThrow(new ConflictException("SECTION_HAS_SLOTS", "nope"));

        // when
        var actual = mvc.delete().uri("/axonia/sessions/{s}/sections/{sec}", 7L, 21L);

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("SECTION_HAS_SLOTS");
    }

    @Test
    void reorder_returns200() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(sectionService.reorderSections(eq(7L), any())).thenReturn(session);

        // when
        var actual = mvc.put().uri("/axonia/sessions/{s}/sections/order", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionIds\":[22,21]}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }
}

package dev.axonia.expertise.internal;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.NotFoundException;
import dev.axonia.expertise.NamedArea;
import dev.axonia.expertise.NamedAreaCurationService;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = NamedAreaController.class)
class NamedAreaControllerTest extends ControllerTestSupport {

    private static final String NAMED_AREAS = "/axonia/communities/{slug}/expertise/curation/named-areas";

    @MockitoBean
    private NamedAreaCurationService namedAreaCurationService;

    @Test
    void namedAreas_answersTheCommunitysNamedAreas() throws JacksonException {

        // given
        var namedAreas = List.of(new NamedArea(7L, "Messaging", List.of("kafka", "rabbitmq")),
                new NamedArea(8L, "Platform", List.of()));
        when(namedAreaCurationService.namedAreas("eng")).thenReturn(namedAreas);

        // when
        var actual = mvc.get().uri(NAMED_AREAS, "eng").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isEqualTo(jsonMapper.writeValueAsString(namedAreas));
    }

    @Test
    void create_answers201WithTheCreatedNamedArea() throws JacksonException {

        // given
        var created = new NamedArea(7L, "Messaging", List.of());
        when(namedAreaCurationService.createNamedArea("eng", "Messaging")).thenReturn(created);

        // when
        var actual = mvc.post().uri(NAMED_AREAS, "eng").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Messaging\"}").exchange();

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(created));
    }

    @Test
    void create_rejectsAMissingName() {

        // when
        var actual = mvc.post().uri(NAMED_AREAS, "eng").contentType(MediaType.APPLICATION_JSON).content("{}")
                .exchange();

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(namedAreaCurationService);
    }

    @Test
    void rename_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri(NAMED_AREAS + "/{id}/rename", "eng", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Streaming\"}").exchange();

        // then
        actual.assertThat().hasStatus(204);
        verify(namedAreaCurationService).renameNamedArea("eng", 7L, "Streaming");
    }

    @Test
    void rename_rejectsAMissingName() {

        // when
        var actual = mvc.post().uri(NAMED_AREAS + "/{id}/rename", "eng", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{}").exchange();

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(namedAreaCurationService);
    }

    @Test
    void rename_ofAnUnknownNamedAreaIs404() {

        // given
        doThrow(new NotFoundException("No named area 9 in this community")).when(namedAreaCurationService)
                .renameNamedArea("eng", 9L, "Streaming");

        // when
        var actual = mvc.post().uri(NAMED_AREAS + "/{id}/rename", "eng", 9L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Streaming\"}").exchange();

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void delete_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri(NAMED_AREAS + "/{id}/delete", "eng", 7L).exchange();

        // then
        actual.assertThat().hasStatus(204);
        verify(namedAreaCurationService).deleteNamedArea("eng", 7L);
    }

    @Test
    void place_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri(NAMED_AREAS + "/{id}/tags", "eng", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\":\"kafka\"}").exchange();

        // then
        actual.assertThat().hasStatus(204);
        verify(namedAreaCurationService).placeInNamedArea("eng", 7L, "kafka");
    }

    @Test
    void place_rejectsABlankTag() {

        // when
        var actual = mvc.post().uri(NAMED_AREAS + "/{id}/tags", "eng", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\":\"\"}").exchange();

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(namedAreaCurationService);
    }

    @Test
    void remove_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri(NAMED_AREAS + "/tags/{tag}/remove", "eng", "kafka").exchange();

        // then
        actual.assertThat().hasStatus(204);
        verify(namedAreaCurationService).removeFromNamedArea("eng", "kafka");
    }
}

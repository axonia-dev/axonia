package dev.axonia.events.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.application.SeriesService;
import dev.axonia.events.domain.SeriesMother;
import dev.axonia.events.domain.SeriesStatus;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = SeriesController.class)
class SeriesControllerTest extends ControllerTestSupport {

    @MockitoBean
    private SeriesService seriesService;

    @Test
    void start_returns201() throws JacksonException {

        // given
        var series = SeriesMother.any().build();
        when(seriesService.start(eq("backend-guild"), any())).thenReturn(series);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/series", "backend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Frontend Dojo\",\"cadence\":{\"count\":1,\"unit\":\"WEEK\"},"
                        + "\"startsOn\":\"2026-06-03\"," + "\"preferredTime\":\"18:00:00\",\"timeZone\":\"UTC\","
                        + "\"durationMinutes\":60,"
                        + "\"defaultStructure\":[{\"sectionTypeId\":1,\"title\":\"Main talk\",\"durationMinutes\":30}]}");

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(series));
    }

    @Test
    void start_blankTitleIsRejected() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/series", "backend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(
                        "{\"title\":\"\",\"cadence\":{\"count\":1,\"unit\":\"WEEK\"},\"startsOn\":\"2026-06-03\",\"timeZone\":\"UTC\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void start_rejectsCadenceCountBelowOne() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/series", "backend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(
                        "{\"title\":\"Dojo\",\"cadence\":{\"count\":0,\"unit\":\"WEEK\"},\"startsOn\":\"2026-06-03\",\"timeZone\":\"UTC\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void start_rejectsCadenceCountAbove999() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/series", "backend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(
                        "{\"title\":\"Dojo\",\"cadence\":{\"count\":1000,\"unit\":\"WEEK\"},\"startsOn\":\"2026-06-03\",\"timeZone\":\"UTC\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void start_rejectsMissingTimeZone() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/series", "backend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(
                        "{\"title\":\"Dojo\",\"cadence\":{\"count\":1,\"unit\":\"WEEK\"},\"startsOn\":\"2026-06-03\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void start_rejectsUnknownTimeZone() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/series", "backend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Dojo\",\"cadence\":{\"count\":1,\"unit\":\"WEEK\"},\"startsOn\":\"2026-06-03\","
                        + "\"timeZone\":\"Not/AZone\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    /**
     * A standing place is inherited by every occurrence, so a script scheme accepted here would reach a whole series.
     */
    @Test
    void start_rejectsAScriptSchemeAsTheStandingJoinUrl() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/series", "backend-guild")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Dojo\",\"cadence\":{\"count\":1,\"unit\":\"WEEK\"},\"startsOn\":\"2026-06-03\","
                        + "\"timeZone\":\"UTC\",\"place\":{\"joinUrl\":\"javascript:alert(1)\"}}");

        // then
        actual.assertThat().hasStatus(400);
        verify(seriesService, never()).start(any(), any());
    }

    @Test
    void update_rejectsAScriptSchemeAsTheStandingJoinUrl() {

        // when
        var actual = mvc.put().uri("/axonia/series/{id}", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Dojo\",\"cadence\":{\"count\":1,\"unit\":\"WEEK\"},\"startsOn\":\"2026-06-03\","
                        + "\"timeZone\":\"UTC\",\"place\":{\"joinUrl\":\"javascript:alert(1)\"}}");

        // then
        actual.assertThat().hasStatus(400);
        verify(seriesService, never()).update(any(), any());
    }

    @Test
    void list_returnsSeriesPage() throws JacksonException {

        // given
        when(seriesService.list(eq("backend-guild"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(SeriesMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/series", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    @Test
    void list_unsupportedSortIsRejected() {

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/series", "backend-guild").queryParam("sort",
                "title,desc");

        // then
        actual.assertThat().hasStatus4xxClientError();
    }

    @Test
    void get_returnsSeries() throws JacksonException {

        // given
        when(seriesService.get(1L)).thenReturn(SeriesMother.any().build());

        // when
        var actual = mvc.get().uri("/axonia/series/{id}", 1L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(1);
    }

    @Test
    void update_returnsSeries() throws JacksonException {

        // given
        when(seriesService.update(eq(1L), any())).thenReturn(SeriesMother.any().title("Renamed").build());

        // when
        var actual = mvc.put().uri("/axonia/series/{id}", 1L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Renamed\",\"cadence\":{\"count\":1,\"unit\":\"MONTH\"},"
                        + "\"startsOn\":\"2026-06-03\"," + "\"timeZone\":\"UTC\"," + "\"durationMinutes\":90,"
                        + "\"defaultStructure\":[{\"sectionTypeId\":1,\"title\":\"Main talk\",\"durationMinutes\":30}]}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.title").isEqualTo("Renamed");
    }

    @Test
    void pause_returnsUpdatedSeries() {

        // given
        when(seriesService.pause(1L)).thenReturn(SeriesMother.any().status(SeriesStatus.PAUSED).build());

        // when
        var actual = mvc.post().uri("/axonia/series/{id}/pause", 1L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.status").isEqualTo("PAUSED");
    }

    @Test
    void resume_returnsUpdatedSeries() {

        // given
        when(seriesService.resume(1L)).thenReturn(SeriesMother.any().status(SeriesStatus.ACTIVE).build());

        // when
        var actual = mvc.post().uri("/axonia/series/{id}/resume", 1L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void end_returnsUpdatedSeries() {

        // given
        when(seriesService.end(1L)).thenReturn(SeriesMother.any().status(SeriesStatus.ENDED).build());

        // when
        var actual = mvc.post().uri("/axonia/series/{id}/end", 1L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.status").isEqualTo("ENDED");
    }
}

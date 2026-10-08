package dev.axonia.events;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.mother.SessionMother;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = SessionController.class)
class SessionControllerTest extends ControllerTestSupport {

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SessionReleaseService sessionReleaseService;

    @MockitoBean
    private OccurrenceSkipService occurrenceSkipService;

    @MockitoBean
    private ScheduleService scheduleService;

    /**
     * A schedule as the wire now carries one: a civil date, a wall-clock time and the zone it is read in. The server
     * composes the instant, so a request body no longer holds one at all.
     */
    private static final String AT = "{\"date\":\"2099-12-20\",\"time\":\"17:00:00\",\"zone\":\"UTC\"}";

    @Test
    void materialize() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(sessionService.materialize(5L)).thenReturn(session);

        // when
        var actual = mvc.post().uri("/axonia/series/{id}/sessions", 5L);

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.CREATED).bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void createOneOff() throws JacksonException {

        var session = SessionMother.any().seriesId(null).occurrenceDate(null).build();
        when(sessionService.createOneOff(eq("frontend"), any())).thenReturn(session);

        var actual = mvc.post().uri("/axonia/communities/{slug}/sessions", "frontend")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Lightning\",\"scheduledAt\":" + AT + ",\"durationMinutes\":60}");

        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.CREATED);
    }

    @Test
    void ensure() throws JacksonException {

        var session = SessionMother.any().build();
        when(sessionService.ensure(eq(5L), any())).thenReturn(session);

        var actual = mvc.post().uri("/axonia/series/{id}/occurrences", 5L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"occurrenceDate\":\"2099-12-12\"}");

        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.CREATED).bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void skip() throws JacksonException {

        var session = SessionMother.any().build();
        when(occurrenceSkipService.skip(eq(5L), any())).thenReturn(session);

        var actual = mvc.post().uri("/axonia/series/{id}/occurrences/skip", 5L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"occurrenceDate\":\"2099-12-12\"}");

        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void unskip() throws JacksonException {

        var session = SessionMother.any().build();
        when(occurrenceSkipService.unskip(eq(5L), any())).thenReturn(session);

        var actual = mvc.post().uri("/axonia/series/{id}/occurrences/unskip", 5L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"occurrenceDate\":\"2099-12-12\"}");

        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void get() throws JacksonException {

        // given
        var session = SessionMother.any().id(7L).build();
        when(sessionService.getDetail(7L)).thenReturn(session);

        // when
        var actual = mvc.get().uri("/axonia/sessions/{id}", 7L);

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void upcoming() throws JacksonException {

        // given
        when(sessionService.upcoming(eq("frontend"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(SessionMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/sessions", "frontend");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    @Test
    void past() throws JacksonException {

        // given
        when(sessionService.past(argThat(request -> "frontend".equals(request.slug()) && request.from() == null
                && request.to() == null && request.status() == null)))
                .thenReturn(new PageImpl<>(List.of(SessionMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/sessions/past", "frontend");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    @Test
    void past_passesWindowThrough() throws JacksonException {

        // given
        when(sessionService.past(argThat(request -> Instant.parse("2026-04-01T00:00:00Z").equals(request.from())
                && Instant.parse("2026-07-01T00:00:00Z").equals(request.to())))).thenReturn(new PageImpl<>(List.of()));

        // when
        var actual = mvc.get().uri(
                "/axonia/communities/{slug}/sessions/past?from=2026-04-01T00:00:00Z&to=2026-07-01T00:00:00Z",
                "frontend");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.page.totalElements").isEqualTo(0);
    }

    @Test
    void past_passesStatusThrough() throws JacksonException {

        // given
        when(sessionService.past(argThat(request -> request.status() == SessionStatus.HELD)))
                .thenReturn(new PageImpl<>(List.of(SessionMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/sessions/past?status=HELD", "frontend");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void update() throws JacksonException {

        // given
        when(sessionService.update(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":" + AT + ",\"durationMinutes\":90}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    /**
     * The join link reaches a member as an {@code href} and an external calendar as a clickable {@code CONFERENCE} URI,
     * so a script scheme has to be refused rather than stored and stripped later. The refusal is a 400 and the service
     * is never reached: nothing is written, and the organizer is told, instead of quietly getting a link nobody can
     * follow.
     */
    @Test
    void update_refusesAScriptSchemeAsTheJoinUrl() {

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":" + AT + ",\"place\":{\"joinUrl\":\"javascript:alert(1)\"}}");

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        verify(sessionService, never()).update(any(), any());
    }

    @Test
    void update_acceptsAnHttpsJoinUrl() throws JacksonException {

        // given
        when(sessionService.update(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":" + AT + ",\"place\":{\"joinUrl\":\"https://meet.example/dojo\"}}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    /**
     * The zone travels as free text and is read back by {@code ZoneId.of} on every projection, so one this machine
     * cannot resolve has to be refused at the edge. Stored instead, it would not fail here but on the next schedule
     * read of a session nobody could then repair through the API.
     */
    @Test
    void update_refusesAZoneThatIsNotARealTimeZone() {

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(
                        "{\"scheduledAt\":{\"date\":\"2099-12-20\",\"time\":\"17:00:00\",\"zone\":\"Mars/Olympus_Mons\"}}");

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        verify(sessionService, never()).update(any(), any());
    }

    /**
     * The companion refusal, and the reason {@code zone} carries {@code @NotBlank} beside {@code @ValidTimeZone}: the
     * validator passes a blank value through, because a blank is absence rather than a bad zone, so without this the
     * server would be left composing a wall clock with no zone to read it in.
     */
    @Test
    void update_refusesAScheduleThatNamesNoZoneAtAll() {

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":{\"date\":\"2099-12-20\",\"time\":\"17:00:00\",\"zone\":\"\"}}");

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        verify(sessionService, never()).update(any(), any());
    }

    @Test
    void createOneOff_refusesAScriptSchemeAsTheJoinUrl() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/sessions", "frontend")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Lightning\",\"scheduledAt\":" + AT + ","
                        + "\"place\":{\"joinUrl\":\"javascript:alert(1)\"}}");

        // then
        actual.assertThat().hasStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        verify(sessionService, never()).createOneOff(any(), any());
    }

    @Test
    void cancel() throws JacksonException {

        // given
        when(sessionService.cancel(7L)).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/cancel", 7L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void followUp() throws JacksonException {

        // given
        when(sessionService.recordFollowUp(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/follow-up", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"done\",\"recordingUrl\":\"\"}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void followUpWithDecisions() throws JacksonException {

        // given
        when(sessionService.recordFollowUp(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/follow-up", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"decisions\":[{\"actionItemId\":5,\"outcome\":\"CARRIED\"}]}");

        // then
        actual.assertThat().hasStatus(200);
        verify(sessionService).recordFollowUp(eq(7L),
                argThat(r -> r.decisions().equals(List.of(new CheckpointDecision(5L, CheckpointOutcome.CARRIED)))));
    }

    @Test
    void followUpWithADecisionMissingItsOutcomeIsRejected() {

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/follow-up", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"decisions\":[{\"actionItemId\":5}]}");

        // then - without the element-level @Valid this reaches the lifecycle's switch and 500s there
        actual.assertThat().hasStatus(400);
    }

    @Test
    void close() throws JacksonException {

        // given
        when(sessionService.close(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/close", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"held\":true}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void close_missingHeldRejected() {

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/close", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void wrapUpRelease() throws JacksonException {

        // given
        when(sessionReleaseService.setWrapUpRelease(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}/wrap-up-release", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"releaseAt\":\"2026-03-13T08:00:00Z\"}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    /**
     * A null {@code releaseAt} is the permanent-suppression answer, not a missing value - unlike
     * {@code CloseSessionRequest.held}, this field must stay unvalidated so the endpoint keeps accepting it.
     */
    @Test
    void wrapUpRelease_nullReleaseAtSucceeds() throws JacksonException {

        // given
        when(sessionReleaseService.setWrapUpRelease(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}/wrap-up-release", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"releaseAt\":null}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void upcomingRelease() throws JacksonException {

        // given
        when(sessionReleaseService.setUpcomingRelease(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}/upcoming-release", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"releaseAt\":\"2026-03-13T08:00:00Z\"}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    /**
     * The three answers the release card offers all have to reach the service: "send it now" as a flag the server
     * answers with its own clock, and the empty body as "no reminder for this session" rather than a missing value.
     */
    @Test
    void upcomingRelease_sendNowAndSuppressionBothReachTheService() throws JacksonException {

        // given
        when(sessionReleaseService.setUpcomingRelease(eq(7L), any())).thenReturn(SessionMother.any().id(7L).build());

        // when
        mvc.put().uri("/axonia/sessions/{id}/upcoming-release", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"sendNow\":true}")
                .assertThat().hasStatus(200);
        mvc.put().uri("/axonia/sessions/{id}/upcoming-release", 7L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"releaseAt\":null}")
                .assertThat().hasStatus(200);

        // then
        verify(sessionReleaseService).setUpcomingRelease(7L, new UpcomingReleaseRequest(true, null));
        verify(sessionReleaseService).setUpcomingRelease(7L, new UpcomingReleaseRequest(false, null));
    }

    @Test
    void messagePreview() throws JacksonException {

        // given
        var preview = new MessagePreview("Frontend Dojo: what happened", "<html>preview</html>");
        when(sessionReleaseService.preview("frontend", 7L, "session_wrap_up")).thenReturn(preview);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/sessions/{id}/message-preview?type=session_wrap_up",
                "frontend", 7L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(preview));
    }

    /**
     * One endpoint, the type as a parameter. The wire code reaches the service verbatim rather than being interpreted
     * at the edge, which is what keeps the refusal of an unsupported one in a single place.
     */
    @Test
    void messagePreview_carriesTheRequestedMessageTypeThrough() throws JacksonException {

        // given
        when(sessionReleaseService.preview("frontend", 7L, "session_upcoming"))
                .thenReturn(new MessagePreview("Frontend Dojo is coming up", "<html>reminder</html>"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/sessions/{id}/message-preview?type=session_upcoming",
                "frontend", 7L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.subject")
                .isEqualTo("Frontend Dojo is coming up");
        verify(sessionReleaseService).preview("frontend", 7L, "session_upcoming");
    }

    /**
     * The type is required, not defaulted. An omitted one has to fail loudly rather than fall back to whichever message
     * the endpoint used to serve when it had the wrap-up's name in its path.
     */
    @Test
    void messagePreview_withoutATypeIsRejected() {

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/sessions/{id}/message-preview", "frontend", 7L);

        // then
        actual.assertThat().hasStatus(400);
        verify(sessionReleaseService, never()).preview(any(), any(), any());
    }

    @Test
    void schedule() {

        // given
        var entry = ScheduleEntry.builder().seriesId(10L).occurrenceDate(java.time.LocalDate.parse("2026-06-17"))
                .scheduledAt(Instant.parse("2026-06-17T18:00:00Z")).hasTime(true).seriesTimeZone("Europe/Paris")
                .durationMinutes(60).status(SessionStatus.SCHEDULED).virtual(true).sections(List.of()).build();
        when(scheduleService.schedule(eq("frontend"), eq(Instant.parse("2026-06-14T00:00:00Z")),
                eq(Instant.parse("2026-07-14T00:00:00Z")))).thenReturn(List.of(entry));

        // when
        var actual = mvc.get().uri(
                "/axonia/communities/{slug}/schedule?from=2026-06-14T00:00:00Z&to=2026-07-14T00:00:00Z", "frontend");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$[0].virtual").isEqualTo(true);
        actual.assertThat().bodyJson().extractingPath("$[0].occurrenceDate").isEqualTo("2026-06-17");
        actual.assertThat().bodyJson().extractingPath("$[0].status").isEqualTo("SCHEDULED");
        // The zone an editor opened from the calendar composes its hour in. It has to reach the wire: a reader that
        // did not get it would fall back to the viewer's own clock and move the occurrence by the offset between them.
        actual.assertThat().bodyJson().extractingPath("$[0].seriesTimeZone").isEqualTo("Europe/Paris");
        actual.assertThat().bodyJson().extractingPath("$[0].sections").isEqualTo(List.of());
    }

    @Test
    void scheduleActivity_returnsActiveMonths() {

        // given
        when(scheduleService.activeMonths("backend-guild")).thenReturn(List.of("2024-06", "2026-07"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/schedule/activity", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$[0]").isEqualTo("2024-06");
        actual.assertThat().bodyJson().extractingPath("$[1]").isEqualTo("2026-07");
    }
}

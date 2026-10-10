package dev.axonia.events.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.api.PresenterAssignment;
import dev.axonia.events.api.PresenterResolution;
import dev.axonia.events.api.RecordedSlot;
import dev.axonia.events.api.SessionMother;
import dev.axonia.events.application.SlotService;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = SlotController.class)
class SlotControllerTest extends ControllerTestSupport {

    @MockitoBean
    private SlotService slotService;

    @Test
    void add() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(slotService.addSlot(eq(7L), eq(21L), any())).thenReturn(session);

        // when
        var actual = mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots", 7L, 21L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Open floor\",\"durationMinutes\":15}");

        // then
        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void signUp() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(slotService.signUp(7L, 21L, 11L)).thenReturn(session);

        // when
        var actual = mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots/{slot}/presenters/me", 7L, 21L, 11L);

        // then
        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void withdraw() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(slotService.withdraw(7L, 21L, 11L)).thenReturn(session);

        // when
        var actual = mvc.delete().uri("/axonia/sessions/{s}/sections/{sec}/slots/{slot}/presenters/me", 7L, 21L, 11L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void edit() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(slotService.editSlot(eq(7L), eq(21L), eq(11L), any())).thenReturn(session);

        // when
        var actual = mvc.put().uri("/axonia/sessions/{s}/sections/{sec}/slots/{slot}", 7L, 21L, 11L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Renamed\"}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void remove() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(slotService.removeSlot(7L, 21L, 11L)).thenReturn(session);

        // when
        var actual = mvc.delete().uri("/axonia/sessions/{s}/sections/{sec}/slots/{slot}", 7L, 21L, 11L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void removePresenter() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(slotService.removePresenter(7L, 21L, 11L, "bob")).thenReturn(session);

        // when
        var actual = mvc.delete().uri("/axonia/sessions/{s}/sections/{sec}/slots/{slot}/presenters/{u}", 7L, 21L, 11L,
                "bob");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void add_rejectsNonPositiveDuration() {

        mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots", 7L, 21L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"durationMinutes\":0}").assertThat().hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void add_rejectsMissingDuration() {

        mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots", 7L, 21L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}").assertThat().hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void fromBacklog_returns201() throws JacksonException {

        var session = SessionMother.any().build();
        when(slotService.queueTopic(eq(7L), eq(21L), any())).thenReturn(session);

        var actual = mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots/from-backlog", 7L, 21L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"topicId\":99,\"durationMinutes\":20}");

        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void fromBacklog_rejectsMissingTopicId() {

        var actual = mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots/from-backlog", 7L, 21L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\":20}");

        actual.assertThat().hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reorder() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        when(slotService.reorderSlots(eq(7L), eq(21L), any())).thenReturn(session);

        // when
        var actual = mvc.put().uri("/axonia/sessions/{s}/sections/{sec}/slots/order", 7L, 21L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slotIds\":[102,101]}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(session));
    }

    @Test
    void recordedSlot_returns201_andReportsWhichPresentersPlaced() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        var presenters = PresenterResolution.builder().assigned(List.of("Alice Dupont")).unmatched(List.of("J. Smith"))
                .ambiguous(List.of()).build();
        var recorded = new RecordedSlot(session, 9L, presenters);
        when(slotService.recordSlot(eq(7L), eq(21L), any())).thenReturn(recorded);

        // when
        var actual = mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots/recorded", 7L, 21L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Kafka 101\",\"durationMinutes\":15,\"presenters\":[\"Alice Dupont\","
                        + "\"J. Smith\"]}");

        // then
        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson().isEqualTo(jsonMapper.writeValueAsString(recorded));
    }

    @Test
    void addPresenter_returns201_andSaysWhenTheNameCouldNotBePlaced() throws JacksonException {

        // given
        var session = SessionMother.any().build();
        var assignment = new PresenterAssignment(session, false, PresenterAssignment.NOT_FOUND, 0);
        when(slotService.addPresenter(7L, 21L, 11L, "J. Smith")).thenReturn(assignment);

        // when
        var actual = mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots/{slot}/presenters", 7L, 21L, 11L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"presenter\":\"J. Smith\"}");

        // then
        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(assignment));
    }

    @Test
    void addPresenter_putsTheAmbiguityCountOnTheWire() {

        // given - two members answer to "Bob"
        var session = SessionMother.any().build();
        when(slotService.addPresenter(7L, 21L, 11L, "Bob"))
                .thenReturn(new PresenterAssignment(session, false, PresenterAssignment.AMBIGUOUS, 2));

        // when
        var actual = mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots/{slot}/presenters", 7L, 21L, 11L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"presenter\":\"Bob\"}");

        // then - a caller told only "AMBIGUOUS" cannot tell two namesakes from a query that was simply too vague
        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.candidates").isEqualTo(2);
    }

    @Test
    void addPresenter_rejectsABlankReference() {

        var actual = mvc.post().uri("/axonia/sessions/{s}/sections/{sec}/slots/{slot}/presenters", 7L, 21L, 11L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"presenter\":\"  \"}");

        actual.assertThat().hasStatus(HttpStatus.BAD_REQUEST);
    }
}

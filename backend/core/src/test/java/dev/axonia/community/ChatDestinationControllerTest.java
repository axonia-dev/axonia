package dev.axonia.community;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.communication.ChatFailureKind;
import dev.axonia.communication.ChatPlatformName;
import dev.axonia.communication.ChatSendFailedException;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ChatDestinationController.class)
class ChatDestinationControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ChatDestinationService chatDestinationService;

    private static ChatDestination sample() {

        return new ChatDestination(1L, "#guild", ChatPlatformName.SLACK, "hooks.slack.com/...abcd", false, null, null,
                null);
    }

    @Test
    void list() throws JacksonException {

        // given
        var destinations = List.of(sample());
        when(chatDestinationService.list("backend-guild")).thenReturn(destinations);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/chat-destinations", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(destinations));
    }

    @Test
    void create() throws JacksonException {

        // given
        var request = new CreateChatDestinationRequest("#guild", ChatPlatformName.SLACK,
                "https://hooks.slack.com/services/T/B/abcd");
        var created = sample();
        when(chatDestinationService.create("backend-guild", request)).thenReturn(created);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/chat-destinations", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(created));
    }

    @Test
    void create_blankNameRejected() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/chat-destinations", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"","webhookUrl":"https://hooks.slack.com/services/x"}""");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void create_limitExceededRejected() throws JacksonException {

        // given
        var request = new CreateChatDestinationRequest("#eleventh", ChatPlatformName.SLACK,
                "https://hooks.slack.com/services/x");
        when(chatDestinationService.create("backend-guild", request)).thenThrow(new ValidationException(
                "CHAT_DESTINATION_LIMIT", "A community can post to at most ten chat destinations. Remove one first."));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/chat-destinations", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("CHAT_DESTINATION_LIMIT");
    }

    @Test
    void create_nonOrganizerForbidden() throws JacksonException {

        // given
        var request = new CreateChatDestinationRequest("#guild", ChatPlatformName.SLACK,
                "https://hooks.slack.com/services/x");
        when(chatDestinationService.create("backend-guild", request))
                .thenThrow(new ForbiddenException("Organizer role required"));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/chat-destinations", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void update() throws JacksonException {

        // given
        var request = new UpdateChatDestinationRequest("#renamed", null, null);
        var updated = sample();
        when(chatDestinationService.update("backend-guild", 1L, request)).thenReturn(updated);

        // when
        var actual = mvc.patch().uri("/axonia/communities/{slug}/chat-destinations/{id}", "backend-guild", 1L)
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(updated));
    }

    @Test
    void update_unknownDestinationNotFound() throws JacksonException {

        // given
        var request = new UpdateChatDestinationRequest("#renamed", null, null);
        when(chatDestinationService.update("backend-guild", 99L, request))
                .thenThrow(new NotFoundException("No such chat destination."));

        // when
        var actual = mvc.patch().uri("/axonia/communities/{slug}/chat-destinations/{id}", "backend-guild", 99L)
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(request));

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void remove() {

        // when
        var actual = mvc.delete().uri("/axonia/communities/{slug}/chat-destinations/{id}", "backend-guild", 1L);

        // then
        actual.assertThat().hasStatus(204);
        verify(chatDestinationService).remove("backend-guild", 1L);
    }

    @Test
    void test() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/chat-destinations/{id}/test", "backend-guild", 1L);

        // then
        actual.assertThat().hasStatus(204);
        verify(chatDestinationService).test("backend-guild", 1L);
    }

    /**
     * A dead webhook is the single most likely outcome of clicking "test": a third party refused or timed out, not
     * axonia breaking, so it must read as 502 with a code the frontend can map - never Spring's default 500. Proves
     * {@code CommunityExceptionHandler}'s advice actually reaches this controller, not only
     * {@code CommunityController}.
     */
    @Test
    void test_chatSendFailureReturns502WithACode() throws JacksonException {

        // given
        doThrow(new ChatSendFailedException(ChatFailureKind.GONE, "404 from hooks.slack.com"))
                .when(chatDestinationService).test("backend-guild", 1L);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/chat-destinations/{id}/test", "backend-guild", 1L);

        // then
        actual.assertThat().hasStatus(502).bodyJson().extractingPath("$.code").isEqualTo("CHAT_SEND_FAILED");
    }
}

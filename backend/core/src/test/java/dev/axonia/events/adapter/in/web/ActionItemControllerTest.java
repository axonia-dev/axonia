package dev.axonia.events.adapter.in.web;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.api.ActionItemMother;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.application.ActionItemQuery;
import dev.axonia.events.application.ActionItemService;
import dev.axonia.events.application.UpdateActionItemRequest;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ActionItemController.class)
class ActionItemControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ActionItemService actionItemService;

    @Test
    void raise_returns201() throws JacksonException {

        // given
        var item = ActionItemMother.any().build();
        when(actionItemService.raise(eq("backend-guild"), any())).thenReturn(item);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/action-items", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Audit the CI pipeline\"}");

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(item));
    }

    @Test
    void raise_blankTitleReturns400() {

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/action-items", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void list_filtersByOutcome() throws JacksonException {

        // given
        when(actionItemService.list(eq("backend-guild"), eq(new ActionItemQuery(ActionItemOutcome.OPEN, null, null)),
                any(Pageable.class))).thenReturn(new PageImpl<>(java.util.List.of(ActionItemMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/action-items?outcome=OPEN", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void list_bindsTheOriginAndCheckpointSessionFilters() throws JacksonException {

        // given
        when(actionItemService.list(eq("backend-guild"), eq(new ActionItemQuery(null, 7L, 9L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(ActionItemMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/action-items?originSessionId=7&checkpointSessionId=9",
                "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
    }

    /**
     * The Past tab reads settled items in settle order, so the key it asks for has to reach the service intact: a sort
     * the controller silently drops would truncate the page on one column and display it on another.
     */
    @Test
    void list_acceptsTheSettledSort() {

        // given
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        when(actionItemService.list(eq("backend-guild"), any(), pageable.capture()))
                .thenReturn(new PageImpl<>(java.util.List.of(ActionItemMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/action-items", "backend-guild")
                .queryParam("sort", "updated_at,desc").exchange();

        // then
        assertThat(actual).hasStatus(200);
        assertThat(pageable.getValue().getSort().getOrderFor("updated_at")).isNotNull().returns(Sort.Direction.DESC,
                Sort.Order::getDirection);
    }

    @Test
    void list_unsupportedSortIsRejected() {

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/action-items", "backend-guild").queryParam("sort",
                "title,desc");

        // then
        actual.assertThat().hasStatus4xxClientError();
    }

    @Test
    void update_returns200() throws JacksonException {

        // given
        var item = ActionItemMother.any().title("Updated").build();
        when(actionItemService.update(eq(1L), any())).thenReturn(item);

        // when
        var actual = mvc.put().uri("/axonia/action-items/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated\",\"tags\":[\"kafka\",\"ci\"],\"dueDate\":\"2026-10-01\","
                        + "\"checkpointSessionId\":42}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(item));
        var captor = ArgumentCaptor.forClass(UpdateActionItemRequest.class);
        verify(actionItemService).update(eq(1L), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Updated");
        assertThat(captor.getValue().tags()).containsExactly("kafka", "ci");
        assertThat(captor.getValue().dueDate()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(captor.getValue().checkpointSessionId()).isEqualTo(42L);
    }

    @Test
    void update_blankTitleReturns400() {

        // when
        var actual = mvc.put().uri("/axonia/action-items/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void update_unknownItemReturns404() {

        // given
        when(actionItemService.update(eq(999L), any())).thenThrow(new NotFoundException("Action item not found: 999"));

        // when
        var actual = mvc.put().uri("/axonia/action-items/{id}", 999L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated\"}");

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void remove_returns204() {

        // when
        var actual = mvc.delete().uri("/axonia/action-items/{id}", 1L);

        // then
        actual.assertThat().hasStatus(204);
        verify(actionItemService).remove(1L);
    }

    @Test
    void close_returns200() throws JacksonException {

        // given
        var item = ActionItemMother.any().outcome(ActionItemOutcome.DONE).build();
        when(actionItemService.close(1L)).thenReturn(item);

        // when
        var actual = mvc.post().uri("/axonia/action-items/{id}/close", 1L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(item));
    }

    @Test
    void close_conflictReturns409WithCode() {

        // given
        when(actionItemService.close(1L)).thenThrow(new ConflictException("ALREADY_CLOSED", "dup"));

        // when
        var actual = mvc.post().uri("/axonia/action-items/{id}/close", 1L);

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ALREADY_CLOSED");
    }

    @Test
    void drop_returns200() throws JacksonException {

        // given
        var item = ActionItemMother.any().outcome(ActionItemOutcome.DROPPED).build();
        when(actionItemService.drop(1L)).thenReturn(item);

        // when
        var actual = mvc.post().uri("/axonia/action-items/{id}/drop", 1L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(item));
    }

    @Test
    void reopen_returns200() throws JacksonException {

        // given
        var item = ActionItemMother.any().outcome(ActionItemOutcome.OPEN).build();
        when(actionItemService.reopen(1L)).thenReturn(item);

        // when
        var actual = mvc.post().uri("/axonia/action-items/{id}/reopen", 1L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(item));
    }
}

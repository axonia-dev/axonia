package dev.axonia.events.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicMother;
import dev.axonia.events.api.TopicQuery;
import dev.axonia.events.application.TopicService;
import dev.axonia.events.domain.TopicCounts;
import dev.axonia.shared.api.error.ConflictException;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = TopicController.class)
class TopicControllerTest extends ControllerTestSupport {

    @MockitoBean
    private TopicService topicService;

    @Test
    void propose_returns201() throws JacksonException {

        // given
        var topic = TopicMother.any().build();
        when(topicService.propose(eq("backend-guild"), any())).thenReturn(topic);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/topics", "backend-guild")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"REQUEST\",\"title\":\"ES intro\",\"description\":\"please\"}");

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(topic));
    }

    @Test
    void vote_duplicateReturns409WithCode() {

        // given
        when(topicService.vote(1L)).thenThrow(new ConflictException("ALREADY_VOTED", "dup"));

        // when
        var actual = mvc.post().uri("/axonia/topics/{id}/votes", 1L);

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ALREADY_VOTED");
    }

    @Test
    void unvote_returns204() {

        // when
        var actual = mvc.delete().uri("/axonia/topics/{id}/votes/me", 1L);

        // then
        actual.assertThat().hasStatus(204);
    }

    @Test
    void list_filtersByKindAndStatus() throws JacksonException {

        // given
        when(topicService.list(eq("backend-guild"), eq(TopicQuery.builder().kind(TopicKind.OFFER).build()),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(TopicMother.any().kind(TopicKind.OFFER).build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/topics?kind=OFFER", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    @Test
    void list_phasePastDelegatesToListPast() throws JacksonException {

        // given
        when(topicService.listPast(eq("backend-guild"), eq("kafka"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(TopicMother.any().build())));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/topics?phase=PAST&q=kafka", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void list_unsupportedSortIsRejected() {

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/topics", "backend-guild").queryParam("sort",
                "title,desc");

        // then
        actual.assertThat().hasStatus4xxClientError();
    }

    @Test
    void get_returns200() throws JacksonException {

        // given
        var topic = TopicMother.any().build();
        when(topicService.get(1L)).thenReturn(topic);

        // when
        var actual = mvc.get().uri("/axonia/topics/{id}", 1L);

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(topic));
    }

    @Test
    void update_returns200() throws JacksonException {

        // given
        var topic = TopicMother.any().title("Updated").build();
        when(topicService.update(eq(1L), any())).thenReturn(topic);

        // when
        var actual = mvc.put().uri("/axonia/topics/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"REQUEST\",\"title\":\"Updated\",\"description\":\"desc\"}");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(topic));
    }

    @Test
    void remove_returns204() {

        // when
        var actual = mvc.delete().uri("/axonia/topics/{id}", 1L);

        // then
        actual.assertThat().hasStatus(204);
    }

    @Test
    void returnToBacklog_returns204() {

        // when
        var actual = mvc.delete().uri("/axonia/topics/{id}/schedule", 1L);

        // then
        actual.assertThat().hasStatus(204);
    }

    @Test
    void vote_returns201() throws JacksonException {

        // given
        var topic = TopicMother.any().voteCount(1L).build();
        when(topicService.vote(1L)).thenReturn(topic);

        // when
        var actual = mvc.post().uri("/axonia/topics/{id}/votes", 1L);

        // then
        actual.assertThat().hasStatus(201).bodyJson().extractingPath("$.voteCount").isEqualTo(1);
    }

    @Test
    void counts_returnsLifecycleBuckets() {

        // given
        when(topicService.counts("backend-guild"))
                .thenReturn(TopicCounts.builder().proposed(3).scheduled(1).done(7).build());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/topics/counts", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.proposed").isEqualTo(3);
        actual.assertThat().bodyJson().extractingPath("$.scheduled").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.done").isEqualTo(7);
    }
}

package dev.axonia.recognition.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.ImpactReplayService;
import dev.axonia.recognition.application.LoveSummary;
import dev.axonia.recognition.application.MarkSeenRequest;
import dev.axonia.recognition.application.NewLoveItem;
import dev.axonia.recognition.application.NewLoves;
import dev.axonia.recognition.application.RecognitionService;
import dev.axonia.recognition.application.RecognizedContribution;
import dev.axonia.recognition.domain.Love;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = RecognitionController.class)
class RecognitionControllerTest extends ControllerTestSupport {

    @MockitoBean
    private RecognitionService recognitionService;

    @MockitoBean
    private ImpactReplayService impactReplayService;

    @Test
    void love() {

        // given
        when(recognitionService.love(any()))
                .thenReturn(Love.builder().id(1L).contributionType(ContributionType.SLOT_PRESENTED).contributionId(100L)
                        .communityId(1L).loverUserId("dev-user").createdAt(Instant.now()).build());

        // when
        var actual = mvc.post().uri("/axonia/recognition/loves").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contributionType\":\"SLOT_PRESENTED\",\"contributionId\":100}");

        // then
        actual.assertThat().hasStatus(201);
    }

    @Test
    void love_invalidRequestIs400() {

        // when
        var actual = mvc.post().uri("/axonia/recognition/loves").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contributionId\":100}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void love_topicIsNotLoveable() {

        // when a TOPIC_PROPOSED love arrives, the retired enum value fails to deserialize
        var actual = mvc.post().uri("/axonia/recognition/loves").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contributionType\":\"TOPIC_PROPOSED\",\"contributionId\":5}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void summary() {

        // given
        when(recognitionService.summary(ContributionType.SLOT_PRESENTED, 100L))
                .thenReturn(LoveSummary.builder().count(3).viewerHasLoved(true).build());

        // when
        var actual = mvc.get().uri("/axonia/recognition/loves").queryParam("contributionType", "SLOT_PRESENTED")
                .queryParam("contributionId", "100");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.count").isEqualTo(3);
    }

    @Test
    void summary_topicIsNotAValidContributionType() {

        // when
        var actual = mvc.get().uri("/axonia/recognition/loves").queryParam("contributionType", "TOPIC_PROPOSED")
                .queryParam("contributionId", "5");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void unlove() {

        // when
        var actual = mvc.delete().uri("/axonia/recognition/loves?contributionType={t}&contributionId={id}",
                "SLOT_PRESENTED", 100L);

        // then
        actual.assertThat().hasStatus(204);
        verify(recognitionService).unlove(eq(ContributionType.SLOT_PRESENTED), eq(100L));
    }

    @Test
    void myContributions() {

        // given
        when(recognitionService.myContributions())
                .thenReturn(List.of(RecognizedContribution.builder().contributionType(ContributionType.SLOT_PRESENTED)
                        .contributionId(100L).communityId(1L).communitySlug("eng").communityName("Engineering")
                        .title("Talk").occurredAt(Instant.now()).loveCount(2).build()));

        // when
        var actual = mvc.get().uri("/axonia/recognition/contributions/me");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$[0].communitySlug").isEqualTo("eng");
        actual.assertThat().bodyJson().extractingPath("$[0].communityName").isEqualTo("Engineering");
    }

    @Test
    void newLoves() {

        // given
        when(impactReplayService.newLoves(Optional.of(7L)))
                .thenReturn(NewLoves.builder().asOf(Instant.parse("2026-10-17T12:00:00.123Z")).capped(true)
                        .items(List.of(new NewLoveItem(ContributionType.ARTIFACT_CAPTURED, 200L, 3))).build());

        // when
        var actual = mvc.get().uri("/axonia/recognition/contributions/me/new-loves").queryParam("communityId", "7")
                .exchange();

        // then
        assertThat(actual).hasStatus2xxSuccessful().bodyJson().isStrictlyEqualTo("""
                {"asOf":"2026-10-17T12:00:00.123Z","capped":true,
                 "items":[{"contributionType":"ARTIFACT_CAPTURED","contributionId":200,"newLoves":3}]}
                """);
    }

    @Test
    void newLoves_withoutACommunityAsksForEvery() {

        // given
        when(impactReplayService.newLoves(Optional.empty())).thenReturn(
                NewLoves.builder().asOf(Instant.parse("2026-10-17T12:00:00Z")).capped(false).items(List.of()).build());

        // when
        var actual = mvc.get().uri("/axonia/recognition/contributions/me/new-loves").exchange();

        // then
        assertThat(actual).hasStatus2xxSuccessful().bodyJson().extractingPath("$.items.length()").isEqualTo(0);
    }

    @Test
    void markSeen() {

        // when
        var actual = mvc.put().uri("/axonia/recognition/contributions/me/seen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"asOf\":\"2026-10-17T12:00:00.123Z\",\"communityId\":7}").exchange();

        // then
        assertThat(actual).hasStatus(204);
        verify(impactReplayService).markSeen(new MarkSeenRequest(Instant.parse("2026-10-17T12:00:00.123Z"), 7L));
    }

    @Test
    void markSeen_withoutACommunity() {

        // when
        var actual = mvc.put().uri("/axonia/recognition/contributions/me/seen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"asOf\":\"2026-10-17T12:00:00.123Z\",\"communityId\":null}").exchange();

        // then
        assertThat(actual).hasStatus(204);
        verify(impactReplayService).markSeen(new MarkSeenRequest(Instant.parse("2026-10-17T12:00:00.123Z"), null));
    }

    @Test
    void markSeen_withoutAsOfIs400() {

        // when
        var actual = mvc.put().uri("/axonia/recognition/contributions/me/seen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"communityId\":7}").exchange();

        // then
        assertThat(actual).hasStatus(400);
        verifyNoInteractions(impactReplayService);
    }

    @Test
    void markSeen_futureAsOfIs400() {

        // given
        doThrow(new ValidationException("FUTURE_AS_OF", "asOf cannot be in the future")).when(impactReplayService)
                .markSeen(any());

        // when
        var actual = mvc.put().uri("/axonia/recognition/contributions/me/seen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"asOf\":\"2099-01-01T00:00:00Z\",\"communityId\":null}").exchange();

        // then
        assertThat(actual).hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("FUTURE_AS_OF");
    }
}

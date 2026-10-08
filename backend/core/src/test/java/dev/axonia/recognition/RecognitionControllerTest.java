package dev.axonia.recognition;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = RecognitionController.class)
class RecognitionControllerTest extends ControllerTestSupport {

    @MockitoBean
    private RecognitionService recognitionService;

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
}

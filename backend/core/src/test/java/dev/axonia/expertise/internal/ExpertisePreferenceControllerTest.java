package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.NotFoundException;
import dev.axonia.expertise.AreaCommunity;
import dev.axonia.expertise.AreaEvidencePage;
import dev.axonia.expertise.CommunityTier;
import dev.axonia.expertise.DeclarableCommunity;
import dev.axonia.expertise.Evidence;
import dev.axonia.expertise.EvidenceItem;
import dev.axonia.expertise.ExpertisePreferenceService;
import dev.axonia.expertise.SelfArea;
import dev.axonia.expertise.SelfDeclaration;
import dev.axonia.expertise.SelfView;
import dev.axonia.expertise.SignalType;
import dev.axonia.expertise.SourceType;
import dev.axonia.expertise.Tier;
import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ExpertisePreferenceController.class)
class ExpertisePreferenceControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ExpertisePreferenceService expertisePreferenceService;

    @Test
    void selfView_returnsSelfView() throws JacksonException {

        // given
        var selfView = new SelfView(false, List.of(), List.of(), List.of());
        when(expertisePreferenceService.selfView()).thenReturn(selfView);

        // when
        var actual = mvc.get().uri("/axonia/expertise/me");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(selfView));
    }

    @Test
    void selfView_serializesEvidenceTitleAndCommunitySlug() {

        // given — an area whose evidence carries an enriched title + linkable community slug
        var item = EvidenceItem.builder().signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT).sourceId(1L)
                .label("Scaling Kafka").communitySlug("eng").occurredAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        var evidence = Evidence.builder().presentedCount(1).contributedCount(0).offeredCount(0).recent(List.of(item))
                .build();
        var selfView = new SelfView(false,
                List.of(new SelfArea("kafka", Tier.PRACTITIONER, false,
                        List.of(new AreaCommunity(1L, "eng", "Engineering")), evidence,
                        List.of(new CommunityTier(1L, Tier.PRACTITIONER)))),
                List.of(), List.of());
        when(expertisePreferenceService.selfView()).thenReturn(selfView);

        // when
        var actual = mvc.get().uri("/axonia/expertise/me").exchange();

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson()
                .extractingPath("$.areas[0].evidence.recent[0].communitySlug").isEqualTo("eng");
        actual.assertThat().bodyJson().extractingPath("$.areas[0].evidence.recent[0].label").isEqualTo("Scaling Kafka");
        actual.assertThat().bodyJson().extractingPath("$.areas[0].communities[0].slug").isEqualTo("eng");
        actual.assertThat().bodyJson().extractingPath("$.areas[0].communityTiers[0].tier").isEqualTo("PRACTITIONER");
        actual.assertThat().bodyJson().extractingPath("$.areas[0].communityTiers[0].communityId").isEqualTo(1);
    }

    @Test
    void selfView_serializesDeclarations() {

        // given
        var declaration = SelfDeclaration.builder().tag("kafka").communityId(7L).communityName("Platform")
                .communitySlug("platform").hidden(false).declaredAt(Instant.parse("2026-09-12T12:00:00Z")).build();
        when(expertisePreferenceService.selfView())
                .thenReturn(new SelfView(false, List.of(), List.of(declaration), List.of()));

        // when
        var actual = mvc.get().uri("/axonia/expertise/me").exchange();

        // then
        assertThat(actual).bodyJson().extractingPath("$.declarations[0].communitySlug").isEqualTo("platform");
        assertThat(actual).bodyJson().extractingPath("$.declarations[0].declaredAt").isEqualTo("2026-09-12T12:00:00Z");
    }

    @Test
    void selfView_serializesDeclarableCommunities() {

        // given
        when(expertisePreferenceService.selfView()).thenReturn(new SelfView(false, List.of(), List.of(),
                List.of(new DeclarableCommunity(7L, "platform", "Platform"))));

        // when
        var actual = mvc.get().uri("/axonia/expertise/me").exchange();

        // then
        assertThat(actual).bodyJson().extractingPath("$.declarableCommunities[0].id").isEqualTo(7);
        assertThat(actual).bodyJson().extractingPath("$.declarableCommunities[0].slug").isEqualTo("platform");
        assertThat(actual).bodyJson().extractingPath("$.declarableCommunities[0].name").isEqualTo("Platform");
    }

    @Test
    void optOut_callsServiceAndReturns204() {

        // when
        var actual = mvc.put().uri("/axonia/expertise/me/opt-out").contentType(MediaType.APPLICATION_JSON)
                .content("{\"optedOut\":true}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertisePreferenceService).optOut(true);
    }

    @Test
    void hideTag_callsServiceAndReturns204() {

        // when
        var actual = mvc.put().uri("/axonia/expertise/me/hidden/{tag}", "kafka");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertisePreferenceService).hideTag("kafka");
    }

    @Test
    void unhideTag_callsServiceAndReturns204() {

        // when
        var actual = mvc.delete().uri("/axonia/expertise/me/hidden/{tag}", "kafka");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertisePreferenceService).unhideTag("kafka");
    }

    @Test
    void dismiss_callsServiceAndReturns204() {

        // when
        var actual = mvc.post().uri("/axonia/expertise/me/dismiss").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceType\":\"SLOT\",\"sourceId\":42,\"tag\":\"kafka\"}");

        // then
        actual.assertThat().hasStatus(204);
        verify(expertisePreferenceService).dismissSignal(SourceType.SLOT, 42L, "kafka");
    }

    @Test
    void dismiss_rejectsBodyWithMissingFields() {

        // when — missing sourceId and tag
        var actual = mvc.post().uri("/axonia/expertise/me/dismiss").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceType\":\"SLOT\"}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void areaEvidence_returnsPagedItemsAndHasMore() {

        // given
        var item = EvidenceItem.builder().signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT).sourceId(1L)
                .label("Scaling Kafka").communitySlug("eng").occurredAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        var page = new AreaEvidencePage(List.of(item, item, item), true);
        when(expertisePreferenceService.areaEvidence("kafka", 0, 5)).thenReturn(page);

        // when
        var actual = mvc.get().uri("/axonia/expertise/me/areas/{tag}/evidence?offset=0&limit=5", "kafka");

        // then
        var body = actual.assertThat().hasStatus2xxSuccessful().bodyJson();
        body.extractingPath("$.items.length()").isEqualTo(3);
        body.extractingPath("$.hasMore").isEqualTo(true);
        verify(expertisePreferenceService).areaEvidence("kafka", 0, 5);
    }

    @Test
    void selfViewIn_returnsTheCommunitysSelfView() throws JacksonException {

        // given
        var selfView = new SelfView(false, List.of(), List.of(),
                List.of(new DeclarableCommunity(7L, "platform", "Platform")));
        when(expertisePreferenceService.selfViewIn("platform")).thenReturn(selfView);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/me", "platform");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(selfView));
    }

    @Test
    void areaEvidenceIn_returnsTheCommunitysPage() throws JacksonException {

        // given
        var item = EvidenceItem.builder().signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT).sourceId(1L)
                .label("Scaling Kafka").communitySlug("platform").occurredAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        var page = new AreaEvidencePage(List.of(item), false);
        when(expertisePreferenceService.areaEvidenceIn("platform", "kafka", 5, 20)).thenReturn(page);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/me/areas/{tag}/evidence?offset=5", "platform",
                "kafka");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(page));
    }

    @Test
    void selfViewIn_isNotFoundForAnUnknownCommunity() {

        // given
        when(expertisePreferenceService.selfViewIn("nowhere"))
                .thenThrow(new NotFoundException("Community not found: nowhere"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/me", "nowhere");

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void areaEvidenceIn_isNotFoundForAnUnknownCommunity() {

        // given
        when(expertisePreferenceService.areaEvidenceIn("nowhere", "kafka", 0, 20))
                .thenThrow(new NotFoundException("Community not found: nowhere"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/me/areas/{tag}/evidence", "nowhere", "kafka");

        // then
        actual.assertThat().hasStatus(404);
    }
}

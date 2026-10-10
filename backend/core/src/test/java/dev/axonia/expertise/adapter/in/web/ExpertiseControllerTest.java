package dev.axonia.expertise.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.expertise.application.ExpertiseMapService;
import dev.axonia.expertise.application.ExpertiseService;
import dev.axonia.expertise.application.MapContribution;
import dev.axonia.expertise.application.MapContributions;
import dev.axonia.expertise.application.MapPerson;
import dev.axonia.expertise.application.Practitioner;
import dev.axonia.expertise.domain.Evidence;
import dev.axonia.expertise.domain.EvidenceItem;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.NamedArea;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.expertise.domain.Tier;
import dev.axonia.expertise.domain.TopicLink;
import dev.axonia.expertise.domain.TopicMap;
import dev.axonia.expertise.domain.TopicNode;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ExpertiseController.class)
class ExpertiseControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ExpertiseService expertiseService;
    @MockitoBean
    private ExpertiseMapService expertiseMapService;

    @Test
    void search_mapsTermAndDemonstratedOnly() throws JacksonException {

        // given
        var practitioner = Practitioner.builder().userId("u1").displayName("Alice").tier(Tier.PRACTITIONER)
                .evidence(Evidence.builder().recent(List.of()).build()).build();
        when(expertiseService.whoKnows(eq("eng"), eq("kafka"), eq(true))).thenReturn(List.of(practitioner));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/search", "eng").queryParam("term", "kafka")
                .queryParam("demonstratedOnly", "true");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.length()").isEqualTo(1);
        verify(expertiseService).whoKnows("eng", "kafka", true);
    }

    @Test
    void search_defaultsDemonstratedOnlyToFalse() {

        // given
        when(expertiseService.whoKnows(anyString(), anyString(), anyBoolean())).thenReturn(List.of());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/search", "eng").queryParam("term", "kafka");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
        verify(expertiseService).whoKnows("eng", "kafka", false);
    }

    @Test
    void areas_passesSearchParam() throws JacksonException {

        // given
        var area = new ExpertiseArea("kafka", List.of("kafka", "apache-kafka"), 3);
        when(expertiseService.areas(eq("eng"), eq("kafka"))).thenReturn(List.of(area));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/areas", "eng").queryParam("search", "kafka")
                .exchange();

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$[0].peopleCount").isEqualTo(3);
        verify(expertiseService).areas("eng", "kafka");
    }

    @Test
    void areas_omitsSearchParamWhenAbsent() {

        // given
        when(expertiseService.areas(anyString(), eq(null))).thenReturn(List.of());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/areas", "eng");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
        verify(expertiseService).areas("eng", null);
    }

    @Test
    void search_serializesTheOpenToQuestionsMarker() {

        // given
        var practitioner = Practitioner.builder().userId("u3").displayName("Dana").tier(Tier.OPEN_TO_QUESTIONS)
                .evidence(Evidence.builder().recent(List.of()).build()).openToQuestions(true)
                .declaredAt(Instant.parse("2026-09-12T12:00:00Z")).build();
        when(expertiseService.whoKnows("eng", "kafka", false)).thenReturn(List.of(practitioner));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/search", "eng").queryParam("term", "kafka")
                .exchange();

        // then
        actual.assertThat().bodyJson().extractingPath("$[0].tier").isEqualTo("OPEN_TO_QUESTIONS");
        actual.assertThat().bodyJson().extractingPath("$[0].openToQuestions").isEqualTo(true);
        actual.assertThat().bodyJson().extractingPath("$[0].declaredAt").isEqualTo("2026-09-12T12:00:00Z");
    }

    @Test
    void suggestions_forwardsTagsToSuggestSpeakers() throws JacksonException {

        // given
        var practitioner = Practitioner.builder().userId("u2").displayName("Bob").orgUnit("Platform")
                .tier(Tier.PRACTITIONER).evidence(Evidence.builder().recent(List.of()).build()).build();
        when(expertiseService.suggestSpeakers(eq("eng"), anyList())).thenReturn(List.of(practitioner));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/suggestions", "eng").queryParam("tags",
                "kafka", "java");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.length()").isEqualTo(1);
        verify(expertiseService).suggestSpeakers(eq("eng"), anyList());
    }

    @Test
    void suggestions_withoutTagsPassesEmptyList() {

        // given
        when(expertiseService.suggestSpeakers(anyString(), anyList())).thenReturn(List.of());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/suggestions", "eng");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
        verify(expertiseService).suggestSpeakers(eq("eng"), anyList());
    }

    @Test
    void map_returnsTheTopicsAndLinksForTheCommunity() throws JacksonException {

        // given
        var node = new TopicNode("kafka", 2, 7, 0.57, "Ana Reis", 6, 1, 0, Instant.parse("2026-05-01T00:00:00Z"), 0);
        var map = new TopicMap(List.of(node), List.of(new TopicLink("debezium", "kafka", 2)),
                List.of(new NamedArea(3L, "Streaming", List.of("kafka"))));
        when(expertiseMapService.topicMap("platform-guild")).thenReturn(map);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/map", "platform-guild").exchange();

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.topics[0].canonicalTag")
                .isEqualTo("kafka");
        actual.assertThat().bodyJson().extractingPath("$.topics[0].practitionerCount").isEqualTo(2);
        actual.assertThat().bodyJson().extractingPath("$.topics[0].topHolderName").isEqualTo("Ana Reis");
        actual.assertThat().bodyJson().extractingPath("$.topics[0].openToQuestionsCount").isEqualTo(0);
        actual.assertThat().bodyJson().extractingPath("$.links[0].a").isEqualTo("debezium");
        actual.assertThat().bodyJson().extractingPath("$.links[0].weight").isEqualTo(2);
        actual.assertThat().bodyJson().extractingPath("$.namedAreas[0].name").isEqualTo("Streaming");
        actual.assertThat().bodyJson().extractingPath("$.namedAreas[0].tags[0]").isEqualTo("kafka");
        verify(expertiseMapService).topicMap("platform-guild");
    }

    @Test
    void mapContributions_returnsThePayload() throws JacksonException {

        // given
        var evidence = EvidenceItem.builder().signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT).sourceId(10L)
                .label("Kafka at scale").communitySlug("platform-guild")
                .occurredAt(Instant.parse("2026-05-01T00:00:00Z")).build();
        var payload = new MapContributions(List.of(new MapPerson("ana", "Ana Reis", "https://avatars/ana.png")),
                List.of(new MapContribution(1L, "kafka", "ana", 42, evidence)), false);
        when(expertiseMapService.contributions("platform-guild")).thenReturn(payload);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/map/contributions", "platform-guild")
                .exchange();

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.truncated").isEqualTo(false);
        actual.assertThat().bodyJson().extractingPath("$.people[0].displayName").isEqualTo("Ana Reis");
        actual.assertThat().bodyJson().extractingPath("$.items[0].canonicalTag").isEqualTo("kafka");
        actual.assertThat().bodyJson().extractingPath("$.items[0].reach").isEqualTo(42);
        actual.assertThat().bodyJson().extractingPath("$.items[0].evidence.label").isEqualTo("Kafka at scale");
        verify(expertiseMapService).contributions("platform-guild");
    }

    @Test
    void mapContributions_reportsAPayloadThatHitTheCap() {

        // given
        when(expertiseMapService.contributions("platform-guild"))
                .thenReturn(new MapContributions(List.of(), List.of(), true));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/expertise/map/contributions", "platform-guild")
                .exchange();

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.truncated").isEqualTo(true);
    }
}

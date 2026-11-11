package dev.axonia.health.adapter.in.web;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.health.application.ActivityTraceService;
import dev.axonia.health.application.HealthService;
import dev.axonia.health.domain.ActivityTrace;
import dev.axonia.health.domain.CadenceStatus;
import dev.axonia.health.domain.Diagnosis;
import dev.axonia.health.domain.Finding;
import dev.axonia.health.domain.HealthSnapshot;
import dev.axonia.health.domain.LearningDial;
import dev.axonia.health.domain.Status;
import dev.axonia.health.domain.StatusCode;
import dev.axonia.health.domain.Trend;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = HealthController.class)
class HealthControllerTest extends ControllerTestSupport {

    private static final List<Long> A_SERIES = List.of(1L, 0L, 2L, 0L, 0L, 1L, 0L, 0L, 3L, 0L, 0L, 1L);

    @MockitoBean
    private HealthService healthService;

    @MockitoBean
    private ActivityTraceService activityTraceService;

    /**
     * A whole snapshot, every field populated. The wire-format test renders this one, so a field left off here is a
     * field whose serialization nothing checks - and a null series or reuse dial is a payload the frontend's schema
     * rejects outright.
     */
    private HealthSnapshot aSnapshot() {

        return HealthSnapshot.builder().window("90d").generatedAt(Instant.parse("2026-06-11T12:00:00Z"))
                .stage(HealthSnapshot.StageDial.builder().talks(14).speakers(5).speakersForHalf(2)
                        .distribution(List.of(0.4, 0.28, 0.12, 0.1, 0.1)).trend(Trend.of(5, 3))
                        .status(Status.of(StatusCode.SHARED)).build())
                .curation(new HealthSnapshot.CurationDial(9, 0.66))
                .cadence(HealthSnapshot.CadenceDial.builder().rhythm(CadenceStatus.STEADY).sessionsHeld(6)
                        .medianGapDays(14).nextSessionAt(Instant.parse("2026-06-18T17:00:00Z"))
                        .lastHeldAt(Instant.parse("2026-06-04T17:00:00Z")).trend(Trend.of(6, 5))
                        .status(Status.of(StatusCode.ON_RHYTHM)).build())
                .people(new HealthSnapshot.PeopleDial(32, 20, Trend.of(32, 28)))
                .engagement(new HealthSnapshot.EngagementDial(41, Trend.of(41, 30)))
                .membershipGrowth(new HealthSnapshot.MembershipGrowthDial(5, 80, Trend.of(5, 2)))
                .reach(new HealthSnapshot.CrossTeamReachDial(true, 6, 3, 2, 4, 5, Trend.of(6, 4)))
                .reuse(HealthSnapshot.ReuseDial.builder().opened(214).artifactsOpened(18)
                        .openingsTrend(Trend.of(214, 151)).saves(37).artifactsSaved(12).helped(9).notHelped(4)
                        .savesTrend(Trend.of(37, 40)).status(Status.of(StatusCode.PAYING_OFF)).build())
                .learning(LearningDial.builder().enjoyed(8.7).learned(8.3).answers(44).enjoyedAnswers(42)
                        .learnedAnswers(41).sessions(6).sessionsTooFew(1).previousEnjoyed(8.6).previousLearned(7.6)
                        .status(Status.of(StatusCode.LEARNING_A_LOT)).build())
                .payoff(Status.of(StatusCode.PAYING_OFF))
                .series(new HealthSnapshot.Series(A_SERIES, A_SERIES, A_SERIES))
                .heldSessionDates(List.of(Instant.parse("2026-04-02T17:00:00Z")))
                .diagnosis(new Diagnosis(List.of(new Finding(Finding.Kind.RISK, Finding.Code.CURATION_LEANS_ON_ONE))))
                .build();
    }

    @Test
    void snapshot() throws JacksonException {

        // given
        when(healthService.snapshot("backend-guild", 90)).thenReturn(aSnapshot());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/health", "backend-guild").queryParam("window", "90d");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(aSnapshot()));
    }

    @Test
    void snapshot_wireFieldNames() {

        // given
        when(healthService.snapshot("backend-guild", 90)).thenReturn(aSnapshot());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/health", "backend-guild").exchange();

        // then the field names the frontend parses
        var json = actual.assertThat().hasStatus2xxSuccessful().bodyJson();
        json.extractingPath("$.stage.speakersForHalf").isEqualTo(2);
        json.extractingPath("$.stage.status.code").isEqualTo("SHARED");
        json.extractingPath("$.stage.status.level").isEqualTo("GOOD");
        json.extractingPath("$.curation.topShare").isEqualTo(0.66);
        json.extractingPath("$.cadence.rhythm").isEqualTo("STEADY");
        json.extractingPath("$.cadence.nextSessionAt").isEqualTo("2026-06-18T17:00:00Z");
        json.extractingPath("$.cadence.lastHeldAt").isEqualTo("2026-06-04T17:00:00Z");
        json.extractingPath("$.cadence.status.level").isEqualTo("GOOD");
        json.extractingPath("$.reach.speakersWithUnit").isEqualTo(4);
        json.extractingPath("$.reach.totalSpeakers").isEqualTo(5);
        json.extractingPath("$.people.involved").isEqualTo(32);
        json.extractingPath("$.people.members").isEqualTo(20);
        json.extractingPath("$.reuse.status.code").isEqualTo("PAYING_OFF");
        json.extractingPath("$.series.talks.length()").isEqualTo(12);
        json.extractingPath("$.series.people.length()").isEqualTo(12);
        json.extractingPath("$.series.joins.length()").isEqualTo(12);
        json.extractingPath("$.diagnosis.findings[0].code").isEqualTo("CURATION_LEANS_ON_ONE");
        json.extractingPath("$.learning.enjoyed").isEqualTo(8.7);
        json.extractingPath("$.learning.learned").isEqualTo(8.3);
        json.extractingPath("$.learning.answers").isEqualTo(44);
        json.extractingPath("$.learning.enjoyedAnswers").isEqualTo(42);
        json.extractingPath("$.learning.learnedAnswers").isEqualTo(41);
        json.extractingPath("$.learning.sessions").isEqualTo(6);
        json.extractingPath("$.learning.sessionsTooFew").isEqualTo(1);
        json.extractingPath("$.learning.previousEnjoyed").isEqualTo(8.6);
        json.extractingPath("$.learning.previousLearned").isEqualTo(7.6);
        json.extractingPath("$.learning.status.code").isEqualTo("LEARNING_A_LOT");
        json.extractingPath("$.learning.status.level").isEqualTo("GREAT");
        json.extractingPath("$.payoff.code").isEqualTo("PAYING_OFF");
        json.extractingPath("$.payoff.level").isEqualTo("GREAT");
    }

    @Test
    void snapshot_absentAveragesAreSentAsNull() {

        // given
        when(healthService.snapshot("backend-guild", 90))
                .thenReturn(aSnapshot().toBuilder().learning(LearningDial.empty()).build());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/health", "backend-guild").exchange();

        // then the frontend's schema reads each average as present and nullable, never missing
        var json = actual.assertThat().hasStatus2xxSuccessful().bodyJson();
        json.extractingPath("$.learning.enjoyed").isNull();
        json.extractingPath("$.learning.learned").isNull();
        json.extractingPath("$.learning.previousEnjoyed").isNull();
        json.extractingPath("$.learning.previousLearned").isNull();
        json.extractingPath("$.learning.status.code").isEqualTo("LEARNING_TOO_FEW");
    }

    @Test
    void snapshot_defaultsToNinetyDays() {

        // given
        when(healthService.snapshot("backend-guild", 90)).thenReturn(aSnapshot());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/health", "backend-guild");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
        verify(healthService).snapshot("backend-guild", 90);
    }

    @Test
    void snapshot_invalidWindowIs400() {

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/health", "backend-guild").queryParam("window", "banana");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void snapshot_nonOrganizerIs403() {

        // given
        when(healthService.snapshot("backend-guild", 90)).thenThrow(new ForbiddenException("organizers only"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/health", "backend-guild");

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void activityTrace() throws JacksonException {

        // given
        var trace = new ActivityTrace(90, Collections.nCopies(90, 1L), Collections.nCopies(90, 2L),
                Collections.nCopies(90, 0L));
        when(activityTraceService.trace("backend-guild")).thenReturn(trace);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/activity-trace", "backend-guild");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo(jsonMapper.writeValueAsString(trace));
        actual.assertThat().bodyJson().extractingPath("$.days").isEqualTo(90);
        actual.assertThat().bodyJson().extractingPath("$.sessionsHeld.length()").isEqualTo(90);
    }

    @Test
    void activityTrace_unknownSlugIs404() {

        // given
        when(activityTraceService.trace("nope")).thenThrow(new NotFoundException("Community not found"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/activity-trace", "nope");

        // then
        actual.assertThat().hasStatus(404);
    }
}

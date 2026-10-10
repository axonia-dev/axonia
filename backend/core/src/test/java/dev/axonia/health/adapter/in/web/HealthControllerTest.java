package dev.axonia.health.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.health.application.HealthService;
import dev.axonia.health.domain.CadenceStatus;
import dev.axonia.health.domain.Diagnosis;
import dev.axonia.health.domain.Finding;
import dev.axonia.health.domain.HealthSnapshot;
import dev.axonia.health.domain.Trend;
import dev.axonia.shared.api.error.ForbiddenException;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = HealthController.class)
class HealthControllerTest extends ControllerTestSupport {

    private static final List<Long> A_SERIES = List.of(1L, 0L, 2L, 0L, 0L, 1L, 0L, 0L, 3L, 0L, 0L, 1L);

    @MockitoBean
    private HealthService healthService;

    /**
     * A whole snapshot, every field populated. The wire-format test renders this one, so a field left off here is a
     * field whose serialization nothing checks - and a null series or reuse dial is a payload the frontend's schema
     * rejects outright.
     */
    private HealthSnapshot aSnapshot() {

        return HealthSnapshot.builder().window("90d").generatedAt(Instant.parse("2026-06-11T12:00:00Z")).busFactor(2)
                .concentration(new HealthSnapshot.Concentration(0.8, List.of(0.4, 0.28, 0.12, 0.1, 0.1)))
                .cadence(new HealthSnapshot.CadenceDial(CadenceStatus.STEADY, 6, 14, Trend.of(6, 5)))
                .participation(new HealthSnapshot.ParticipationDial(32, 80, 0.4, Trend.of(0.4, 0.28)))
                .engagement(new HealthSnapshot.EngagementDial(41, Trend.of(41, 30)))
                .membershipGrowth(new HealthSnapshot.MembershipGrowthDial(5, 80, Trend.of(5, 2)))
                .reach(new HealthSnapshot.CrossTeamReachDial(true, 6, 3, 2, 9, 12, Trend.of(6, 4)))
                .reuse(HealthSnapshot.ReuseDial.builder().opened(214).artifactsOpened(18)
                        .openingsTrend(Trend.of(214, 151)).saves(37).artifactsSaved(12).helped(9).notHelped(4)
                        .savesTrend(Trend.of(37, 40)).build())
                .series(new HealthSnapshot.Series(A_SERIES, A_SERIES, A_SERIES, A_SERIES, A_SERIES, A_SERIES))
                .heldSessionDates(List.of(Instant.parse("2026-04-02T17:00:00Z")))
                .diagnosis(new Diagnosis(List.of(new Finding(Finding.Kind.RISK, Finding.Code.OVER_RELIANCE)))).build();
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
}

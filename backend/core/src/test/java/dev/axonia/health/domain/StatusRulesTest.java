package dev.axonia.health.domain;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusRulesTest {

    private static final Instant LAST_HELD = Instant.parse("2026-01-10T18:00:00Z");

    private static Status stage(Long... counts) {

        return StatusRules.stage(Distribution.of(List.of(counts)));
    }

    @Test
    void status_carriesTheLevelOfItsCode() {

        // when
        var actual = Status.of(StatusCode.LEANS_ON_ONE);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.LEANS_ON_ONE, StatusLevel.WATCH));
    }

    @Test
    void stage_tooFewTalks() {

        // when
        var actual = stage(1L, 1L);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.TOO_FEW_TALKS, StatusLevel.QUIET));
    }

    @Test
    void stage_oneVoice() {

        // when
        var actual = stage(3L);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.ONE_VOICE, StatusLevel.WORRY));
    }

    @Test
    void stage_leansOnOne() {

        // when
        var actual = stage(3L, 1L, 1L);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.LEANS_ON_ONE, StatusLevel.WATCH));
    }

    @Test
    void stage_exactlyHalfIsShared() {

        // when / then
        assertThat(stage(2L, 1L, 1L)).isEqualTo(new Status(StatusCode.SHARED, StatusLevel.GOOD));
        assertThat(stage(2L, 2L)).isEqualTo(new Status(StatusCode.SHARED, StatusLevel.GOOD));
    }

    @Test
    void stage_sharedStage() {

        // given
        var distribution = Distribution.of(List.of(2L, 2L, 2L, 1L, 1L, 1L));

        // when
        var actual = StatusRules.stage(distribution);

        // then
        assertThat(distribution.speakersForHalf()).isEqualTo(3);
        assertThat(actual).isEqualTo(new Status(StatusCode.SHARED_STAGE, StatusLevel.GREAT));
    }

    @Test
    void alive_dormant() {

        // when
        var actual = StatusRules.alive(CadenceStatus.DORMANT, null, false, Trend.Direction.FLAT);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.DORMANT, StatusLevel.WORRY));
    }

    @Test
    void alive_startingUp() {

        // when
        var actual = StatusRules.alive(CadenceStatus.STALLED, null, true, Trend.Direction.FLAT);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.STARTING_UP, StatusLevel.QUIET));
    }

    @Test
    void alive_stalledOnceAnySessionWasEverHeld() {

        // when
        var actual = StatusRules.alive(CadenceStatus.STALLED, LAST_HELD, true, Trend.Direction.FLAT);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.STALLED, StatusLevel.WORRY));
    }

    @Test
    void alive_stalledWithNothingAhead() {

        // when
        var actual = StatusRules.alive(CadenceStatus.STALLED, null, false, Trend.Direction.FLAT);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.STALLED, StatusLevel.WORRY));
    }

    @Test
    void alive_slowing() {

        // when
        var actual = StatusRules.alive(CadenceStatus.SLOWING, LAST_HELD, true, Trend.Direction.UP);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.SLOWING, StatusLevel.WATCH));
    }

    @Test
    void alive_pickingUp() {

        // when
        var actual = StatusRules.alive(CadenceStatus.STEADY, LAST_HELD, true, Trend.Direction.UP);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.PICKING_UP, StatusLevel.GREAT));
    }

    @Test
    void alive_onRhythm() {

        // when
        var actual = StatusRules.alive(CadenceStatus.STEADY, LAST_HELD, true, Trend.Direction.FLAT);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.ON_RHYTHM, StatusLevel.GOOD));
    }

    @Test
    void payoff_helpedWinsOverNothingOpened() {

        // when
        var actual = StatusRules.payoff(0, 0, 3);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.PAYING_OFF, StatusLevel.GREAT));
    }

    @Test
    void payoff_startingToPayOff() {

        // when
        var actual = StatusRules.payoff(5, 2, 1);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.STARTING_TO_PAY_OFF, StatusLevel.GOOD));
    }

    @Test
    void payoff_savedNotConfirmed() {

        // when
        var actual = StatusRules.payoff(5, 2, 0);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.SAVED_NOT_CONFIRMED, StatusLevel.WATCH));
    }

    @Test
    void payoff_noPayoffYet() {

        // when
        var actual = StatusRules.payoff(2, 0, 0);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.NO_PAYOFF_YET, StatusLevel.QUIET));
    }

    @Test
    void payoff_nothingOpened() {

        // when
        var actual = StatusRules.payoff(0, 0, 0);

        // then
        assertThat(actual).isEqualTo(new Status(StatusCode.NOTHING_OPENED, StatusLevel.QUIET));
    }
}

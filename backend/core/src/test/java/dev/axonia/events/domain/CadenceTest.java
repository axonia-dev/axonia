package dev.axonia.events.domain;

import java.time.Period;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CadenceTest {

    @Test
    void rejectsCountBelowOne() {
        assertThatThrownBy(() -> new Cadence(0, CadenceUnit.WEEK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeCount() {
        assertThatThrownBy(() -> new Cadence(-1, CadenceUnit.WEEK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCountAbove999() {
        assertThatThrownBy(() -> new Cadence(1000, CadenceUnit.WEEK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullUnit() {
        assertThatThrownBy(() -> new Cadence(1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void toPeriod_day() {
        assertThat(new Cadence(9, CadenceUnit.DAY).toPeriod()).isEqualTo(Period.ofDays(9));
    }

    @Test
    void toPeriod_week_isCountTimesSevenDays() {
        assertThat(new Cadence(3, CadenceUnit.WEEK).toPeriod()).isEqualTo(Period.ofDays(21));
    }

    @Test
    void toPeriod_month() {
        assertThat(new Cadence(2, CadenceUnit.MONTH).toPeriod()).isEqualTo(Period.ofMonths(2));
    }

    @Test
    void toPeriod_year() {
        assertThat(new Cadence(1, CadenceUnit.YEAR).toPeriod()).isEqualTo(Period.ofYears(1));
    }

    @Test
    void toDays_perUnit() {
        assertThat(new Cadence(1, CadenceUnit.DAY).toDays()).isEqualTo(1);
        assertThat(new Cadence(2, CadenceUnit.WEEK).toDays()).isEqualTo(14);
        assertThat(new Cadence(1, CadenceUnit.MONTH).toDays()).isEqualTo(30);
        assertThat(new Cadence(1, CadenceUnit.YEAR).toDays()).isEqualTo(365);
    }
}

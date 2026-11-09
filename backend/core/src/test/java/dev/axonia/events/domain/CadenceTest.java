package dev.axonia.events.domain;

import java.time.LocalDate;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CadenceTest {

    private static final LocalDate WEDNESDAY_13_JAN = LocalDate.of(2027, 1, 13);

    private static LocalDate[] firstFour(Cadence cadence, LocalDate startsOn) {

        return IntStream.range(0, 4).mapToObj(index -> cadence.occurrence(startsOn, index)).toArray(LocalDate[]::new);
    }

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
    @DisplayName("a monthly cadence nobody stated a rule for keeps its day number, as every series did before the rule")
    void monthlyDefaultsToTheDayOfTheMonth() {
        assertThat(new Cadence(1, CadenceUnit.MONTH).monthlyBy()).isEqualTo(MonthlyRule.DAY_OF_MONTH);
    }

    @Test
    @DisplayName("only a monthly cadence carries a monthly rule")
    void otherUnitsCarryNoMonthlyRule() {
        assertThat(new Cadence(1, CadenceUnit.WEEK, MonthlyRule.WEEKDAY_OF_MONTH).monthlyBy()).isNull();
    }

    @Test
    void stepsDays() {
        assertThat(firstFour(new Cadence(9, CadenceUnit.DAY), WEDNESDAY_13_JAN)).containsExactly(WEDNESDAY_13_JAN,
                LocalDate.of(2027, 1, 22), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 9));
    }

    @Test
    void stepsWeeks() {
        assertThat(firstFour(new Cadence(3, CadenceUnit.WEEK), WEDNESDAY_13_JAN)).containsExactly(WEDNESDAY_13_JAN,
                LocalDate.of(2027, 2, 3), LocalDate.of(2027, 2, 24), LocalDate.of(2027, 3, 17));
    }

    @Test
    void stepsYears() {
        assertThat(firstFour(new Cadence(2, CadenceUnit.YEAR), WEDNESDAY_13_JAN)).containsExactly(WEDNESDAY_13_JAN,
                LocalDate.of(2029, 1, 13), LocalDate.of(2031, 1, 13), LocalDate.of(2033, 1, 13));
    }

    @Test
    @DisplayName("by weekday, the second Wednesday stays the second Wednesday: 13 Jan, 10 Feb, 10 Mar, 14 Apr")
    void monthlyByWeekdayKeepsTheOrdinalWeekday() {
        assertThat(firstFour(new Cadence(1, CadenceUnit.MONTH, MonthlyRule.WEEKDAY_OF_MONTH), WEDNESDAY_13_JAN))
                .containsExactly(WEDNESDAY_13_JAN, LocalDate.of(2027, 2, 10), LocalDate.of(2027, 3, 10),
                        LocalDate.of(2027, 4, 14));
    }

    @Test
    @DisplayName("by weekday, a fifth weekday means the last one, since most months have only four")
    void monthlyByWeekdayReadsAFifthWeekdayAsTheLast() {
        var fifthFriday = LocalDate.of(2027, 1, 29);

        assertThat(firstFour(new Cadence(1, CadenceUnit.MONTH, MonthlyRule.WEEKDAY_OF_MONTH), fifthFriday))
                .containsExactly(fifthFriday, LocalDate.of(2027, 2, 26), LocalDate.of(2027, 3, 26),
                        LocalDate.of(2027, 4, 30));
    }

    @Test
    void monthlyByWeekdayStepsTheMonthCount() {
        assertThat(firstFour(new Cadence(2, CadenceUnit.MONTH, MonthlyRule.WEEKDAY_OF_MONTH), WEDNESDAY_13_JAN))
                .containsExactly(WEDNESDAY_13_JAN, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 5, 12),
                        LocalDate.of(2027, 7, 14));
    }

    @Test
    void monthlyByDayKeepsTheDayNumber() {
        assertThat(firstFour(new Cadence(1, CadenceUnit.MONTH, MonthlyRule.DAY_OF_MONTH), WEDNESDAY_13_JAN))
                .containsExactly(WEDNESDAY_13_JAN, LocalDate.of(2027, 2, 13), LocalDate.of(2027, 3, 13),
                        LocalDate.of(2027, 4, 13));
    }

    /**
     * Every occurrence is measured from the start rather than from the one before it. Stepping from the previous one
     * clamped 31 January to 28 February and then stayed on the 28th for every month after.
     */
    @Test
    @DisplayName("by day, the 31st clamps in a short month and comes back afterwards")
    void monthlyByDayDoesNotDriftAfterAShortMonth() {
        var thirtyFirst = LocalDate.of(2027, 1, 31);

        assertThat(firstFour(new Cadence(1, CadenceUnit.MONTH), thirtyFirst)).containsExactly(thirtyFirst,
                LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
    }

    @Test
    void toDays_perUnit() {
        assertThat(new Cadence(1, CadenceUnit.DAY).toDays()).isEqualTo(1);
        assertThat(new Cadence(2, CadenceUnit.WEEK).toDays()).isEqualTo(14);
        assertThat(new Cadence(1, CadenceUnit.MONTH).toDays()).isEqualTo(30);
        assertThat(new Cadence(1, CadenceUnit.YEAR).toDays()).isEqualTo(365);
    }
}

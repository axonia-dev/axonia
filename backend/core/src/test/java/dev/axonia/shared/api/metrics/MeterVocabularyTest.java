package dev.axonia.shared.api.metrics;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.shared.api.schedule.SweepHeartbeat.Sweep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeterVocabularyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"R2", "billing sweep", "-drain", "drain-", "drain--twice", "9lives"})
    void refusesAValueNoDashboardCouldMatchVerbatim(String tag) {

        assertThatThrownBy(() -> new Sweep(tag)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a meter tag: " + tag);
    }

    @Test
    void acceptsLowercaseWordsJoinedByDashes() {

        assertThat(new Sweep("agent-connection-retention").tag()).isEqualTo("agent-connection-retention");
    }

    @Test
    void joinsTheOpenVocabularyAndTheContributedOneInThatOrder() {

        var actual = MeterVocabulary.join(List.of("a", "b"), Stream.of("c"), Function.identity());

        assertThat(actual).containsExactly("a", "b", "c");
    }

    @Test
    void refusesTwoEntriesSharingATag() {

        assertThatThrownBy(() -> MeterVocabulary.join(List.of("a", "b"), Stream.of("b"), Function.identity()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Two meter vocabulary entries share the tag [b]");
    }

    @Test
    void namesTheMissingContributionForAnUnregisteredEntry() {

        assertThatThrownBy(() -> MeterVocabulary.registered(Map.of(), "x", "billing-sweep"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No meter is registered for billing-sweep: the module that uses it contributes it as a "
                        + "MeterContribution");
    }

    @Test
    void answersTheRegisteredMeter() {

        assertThat(MeterVocabulary.registered(Map.of("x", 1), "x", "x")).isEqualTo(1);
    }

    @Test
    void aContributionDeclaresNothingItDoesNotName() {

        var contribution = new MeterContribution() {
        };

        assertThat(contribution.sweeps()).isEmpty();
        assertThat(contribution.integrations()).isEmpty();
        assertThat(contribution.events()).isEmpty();
    }
}

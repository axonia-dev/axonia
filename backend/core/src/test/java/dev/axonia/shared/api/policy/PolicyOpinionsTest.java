package dev.axonia.shared.api.policy;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyOpinionsTest {

    private static final Function<Function<String, Optional<String>>, Optional<String>> ASK_ACME = policy -> policy
            .apply("acme");

    @Test
    void noContributorHasNoOpinion() {

        assertThat(PolicyOpinions.sole(List.of(), ASK_ACME)).isEmpty();
    }

    @Test
    void contributorsThatHaveNothingToSayHaveNoOpinion() {

        List<Function<String, Optional<String>>> silent = List.of(id -> Optional.empty(), id -> Optional.empty());

        assertThat(PolicyOpinions.sole(silent, ASK_ACME)).isEmpty();
    }

    @Test
    void theOneContributorThatAnswersIsTheAnswer() {

        List<Function<String, Optional<String>>> contributors = List.of(id -> Optional.empty(),
                id -> Optional.of("hub rule for " + id));

        assertThat(PolicyOpinions.sole(contributors, ASK_ACME)).contains("hub rule for acme");
    }

    @Test
    void twoAnswersAreAWiringErrorNotAChoice() {

        List<Function<String, Optional<String>>> contributors = List.of(id -> Optional.of("first"),
                id -> Optional.of("second"));

        assertThatThrownBy(() -> PolicyOpinions.sole(contributors, ASK_ACME)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("first").hasMessageContaining("second");
    }
}

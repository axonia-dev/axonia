package dev.axonia.expertise.internal;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagSimilarityTest {

    @Test
    void suggestMerges_detectsTypo() {

        // "kafkq" is edit-distance 1 from "kafka" (len=5), score = 1 - 1/5 = 0.8
        var pairs = TagSimilarity.suggestMerges(List.of("kafka", "kafkq"), 0.8);

        assertThat(pairs).isNotEmpty();
        var pair = pairs.get(0);
        assertThat(pair.tagA()).isIn("kafka", "kafkq");
        assertThat(pair.tagB()).isIn("kafka", "kafkq");
        assertThat(pair.tagA()).isNotEqualTo(pair.tagB());
        assertThat(pair.score()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void suggestMerges_substringBonusApplied() {

        // "kafka" is a substring of "kafka-streams", score >= 0.7
        var pairs = TagSimilarity.suggestMerges(List.of("kafka", "kafka-streams"), 0.5);

        assertThat(pairs).isNotEmpty();
        var pair = pairs.get(0);
        assertThat(pair.score()).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    void suggestMerges_noPairWhenNotSimilar() {

        var pairs = TagSimilarity.suggestMerges(List.of("kafka", "postgres"), 0.5);

        assertThat(pairs).isEmpty();
    }

    @Test
    void suggestMerges_resultSortedByDescendingScore() {

        // kafka/kafkq (score=0.8) and kafka/kafka-a (substring => 0.7)
        var pairs = TagSimilarity.suggestMerges(List.of("kafka", "kafkq", "kafka-a"), 0.5);

        assertThat(pairs).hasSizeGreaterThanOrEqualTo(2);
        for (var i = 0; i < pairs.size() - 1; i++) {
            assertThat(pairs.get(i).score()).isGreaterThanOrEqualTo(pairs.get(i + 1).score());
        }
    }

    // -------------------------------------------------------------------------
    // Line 55/56: length diff > 5, not substring → scorePair returns null → pair skipped
    // -------------------------------------------------------------------------

    @Test
    void suggestMerges_skipsLongLengthDifferenceWhenNotSubstring() {

        // "ab" and "ghijklmn" — length diff 6 > 5, neither contains the other
        var pairs = TagSimilarity.suggestMerges(List.of("ab", "ghijklmn"), 0.0);

        assertThat(pairs).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Line 60: maxLen == 0 branch — both empty strings → score 1.0
    // -------------------------------------------------------------------------

    @Test
    void suggestMerges_bothEmptyStrings_scoreIsOne() {

        // both "" strings: maxLen = max(0,0) = 0 → score = 1.0 (the maxLen == 0 branch)
        // empty string contains empty string (isSubstring = true), so length-diff skip does not apply
        var pairs = TagSimilarity.suggestMerges(List.of("", ""), 0.5);

        assertThat(pairs).isNotEmpty();
        assertThat(pairs.get(0).score()).isEqualTo(1.0);
    }
}

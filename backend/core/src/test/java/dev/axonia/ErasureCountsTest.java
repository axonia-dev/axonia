package dev.axonia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErasureCountsTest {

    @Test
    void plusSumsAllFourComponents() {

        var sum = new ErasureCounts(2, 3, 4, 5).plus(new ErasureCounts(6, 7, 8, 9));

        assertThat(sum.deleted()).isEqualTo(8);
        assertThat(sum.cleared()).isEqualTo(10);
        assertThat(sum.anonymised()).isEqualTo(12);
        assertThat(sum.mediaResidue()).isEqualTo(14);
    }

    @Test
    void noneIsTheIdentity() {

        var counts = new ErasureCounts(4, 6, 8, 10);

        var sum = counts.plus(ErasureCounts.NONE);

        assertThat(sum.deleted()).isEqualTo(counts.deleted());
        assertThat(sum.cleared()).isEqualTo(counts.cleared());
        assertThat(sum.anonymised()).isEqualTo(counts.anonymised());
        assertThat(sum.mediaResidue()).isEqualTo(counts.mediaResidue());
    }
}

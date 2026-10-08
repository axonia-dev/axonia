package dev.axonia.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrgUnitTest {

    @Test
    void leaf_returnsFinestNonNullLabel() {

        assertThat(new OrgUnit("Acme", "Platform", "Payments").leaf()).isEqualTo("Payments");
        assertThat(new OrgUnit("Acme", "Platform", null).leaf()).isEqualTo("Platform");
        assertThat(new OrgUnit("Acme", null, null).leaf()).isEqualTo("Acme");
    }

    @Test
    void path_listsNonNullLabelsCoarseToFine() {

        assertThat(new OrgUnit("Acme", "Platform", "Payments").path()).containsExactly("Acme", "Platform", "Payments");
        assertThat(new OrgUnit("Acme", null, "Payments").path()).containsExactly("Acme", "Payments");
    }

    @Test
    void key_joinsThePath() {

        assertThat(new OrgUnit("Acme", "Platform", "Payments").key()).isEqualTo("Acme / Platform / Payments");
    }

    @Test
    void isEmpty_trueOnlyWhenAllLabelsNull() {

        assertThat(new OrgUnit(null, null, null).isEmpty()).isTrue();
        assertThat(new OrgUnit(null, null, "Payments").isEmpty()).isFalse();
    }
}

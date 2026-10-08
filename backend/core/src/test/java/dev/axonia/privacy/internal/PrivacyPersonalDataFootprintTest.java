package dev.axonia.privacy.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The design's claim about {@code erasure_receipt} - that it names nobody - is only as real as this assertion.
 * {@code PersonalDataFootprintTest} requires every column to be declared in one bucket or the other, but declaring a
 * name-bearing column {@code personal} with a {@code Disposition} would pass that check just as green. This is the one
 * place that forces every column of this table into {@code notPersonal} and nowhere else.
 */
class PrivacyPersonalDataFootprintTest {

    @Test
    void everyTableDeclaresNoPersonalColumn() {

        var tables = new PrivacyPersonalDataFootprint().tables();

        assertThat(tables).isNotEmpty().allSatisfy(table -> assertThat(table.personal())
                .describedAs("'%s' must declare no personal column - it names nobody", table.table()).isEmpty());
    }
}

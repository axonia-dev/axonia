package dev.axonia.shared.api.personaldata;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PersonalDataFootprint#table(String)} is how an exporter reaches the manifest that governs its projection, so a
 * name that does not exist has to fail loudly and say which module was asked. Silently returning nothing would produce
 * an export with no columns rather than a build failure.
 */
class PersonalDataFootprintLookupTest {

    private static final PersonalDataFootprint FOOTPRINT = () -> List.of(TableFootprint.builder().table("bookmark")
            .category(DataCategory.WHAT_YOU_SAVED).personal(List.of()).notPersonal(Set.of("id")).build());

    @Test
    void findsATableTheModuleDeclares() {

        assertThat(FOOTPRINT.table("bookmark").notPersonal()).containsExactly("id");
    }

    @Test
    void refusesATableTheModuleDoesNotDeclare() {

        assertThatThrownBy(() -> FOOTPRINT.table("topic")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }
}

package dev.axonia.shared.api.personaldata;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintLayers;
import dev.axonia.FootprintTestSupport;
import dev.axonia.OpenSeed;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layers 1 and 2 of the data-subject-rights gate over the open modules. Layer 1: every table an open module claims
 * exists and is claimed once. Layer 2: every column of a claimed table is declared, either as personal with a
 * disposition or as affirmatively not personal. A migration that adds either without a declaration fails here, which is
 * the whole point: the gate exists so that a feature added next year cannot introduce personal data nobody erases or
 * exports. Layer 1 is an equality here, since the open tests run on the Self-hosted schema; that the closed modules
 * claim every table the Cloud adds is {@code CloudPersonalDataFootprintTest}'s.
 */
class PersonalDataFootprintTest extends FootprintTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private List<PersonalDataFootprint> footprints;

    @Autowired
    private List<PersonalDataExporter> exporters;

    /**
     * An export carries facts, not keys. The declaration is the only place this can be held: a projection is built from
     * it, so a column that gets back into {@link TableFootprint#exportedContext()} is back in somebody's download with
     * nothing else to notice. A label is checked on the same footing, because a label named for the key it replaces
     * would put the key back under a friendlier name.
     */
    @Test
    void noExportedContextOrLabelNamesAnIdentifier() {

        assertThat(layers().exportedIdentifiers()).describedAs(
                "an export carries facts, not keys - emit a readable label the module joins for, or drop the column")
                .isEmpty();
    }

    /**
     * A withheld column is one a member's export silently leaves out, so the set is held here in full: a new one has to
     * be written down and reviewed rather than merely declared.
     */
    @Test
    void theWithheldColumnsAreExactlyTheReviewedOnes() {

        assertThat(layers().withheldColumns()).containsExactlyInAnyOrder("identity_user.external_id",
                "identity_user.scim_id", "identity_user.avatar_media_key", "identity_user.avatar_override_key",
                "directory_group_member.user_scim_id", "role_binding.subject_label");
    }

    @Test
    void everyTableIsClaimedByAnOpenModule() {

        // when
        var claimed = layers().claimedTables();

        // then
        assertThat(layers().liveTables(Set.of("flyway_schema_history")))
                .describedAs("the open schema and the open modules' claims differ: declare a new table in its module's "
                        + "PersonalDataFootprint, and drop a claim whose table is gone")
                .isNotEmpty().containsExactlyInAnyOrderElementsOf(claimed);
    }

    @Test
    void noTableIsClaimedTwice() {

        assertThat(layers().tablesClaimedTwice()).describedAs("tables claimed by more than one module").isEmpty();
    }

    @Test
    void everyColumnOfEveryClaimedTableIsDeclared() {

        layers().assertEveryColumnIsDeclared();
    }

    /**
     * Layer 3: every personal column a module declares shows up as a key somewhere in that same module's export
     * fragment. {@link Disposition#EXPORT_ONLY} is emphatically not an exemption from that - it is the disposition
     * whose entire meaning is "no per-column rule erases this one; it is declared so that an export carries it", so a
     * column declaring it is the column this test most needs to hold. {@code communication_message.facts} is its only
     * user today: the message is shared by every recipient of it, so no single member's erasure may delete the row, and
     * it goes when its last delivery does instead, which changes nothing about the export. {@code RETAIN} columns are
     * the real exemption - personal but not the member's, {@code organization.closed_by} naming the axonia operator who
     * closed a tenant - so they sit outside the member data-subject regime entirely and no export carries them.
     */
    @Test
    void everyPersonalColumnAppearsInItsModulesExportFragment() {

        new OpenSeed(jdbc).seedOpenTables();

        layers().assertEveryPersonalColumnIsExported();
    }

    /**
     * Layer 3's other half, and the reason the projection is derived from the manifest rather than written beside it:
     * an export may not carry a column the footprint did not put in {@link TableFootprint#exportedColumns()}. That is
     * every credential digest, and every column classified {@code RETAIN} because it names an axonia operator rather
     * than a member. A {@code SELECT *} anywhere - the shape every exporter here used to have - fails this by
     * construction, whatever the column happens to be called.
     */
    @Test
    void noExportFragmentCarriesAColumnItsFootprintWithholds() {

        new OpenSeed(jdbc).seedOpenTables();

        layers().assertNoExportCarriesAWithheldColumn();
    }

    private FootprintLayers layers() {

        return new FootprintLayers(jdbc, footprints, exporters);
    }
}

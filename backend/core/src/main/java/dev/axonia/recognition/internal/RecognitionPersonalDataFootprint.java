package dev.axonia.recognition.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.Classification;
import dev.axonia.ColumnFootprint;
import dev.axonia.DataCategory;
import dev.axonia.Disposition;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.TableFootprint;

/**
 * {@code recognized_contribution} no longer names its contributor: that column was dropped when credit became a
 * read-time projection from the owning bounded context (V4), so the row itself carries no personal data. A love,
 * {@code contribution_love}, does carry a person - {@code lover_user_id} - and it is anonymised in place: the
 * contribution's love count must survive the lover's erasure.
 * <p>
 * A reaction, {@code contribution_reaction}, is a thing a member said, so its {@code reactor_user_id} goes with the
 * member: {@code DELETE_ROW}, not anonymised. {@code reaction_definition} is the opposite: once approved it belongs to
 * the Organization, not the member who proposed or approved it, so {@code proposed_by_user_id} and
 * {@code approved_by_user_id} are {@code CLEAR} - both columns are nullable, and the definition itself, the hub's word,
 * survives unnamed.
 */
@Component
public class RecognitionPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(recognizedContribution(), contributionLove(), contributionReaction(), reactionDefinition());
    }

    private TableFootprint recognizedContribution() {

        return TableFootprint.builder().table("recognized_contribution").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "contribution_type", "contribution_id",
                        "community_id", "title", "occurred_at", "created_at", "updated_at", "version"))
                .build();
    }

    private TableFootprint contributionLove() {

        return TableFootprint.builder().table("contribution_love").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of(new ColumnFootprint("lover_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "recognized_contribution_id", "created_at", "updated_at",
                        "version"))
                .exportedContext(Set.of("created_at", "updated_at")).exportedLabels(Set.of("contribution")).build();
    }

    /**
     * {@code name} names the reaction definition's own name, joined in by the exporter rather than owned by this table;
     * declaring it here under {@code exportedLabels} is what makes that joined alias a deliberate export rather than an
     * accident of {@code reaction_definition}'s own {@code exportedContext} happening to contain it.
     */
    private TableFootprint contributionReaction() {

        return TableFootprint.builder().table("contribution_reaction").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(
                        List.of(new ColumnFootprint("reactor_user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "community_id", "contribution_type", "contribution_id",
                        "emoji", "definition_id", "created_at", "updated_at", "version"))
                .exportedContext(Set.of("contribution_type", "emoji", "created_at"))
                .exportedLabels(Set.of("name", "contribution")).build();
    }

    private TableFootprint reactionDefinition() {

        return TableFootprint.builder().table("reaction_definition").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of(new ColumnFootprint("proposed_by_user_id", Classification.SUBJECT, Disposition.CLEAR),
                        new ColumnFootprint("approved_by_user_id", Classification.SUBJECT, Disposition.CLEAR)))
                .notPersonal(Set.of("id", "organization_id", "name", "image_key", "state", "created_at", "updated_at",
                        "version"))
                .exportedContext(Set.of("name")).build();
    }
}

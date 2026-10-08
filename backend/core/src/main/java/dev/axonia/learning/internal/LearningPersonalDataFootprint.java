package dev.axonia.learning.internal;

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
 * {@code learning_intent} keys on {@code user_id}, and a row exists only to say that one member wants to learn one area
 * in one community, so the whole row goes with them for erasure. An export carries the area and the date it was named,
 * with the community's name joined in place of its surrogate id.
 */
@Component
public class LearningPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(TableFootprint.builder().table("learning_intent").category(DataCategory.WHAT_YOU_WANT_TO_LEARN)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("organization_id", "community_id", "tag", "declared_at"))
                .exportedContext(Set.of("tag", "declared_at")).exportedLabels(Set.of("community")).build());
    }
}

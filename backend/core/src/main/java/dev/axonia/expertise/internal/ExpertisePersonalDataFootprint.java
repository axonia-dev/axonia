package dev.axonia.expertise.internal;

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
 * The signal tables ({@code expertise_signal} and everything a member does to their own graph - hide, opt out, dismiss
 * a suggestion, declare an area) each key on {@code user_id}, and the row exists only to describe that person's
 * expertise, so the whole row goes with them for erasure. The vocabulary tables, {@code expertise_tag_alias} and
 * {@code expertise_tag_dismissal}, hold a community's curated tag spellings and merge verdicts, and the named-area
 * tables ({@code expertise_named_area} and {@code expertise_named_area_tag}) hold the groupings a leader named - facts
 * about tags, not about any one member - so none of them carries a personal column.
 *
 * <p>
 * An export is narrower than erasure. Surrogate ids ({@code id}, {@code community_id}, {@code source_id}) and the
 * tenant id are keys into axonia's own storage, not facts about the person, so they stay out of {@code exportedContext}
 * and a joined name or title is exported in their place via {@code exportedLabels} instead. {@code expertise_opt_out}
 * has no fact beyond the subject itself - the fact worth exporting is that the row exists at all - so its
 * {@code exportedContext} is empty.
 */
@Component
public class ExpertisePersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(expertiseSignal(), expertiseHiddenTag(), expertiseOptOut(), expertiseDismissedSignal(),
                expertiseDeclaration(), expertiseTagAlias(), expertiseTagDismissal(), expertiseNamedArea(),
                expertiseNamedAreaTag());
    }

    private TableFootprint expertiseSignal() {

        return TableFootprint.builder().table("expertise_signal").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "community_id", "tag", "signal_type", "source_type",
                        "source_id", "occurred_at"))
                .exportedContext(Set.of("tag", "signal_type", "source_type", "occurred_at"))
                .exportedLabels(Set.of("community", "source")).build();
    }

    private TableFootprint expertiseHiddenTag() {

        return TableFootprint.builder().table("expertise_hidden_tag").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("organization_id", "tag")).exportedContext(Set.of("tag")).build();
    }

    private TableFootprint expertiseOptOut() {

        return TableFootprint.builder().table("expertise_opt_out").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("organization_id")).exportedContext(Set.of()).build();
    }

    private TableFootprint expertiseDismissedSignal() {

        return TableFootprint.builder().table("expertise_dismissed_signal").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("organization_id", "source_type", "source_id", "tag"))
                .exportedContext(Set.of("source_type", "tag")).exportedLabels(Set.of("source")).build();
    }

    private TableFootprint expertiseDeclaration() {

        return TableFootprint.builder().table("expertise_declaration").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("organization_id", "community_id", "tag", "declared_at"))
                .exportedContext(Set.of("tag", "declared_at")).exportedLabels(Set.of("community")).build();
    }

    private TableFootprint expertiseTagAlias() {

        return TableFootprint.builder().table("expertise_tag_alias").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of())
                .notPersonal(Set.of("organization_id", "community_id", "variant_tag", "canonical_tag")).build();
    }

    private TableFootprint expertiseTagDismissal() {

        return TableFootprint.builder().table("expertise_tag_dismissal").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of()).notPersonal(Set.of("organization_id", "community_id", "tag_low", "tag_high"))
                .build();
    }

    private TableFootprint expertiseNamedArea() {

        return TableFootprint.builder().table("expertise_named_area").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "community_id", "name")).build();
    }

    private TableFootprint expertiseNamedAreaTag() {

        return TableFootprint.builder().table("expertise_named_area_tag").category(DataCategory.WHAT_YOU_KNOW)
                .personal(List.of())
                .notPersonal(Set.of("organization_id", "community_id", "named_area_id", "canonical_tag")).build();
    }
}

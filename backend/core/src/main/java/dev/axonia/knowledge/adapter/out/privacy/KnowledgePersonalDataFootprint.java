package dev.axonia.knowledge.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.Classification;
import dev.axonia.shared.api.personaldata.ColumnFootprint;
import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.Disposition;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * An artifact and its attachments belong to the community's library, not to the person who contributed them, so
 * {@code contributor_user_id} and {@code attached_by_user_id} are anonymised in place - the artifact and its attachment
 * survive, credited to no one. {@code artifact_tag} and {@code artifact_type} describe the library's vocabulary and its
 * categories, never a person.
 *
 * <p>
 * {@code artifact.search_vector} and {@code artifact.search_document} both stay out of the export:
 * {@code search_vector} is a {@code tsvector} derived from {@code search_document}, and {@code search_document} is
 * itself only the pre-computed text behind that index, a verbatim duplicate of {@code title} and {@code description}
 * kept so Postgres has something to index. The export already carries the title and description in readable form, so
 * neither column tells the reader anything new. {@code artifact.provenance_segment_id} leaves for a different reason:
 * nothing readable exists on the other end of it, since there is no segment table with a name to join to, so it is
 * dropped rather than exported as a bare surrogate key. {@code artifact_attachment.segment_id} is the same case for the
 * same reason.
 */
@Component
public class KnowledgePersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(artifact(), artifactAttachment(), artifactOpening(), artifactTag(), artifactType());
    }

    private TableFootprint artifact() {

        return TableFootprint.builder().table("artifact").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List
                        .of(new ColumnFootprint("contributor_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "community_id", "type_id", "title", "description", "url",
                        "provenance_session_id", "provenance_segment_id", "provenance_label", "status",
                        "search_document", "search_vector", "created_at", "updated_at", "version", "love_count"))
                .exportedContext(Set.of("title", "description", "url", "provenance_label", "status", "love_count",
                        "created_at", "updated_at"))
                .exportedLabels(Set.of("community", "type", "provenance_session")).build();
    }

    private TableFootprint artifactAttachment() {

        return TableFootprint.builder().table("artifact_attachment").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List
                        .of(new ColumnFootprint("attached_by_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "artifact_id", "session_id", "segment_id", "created_at",
                        "updated_at", "version"))
                .exportedContext(Set.of("created_at", "updated_at")).exportedLabels(Set.of("artifact", "session"))
                .build();
    }

    /**
     * Deliberately {@code HOW_YOU_TAKE_PART} rather than {@code WHAT_YOU_CONTRIBUTE}: opening somebody else's artifact
     * is taking part in the community, not contributing to it, and the notice section a reader lands on has to say the
     * thing they would recognise.
     *
     * <p>
     * One personal column and a plain {@code DELETE_ROW}. Unlike {@code bookmark}, this table does not carry a copy of
     * the artifact's contributor, so there is nothing here to anonymise and no optimistic-lock resurrection hazard for
     * the eraser to guard against.
     */
    private TableFootprint artifactOpening() {

        return TableFootprint.builder().table("artifact_opening").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(
                        List.of(new ColumnFootprint("member_user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "community_id", "artifact_id", "opened_on", "created_at"))
                .exportedContext(Set.of("opened_on", "created_at")).exportedLabels(Set.of("community", "artifact"))
                .build();
    }

    private TableFootprint artifactTag() {

        return TableFootprint.builder().table("artifact_tag").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of()).notPersonal(Set.of("artifact_id", "tag")).build();
    }

    private TableFootprint artifactType() {

        return TableFootprint.builder().table("artifact_type").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "label", "created_at", "updated_at",
                        "version", "icon", "accent_color"))
                .build();
    }
}

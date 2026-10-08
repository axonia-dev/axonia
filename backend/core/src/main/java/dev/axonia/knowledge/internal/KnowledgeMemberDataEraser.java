package dev.axonia.knowledge.internal;

import java.sql.Timestamp;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ErasureCounts;
import dev.axonia.MemberDataEraser;
import dev.axonia.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * An artifact and its attachments belong to the community's library, not to the person who contributed them, so
 * {@code artifact.contributor_user_id} and {@code artifact_attachment.attached_by_user_id} are anonymised in place -
 * both survive, credited to no one. {@code artifact_attachment} carries its own {@code organization_id} - declared by
 * {@link KnowledgePersonalDataFootprint} and enforced by its own row-level-security policy - so it is anonymised
 * directly on that column. Reaching it through the parent artifact instead would miss a row whose own organization_id
 * disagrees with its parent's, since nothing in the schema forces the two to agree: {@code artifact_id} carries only a
 * plain foreign key, not a composite one on {@code (organization_id, artifact_id)}.
 * <p>
 * Each UPDATE bumps {@code version} and stamps {@code updated_at} from the erasure's own instant. Without that, a
 * request that loaded the row just before the erasure still matches its own optimistic-lock predicate afterwards and
 * writes the whole entity back - including the pre-erasure subject - silently resurrecting an identifier a legal
 * erasure just removed. The instant is a plain named parameter from the caller's Clock, never SQL {@code now()}.
 * <p>
 * A member's openings are deleted outright rather than anonymised: an opening is that member's own act, including on
 * their own artifacts, and unlike a contribution the community's history does not depend on it surviving.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId(), "surrogate",
                erasure.surrogateId(), "erasedAt", Timestamp.from(erasure.erasedAt()));
        var deleted = jdbc
                .update("DELETE FROM artifact_opening WHERE organization_id = :org AND member_user_id = :user", params);
        var anonymised = jdbc.update(
                "UPDATE artifact SET contributor_user_id = :surrogate, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND contributor_user_id = :user",
                params);
        anonymised += jdbc.update(
                "UPDATE artifact_attachment SET attached_by_user_id = :surrogate, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND attached_by_user_id = :user",
                params);
        return new ErasureCounts(deleted, 0, anonymised, 0);
    }
}

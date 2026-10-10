package dev.axonia.identity.adapter.out.privacy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.media.api.MediaStorage;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Identity owns the person record itself, so its erasure touches the widest slice of any module. Order is load bearing:
 * the avatar key and the {@code scim_id} needed to find the member's synced group memberships are both read off
 * {@code identity_user} - in one query, so "read everything off the row before deleting it" is one obviously-atomic
 * step rather than two independently-orderable SELECTs - before that row is deleted, then
 * {@code personal_access_token}, {@code agent_connection} (its tokens go with it by {@code ON DELETE CASCADE}) and
 * {@code directory_group_member} go, and {@code identity_user} goes last.
 *
 * <p>
 * The avatar is personal data and leaves with the person, but a storage failure must not fail the erasure - refusing to
 * erase a database because object storage hiccuped serves nobody. Unlike tenant purge, a warning alone is too quiet
 * here: the residue is counted and lands on the receipt where it can be seen and re-run.
 *
 * <p>
 * A table another module owns is erased by that module's own {@link MemberDataEraser}; none carries a foreign key to or
 * from any identity table, so the erasers have no ordering dependency on each other.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaStorage mediaStorage;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId());
        var identityRow = findIdentityUser(params);
        var mediaResidue = identityRow.map(this::deleteAvatarBlob).orElse(0L);
        var scimId = identityRow.flatMap(IdentityMemberDataEraser::scimIdOf);

        var deleted = jdbc.update("DELETE FROM personal_access_token WHERE organization_id = :org AND user_id = :user",
                params);
        deleted += jdbc.update("DELETE FROM agent_connection WHERE organization_id = :org AND user_id = :user", params);
        deleted += deleteDirectoryGroupMembership(erasure.organizationId(), scimId);
        deleted += jdbc.update("DELETE FROM identity_user WHERE organization_id = :org AND user_id = :user", params);

        return new ErasureCounts(deleted, 0, 0, mediaResidue);
    }

    private Optional<Map<String, Object>> findIdentityUser(Map<String, String> params) {

        List<Map<String, Object>> rows = jdbc
                .queryForList("SELECT avatar_media_key, avatar_override_key, scim_id FROM identity_user "
                        + "WHERE organization_id = :org AND user_id = :user", params);
        return rows.stream().findFirst();
    }

    private static Optional<String> scimIdOf(Map<String, Object> identityRow) {

        return Optional.ofNullable((String) identityRow.get("scim_id"));
    }

    private long deleteDirectoryGroupMembership(String organizationId, Optional<String> scimId) {

        if (scimId.isEmpty()) {
            return 0;
        }
        return jdbc.update("DELETE FROM directory_group_member WHERE organization_id = :org AND user_scim_id = :scimId",
                Map.of("org", organizationId, "scimId", scimId.get()));
    }

    private long deleteAvatarBlob(Map<String, Object> identityRow) {

        var residue = 0L;
        for (var column : new String[]{"avatar_media_key", "avatar_override_key"}) {
            var key = (String) identityRow.get(column);
            if (key != null && deleteQuietly(key)) {
                residue++;
            }
        }
        return residue;
    }

    /** Returns {@code true} when the delete failed, so the caller can count it as residue on the receipt. */
    private boolean deleteQuietly(String key) {

        try {
            mediaStorage.delete(key);
            return false;
        } catch (RuntimeException ex) {
            log.warn("Avatar blob '{}' could not be deleted during member erasure - counted as media residue", key, ex);
            return true;
        }
    }
}

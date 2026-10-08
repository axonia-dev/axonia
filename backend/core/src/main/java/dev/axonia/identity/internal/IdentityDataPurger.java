package dev.axonia.identity.internal;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationDataPurger;
import dev.axonia.media.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes every identity row for one organization - hub tenant deletion's identity slice.
 * {@code directory_group_member} carries its own {@code organization_id} and cascades from {@code directory_group}; it
 * is still deleted explicitly first, ahead of its parent, rather than relying on the cascade. Synced avatar blobs are
 * deleted best-effort before the rows go (the keys live on the rows): a storage failure is logged, never fatal -
 * orphaned blobs are acceptable residue, missing tenant rows are not.
 *
 * <p>
 * A table another module owns is deleted by that module's own {@link OrganizationDataPurger}; none carries a foreign
 * key to or from any identity table, so the purgers have no ordering dependency on each other.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaStorage mediaStorage;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        deleteAvatarBlobs(params);
        jdbc.update("DELETE FROM directory_group_member WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM directory_group WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM scim_token WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM personal_access_token WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM agent_connection_token WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM agent_connection WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM identity_user WHERE organization_id = :org", params);
    }

    /**
     * Synced avatar photos and any member-uploaded overrides are personal data; both leave with the tenant. Best-effort
     * like community media.
     */
    private void deleteAvatarBlobs(Map<String, String> params) {

        var keys = jdbc.queryForList("SELECT avatar_media_key AS key FROM identity_user "
                + "WHERE organization_id = :org AND avatar_media_key IS NOT NULL " + "UNION ALL "
                + "SELECT avatar_override_key AS key FROM identity_user "
                + "WHERE organization_id = :org AND avatar_override_key IS NOT NULL", params, String.class);
        keys.forEach(this::deleteQuietly);
    }

    private void deleteQuietly(String key) {

        try {
            mediaStorage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Orphaned media blob '{}' - storage delete failed during tenant purge", key, ex);
        }
    }
}

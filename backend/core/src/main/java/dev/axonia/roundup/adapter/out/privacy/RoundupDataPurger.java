package dev.axonia.roundup.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.media.api.MediaStorage;
import dev.axonia.shared.api.personaldata.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes a closed tenant's roundup setting and composed thumbnails. Neither carries personal data (see
 * {@link RoundupPersonalDataFootprint}), but both are organization-scoped bookkeeping that must not survive a closed
 * tenant. A thumbnail's stored image is deleted best-effort: an orphaned blob is acceptable residue, a tenant row is
 * not.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoundupDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaStorage mediaStorage;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        jdbc.queryForList("SELECT media_key FROM roundup_thumbnail WHERE organization_id = :org", params, String.class)
                .forEach(this::deleteQuietly);
        jdbc.update("DELETE FROM roundup_thumbnail WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM roundup_settings WHERE organization_id = :org", params);
    }

    private void deleteQuietly(String key) {

        try {
            mediaStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Orphaned media blob '{}': storage delete failed during tenant purge", key, e);
        }
    }
}

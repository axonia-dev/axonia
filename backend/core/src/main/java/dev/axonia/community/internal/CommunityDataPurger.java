package dev.axonia.community.internal;

import java.util.Map;
import java.util.Objects;

import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationDataPurger;
import dev.axonia.media.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes every community row for one organization — hub tenant deletion's community slice. Runs after the events
 * purger ({@code @Order}) because events tables hold FKs into {@code communities}. Stored look-and-feel media (badge,
 * hero) is deleted best-effort: a storage failure is logged, never fatal — orphaned blobs are acceptable residue,
 * missing tenant rows are not.
 */
@Slf4j
@Component
@Order(CommunityDataPurger.PURGE_ORDER)
@RequiredArgsConstructor
public class CommunityDataPurger implements OrganizationDataPurger {

    public static final int PURGE_ORDER = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaStorage mediaStorage;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        deleteStoredMedia(params);
        jdbc.update("DELETE FROM community_chat_destination WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM memberships WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM communities WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM community_kind WHERE organization_id = :org", params);
    }

    private void deleteStoredMedia(Map<String, String> params) {

        var badgeKeys = jdbc.queryForList(
                "SELECT badge_key FROM communities WHERE organization_id = :org AND badge_key IS NOT NULL", params,
                String.class);
        var heroKeys = jdbc.queryForList(
                "SELECT hero_key FROM communities WHERE organization_id = :org AND hero_key IS NOT NULL", params,
                String.class);
        badgeKeys.stream().filter(Objects::nonNull).forEach(this::deleteQuietly);
        heroKeys.stream().filter(Objects::nonNull).forEach(this::deleteQuietly);
    }

    private void deleteQuietly(String key) {

        try {
            mediaStorage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Orphaned media blob '{}' — storage delete failed during tenant purge", key, ex);
        }
    }
}

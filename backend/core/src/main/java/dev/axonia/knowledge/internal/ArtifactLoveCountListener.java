package dev.axonia.knowledge.internal;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import dev.axonia.recognition.ContributionLoved;
import dev.axonia.recognition.ContributionType;
import dev.axonia.recognition.ContributionUnloved;
import lombok.RequiredArgsConstructor;

/**
 * Keeps {@code artifact.love_count} in sync with recognition's loves by listening to its application events — the first
 * Spring Modulith application-event integration in the codebase. Only {@link ContributionType#ARTIFACT_CAPTURED}
 * contributions correlate to an artifact, so other contribution kinds are ignored.
 *
 * <p>
 * The count is a denormalized projection, not a source of truth: should it ever drift (e.g. a lost async delivery), it
 * is fully rebuildable by re-running the backfill in {@code V25__artifact_love_count.sql}. A reconciliation job is out
 * of scope for this first cut.
 *
 * <p>
 * <strong>RLS INVARIANT:</strong> this listener writes the {@code artifact} table, which is a organization-scoped,
 * RLS-protected table. The UPDATE must execute with {@code app.current_org} already set in the Postgres session, which
 * {@link dev.axonia.config.OrganizationAwareDataSource} arranges from the caller's
 * {@link org.springframework.security.core.context.SecurityContext}. This is safe ONLY because {@code @EnableAsync} is
 * currently OFF, so {@link ApplicationModuleListener @ApplicationModuleListener} dispatches synchronously on the
 * request thread where the JWT (and thus the organization GUC) is present. <br>
 * <em>If async event processing is ever enabled</em>, the SecurityContext will be absent on the worker thread, the
 * organization GUC will be unset, and writes will silently match zero rows — at that point the organization must be
 * propagated into the event payload and the GUC set explicitly here before the UPDATE.
 */
@Component
@RequiredArgsConstructor
class ArtifactLoveCountListener {

    private final NamedParameterJdbcTemplate jdbc;

    @ApplicationModuleListener
    void on(ContributionLoved event) {

        if (event.contributionType() != ContributionType.ARTIFACT_CAPTURED) {
            return;
        }
        jdbc.update("UPDATE artifact SET love_count = love_count + 1 WHERE id = :id AND community_id = :communityId",
                params(event.contributionId(), event.communityId()));
    }

    @ApplicationModuleListener
    void on(ContributionUnloved event) {

        if (event.contributionType() != ContributionType.ARTIFACT_CAPTURED) {
            return;
        }
        jdbc.update(
                "UPDATE artifact SET love_count = GREATEST(love_count - 1, 0)"
                        + " WHERE id = :id AND community_id = :communityId",
                params(event.contributionId(), event.communityId()));
    }

    private MapSqlParameterSource params(Long contributionId, Long communityId) {

        return new MapSqlParameterSource().addValue("id", contributionId).addValue("communityId", communityId);
    }
}

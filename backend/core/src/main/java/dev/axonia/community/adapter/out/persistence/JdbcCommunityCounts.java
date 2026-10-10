package dev.axonia.community.adapter.out.persistence;

import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.CommunityCounts;
import lombok.RequiredArgsConstructor;

/**
 * Answers the active-community count from the {@code communities} table, inside whatever organization scope the caller
 * already entered. The {@code organization_id} predicate is redundant under RLS and stated anyway: a query that carries
 * it is correct even when run by a role that bypasses row-level security by ownership, which is exactly what the test
 * containers' owner does.
 *
 * <p>
 * {@link NamedParameterJdbcTemplate}, not {@link org.springframework.jdbc.core.simple.JdbcClient}: every other
 * organization-scoped adapter in this codebase (roughly forty of them, e.g.
 * {@code bookmarks.adapter.out.health.ReuseSignalsAdapter}, {@code access.adapter.out.privacy.AccessDataPurger})
 * injects the plain, Boot-autoconfigured {@code NamedParameterJdbcTemplate}, which already resolves unambiguously to
 * the single {@code @Primary} organization-aware {@code DataSource} with no bean of its own to add or maintain.
 */
@Component
@RequiredArgsConstructor
class JdbcCommunityCounts implements CommunityCounts {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public int countActive(String organizationId) {

        var params = new MapSqlParameterSource().addValue("organizationId", organizationId);
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT count(*) FROM communities WHERE organization_id = :organizationId AND status = 'ACTIVE'",
                params, Integer.class));
    }
}

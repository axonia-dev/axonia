package dev.axonia.bookmarks.adapter.out.knowledge;

import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.api.spi.SavedArtifacts;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class SavedArtifactsAdapter implements SavedArtifacts {

    private static final String SAVED_IN_SQL = """
            SELECT saveable_id FROM bookmark
             WHERE organization_id = :organizationId AND member_user_id = :memberUserId
               AND community_id = :communityId AND saveable_type = 'ARTIFACT'
            """;

    private static final String FORGET_SQL = """
            DELETE FROM bookmark
             WHERE organization_id = :organizationId AND saveable_type = 'ARTIFACT' AND saveable_id = :artifactId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public Set<Long> savedIn(String organizationId, String memberUserId, long communityId) {

        var params = new MapSqlParameterSource().addValue("organizationId", organizationId)
                .addValue("memberUserId", memberUserId).addValue("communityId", communityId);
        return Set.copyOf(jdbc.queryForList(SAVED_IN_SQL, params, Long.class));
    }

    @Override
    @Transactional
    public void forget(String organizationId, long artifactId) {

        jdbc.update(FORGET_SQL, new MapSqlParameterSource().addValue("organizationId", organizationId)
                .addValue("artifactId", artifactId));
    }
}

package dev.axonia.expertise.internal;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class TagDismissalRepositoryImpl implements TagDismissalRepository {

    private static final String SELECT_SQL = """
            SELECT tag_low, tag_high
            FROM expertise_tag_dismissal
            WHERE organization_id = :organizationId AND community_id = :communityId
            """;

    private static final String INSERT_SQL = """
            INSERT INTO expertise_tag_dismissal (organization_id, community_id, tag_low, tag_high)
            VALUES (:organizationId, :communityId, :tagLow, :tagHigh)
            ON CONFLICT (organization_id, community_id, tag_low, tag_high) DO NOTHING
            """;

    private static final String DELETE_SQL = """
            DELETE FROM expertise_tag_dismissal
            WHERE organization_id = :organizationId AND community_id = :communityId
              AND tag_low = :tagLow AND tag_high = :tagHigh
            """;

    private static final String DELETE_NAMING_SQL = """
            DELETE FROM expertise_tag_dismissal
             WHERE organization_id = :organizationId AND community_id = :communityId
               AND (tag_low = :tag OR tag_high = :tag)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Set<TagPair> dismissedPairs(String organizationId, long communityId) {

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId);
        return jdbc.queryForList(SELECT_SQL, params).stream()
                .map(r -> new TagPair((String) r.get("tag_low"), (String) r.get("tag_high")))
                .collect(Collectors.toSet());
    }

    @Override
    public void addDismissal(String organizationId, long communityId, String tagLow, String tagHigh) {

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId)
                .addValue("tagLow", tagLow).addValue("tagHigh", tagHigh);
        jdbc.update(INSERT_SQL, params);
    }

    @Override
    public void removeDismissal(String organizationId, long communityId, String tagLow, String tagHigh) {

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId)
                .addValue("tagLow", tagLow).addValue("tagHigh", tagHigh);
        jdbc.update(DELETE_SQL, params);
    }

    @Override
    public void removeDismissalsNaming(String organizationId, long communityId, String tag) {

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId)
                .addValue("tag", tag);
        jdbc.update(DELETE_NAMING_SQL, params);
    }
}

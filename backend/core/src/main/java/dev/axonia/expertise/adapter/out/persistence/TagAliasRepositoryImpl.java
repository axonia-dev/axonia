package dev.axonia.expertise.adapter.out.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.axonia.expertise.application.port.out.TagAliasRepository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TagAliasRepositoryImpl implements TagAliasRepository {

    private static final String SELECT_SQL = """
            SELECT variant_tag, canonical_tag
            FROM expertise_tag_alias
            WHERE organization_id = :organizationId AND community_id = :communityId
            """;

    private static final String SELECT_MANY_SQL = """
            SELECT community_id, variant_tag, canonical_tag
            FROM expertise_tag_alias
            WHERE organization_id = :organizationId AND community_id IN (:communityIds)
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO expertise_tag_alias (organization_id, community_id, variant_tag, canonical_tag)
            VALUES (:organizationId, :communityId, :variantTag, :canonicalTag)
            ON CONFLICT (organization_id, community_id, variant_tag)
            DO UPDATE SET canonical_tag = EXCLUDED.canonical_tag
            """;

    private static final String DELETE_SQL = """
            DELETE FROM expertise_tag_alias
            WHERE organization_id = :organizationId AND community_id = :communityId AND variant_tag = :variantTag
            """;

    private static final String DELETE_NAMING_SQL = """
            DELETE FROM expertise_tag_alias
            WHERE organization_id = :organizationId AND community_id = :communityId
              AND (variant_tag = :tag OR canonical_tag = :tag)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Map<String, String> aliasMap(String organizationId, long communityId) {

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId);
        return jdbc.queryForList(SELECT_SQL, params).stream()
                .collect(Collectors.toMap(r -> (String) r.get("variant_tag"), r -> (String) r.get("canonical_tag")));
    }

    @Override
    public Map<Long, Map<String, String>> aliasMaps(String organizationId, Collection<Long> communityIds) {

        var result = new HashMap<Long, Map<String, String>>();
        if (communityIds.isEmpty()) {
            return result;
        }

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityIds", communityIds);
        for (var row : jdbc.queryForList(SELECT_MANY_SQL, params)) {
            var communityId = ((Number) row.get("community_id")).longValue();
            result.computeIfAbsent(communityId, k -> new HashMap<>()).put((String) row.get("variant_tag"),
                    (String) row.get("canonical_tag"));
        }
        for (var communityId : communityIds) {
            result.putIfAbsent(communityId, Map.of());
        }
        return result;
    }

    @Override
    public void addAlias(String organizationId, long communityId, String variantTag, String canonicalTag) {

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId)
                .addValue("variantTag", variantTag).addValue("canonicalTag", canonicalTag);
        jdbc.update(UPSERT_SQL, params);
    }

    @Override
    public void removeAlias(String organizationId, long communityId, String variantTag) {

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId)
                .addValue("variantTag", variantTag);
        jdbc.update(DELETE_SQL, params);
    }

    @Override
    public void removeAliasesNaming(String organizationId, long communityId, String tag) {

        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId)
                .addValue("tag", tag);
        jdbc.update(DELETE_NAMING_SQL, params);
    }
}

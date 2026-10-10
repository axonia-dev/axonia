package dev.axonia.expertise.adapter.out.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.axonia.expertise.application.port.out.NamedAreaRepository;
import dev.axonia.expertise.domain.NamedArea;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NamedAreaRepositoryImpl implements NamedAreaRepository {

    private static final String SELECT_SQL = """
            SELECT a.id, a.name, t.canonical_tag
            FROM expertise_named_area a
            LEFT JOIN expertise_named_area_tag t ON t.named_area_id = a.id
            WHERE a.organization_id = :organizationId AND a.community_id = :communityId
            ORDER BY a.name, a.id, t.canonical_tag
            """;

    private static final String INSERT_SQL = """
            INSERT INTO expertise_named_area (organization_id, community_id, name)
            VALUES (:organizationId, :communityId, :name)
            RETURNING id
            """;

    private static final String RENAME_SQL = """
            UPDATE expertise_named_area SET name = :name
            WHERE id = :id AND organization_id = :organizationId AND community_id = :communityId
            """;

    private static final String DELETE_SQL = """
            DELETE FROM expertise_named_area
            WHERE id = :id AND organization_id = :organizationId AND community_id = :communityId
            """;

    /** Selecting from the owning row is what keeps a foreign id from placing a tag into another community. */
    private static final String PLACE_SQL = """
            INSERT INTO expertise_named_area_tag (organization_id, community_id, named_area_id, canonical_tag)
            SELECT organization_id, community_id, id, :canonicalTag
            FROM expertise_named_area
            WHERE id = :id AND organization_id = :organizationId AND community_id = :communityId
            ON CONFLICT (organization_id, community_id, canonical_tag)
            DO UPDATE SET named_area_id = EXCLUDED.named_area_id
            """;

    private static final String REMOVE_SQL = """
            DELETE FROM expertise_named_area_tag
            WHERE organization_id = :organizationId AND community_id = :communityId AND canonical_tag = :canonicalTag
            """;

    private static final String RENAME_TAG_SQL = """
            UPDATE expertise_named_area_tag SET canonical_tag = :to
            WHERE organization_id = :organizationId AND community_id = :communityId AND canonical_tag = :from
              AND NOT EXISTS (
                  SELECT 1 FROM expertise_named_area_tag placed
                  WHERE placed.organization_id = :organizationId AND placed.community_id = :communityId
                    AND placed.canonical_tag = :to)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<NamedArea> findAll(String organizationId, long communityId) {

        var tagsById = new LinkedHashMap<Long, List<String>>();
        var namesById = new LinkedHashMap<Long, String>();
        jdbc.query(SELECT_SQL, scope(organizationId, communityId), rs -> {
            var id = rs.getLong("id");
            namesById.put(id, rs.getString("name"));
            var tags = tagsById.computeIfAbsent(id, k -> new ArrayList<>());
            var tag = rs.getString("canonical_tag");
            if (tag != null) {
                tags.add(tag);
            }
        });
        return namesById.entrySet().stream().map(
                entry -> new NamedArea(entry.getKey(), entry.getValue(), List.copyOf(tagsById.get(entry.getKey()))))
                .toList();
    }

    @Override
    public long create(String organizationId, long communityId, String name) {

        return Objects.requireNonNull(
                jdbc.queryForObject(INSERT_SQL, scope(organizationId, communityId).addValue("name", name), Long.class));
    }

    @Override
    public boolean rename(String organizationId, long communityId, long id, String name) {

        return jdbc.update(RENAME_SQL,
                scope(organizationId, communityId).addValue("id", id).addValue("name", name)) > 0;
    }

    @Override
    public boolean delete(String organizationId, long communityId, long id) {

        return jdbc.update(DELETE_SQL, scope(organizationId, communityId).addValue("id", id)) > 0;
    }

    @Override
    public boolean place(String organizationId, long communityId, long id, String canonicalTag) {

        return jdbc.update(PLACE_SQL,
                scope(organizationId, communityId).addValue("id", id).addValue("canonicalTag", canonicalTag)) > 0;
    }

    @Override
    public void remove(String organizationId, long communityId, String canonicalTag) {

        jdbc.update(REMOVE_SQL, scope(organizationId, communityId).addValue("canonicalTag", canonicalTag));
    }

    /** A membership row names its tag in one column only, so this and {@link #remove} end the same rows. */
    @Override
    public void removeNaming(String organizationId, long communityId, String tag) {

        remove(organizationId, communityId, tag);
    }

    @Override
    public void renameTag(String organizationId, long communityId, String from, String to) {

        if (from.equals(to)) {
            return;
        }
        jdbc.update(RENAME_TAG_SQL, scope(organizationId, communityId).addValue("from", from).addValue("to", to));
        remove(organizationId, communityId, from);
    }

    private static MapSqlParameterSource scope(String organizationId, long communityId) {

        return new MapSqlParameterSource("organizationId", organizationId).addValue("communityId", communityId);
    }
}

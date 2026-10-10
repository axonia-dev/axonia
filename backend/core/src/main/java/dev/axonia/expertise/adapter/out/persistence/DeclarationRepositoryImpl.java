package dev.axonia.expertise.adapter.out.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.domain.Declaration;

import static dev.axonia.expertise.adapter.out.persistence.MemberTagTable.scope;

@Repository
public class DeclarationRepositoryImpl implements DeclarationRepository {

    private static final String COLUMNS = "d.community_id, d.user_id, d.tag, d.declared_at";

    private static final String FOR_USER_SQL = "SELECT " + COLUMNS + """
             FROM expertise_declaration d
            WHERE d.organization_id = :org AND d.user_id = :userId
            ORDER BY d.community_id, d.tag
            """;

    private static final String VISIBLE_SQL = "SELECT " + COLUMNS + """
             FROM expertise_declaration d
             JOIN identity_user u
                    ON u.organization_id = d.organization_id
                   AND u.user_id         = d.user_id
                   AND u.active          = true
             JOIN memberships m
                    ON m.organization_id = d.organization_id
                   AND m.community_id    = d.community_id
                   AND m.user_id         = d.user_id
             LEFT JOIN expertise_opt_out optout
                    ON optout.organization_id = d.organization_id
                   AND optout.user_id         = d.user_id
             LEFT JOIN expertise_tag_alias alias
                    ON alias.organization_id = d.organization_id
                   AND alias.community_id    = d.community_id
                   AND alias.variant_tag     = d.tag
             LEFT JOIN expertise_hidden_tag hidden
                    ON hidden.organization_id = d.organization_id
                   AND hidden.user_id         = d.user_id
                   AND hidden.tag          = COALESCE(alias.canonical_tag, d.tag)
            WHERE d.organization_id = :org
              AND d.community_id    = :communityId
              AND optout.user_id    IS NULL
              AND hidden.tag        IS NULL
            {tagFilter}
            ORDER BY d.user_id, d.declared_at, d.tag
            """;

    private static final String VISIBLE_IN_SQL = VISIBLE_SQL.replace("{tagFilter}", "  AND d.tag IN (:tags)");

    private static final String ALL_VISIBLE_IN_SQL = VISIBLE_SQL.replace("{tagFilter}", "");

    private static final String COUNT_IN_SQL = """
            SELECT count(DISTINCT d.user_id)
              FROM expertise_declaration d
             WHERE d.organization_id = :org AND d.community_id = :communityId AND d.tag IN (:tags)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final MemberTagTable<Declaration> declarations;

    DeclarationRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
        this.declarations = new MemberTagTable<>(jdbc, "expertise_declaration", Declaration::new);
    }

    @Override
    public void lockDeclarer(String org, long communityId, String userId) {

        declarations.lockMember(org, communityId, userId);
    }

    @Override
    public List<Declaration> inCommunity(String org, long communityId, String userId) {

        return declarations.inCommunity(org, communityId, userId);
    }

    @Override
    public void insert(String org, Declaration declaration) {

        declarations.insert(org, declaration);
    }

    @Override
    public void delete(String org, long communityId, String userId, Set<String> tags) {

        declarations.delete(org, communityId, userId, tags);
    }

    @Override
    public void deleteAllIn(String org, long communityId, String userId) {

        declarations.deleteAllIn(org, communityId, userId);
    }

    @Override
    public List<Declaration> forUser(String org, String userId) {

        var params = new MapSqlParameterSource("org", org).addValue("userId", userId);
        return declarations.query(FOR_USER_SQL, params);
    }

    @Override
    public List<Declaration> visibleIn(String org, long communityId, Set<String> tags) {

        return declarations.query(VISIBLE_IN_SQL, scope(org, communityId).addValue("tags", tags));
    }

    @Override
    public List<Declaration> allVisibleIn(String org, long communityId) {

        return declarations.query(ALL_VISIBLE_IN_SQL, scope(org, communityId));
    }

    @Override
    public List<String> tagsIn(String org, long communityId) {

        return declarations.tagsIn(org, communityId);
    }

    @Override
    public int countIn(String org, long communityId, Set<String> tags) {

        if (tags.isEmpty()) {
            return 0;
        }
        return Objects.requireNonNull(
                jdbc.queryForObject(COUNT_IN_SQL, scope(org, communityId).addValue("tags", tags), Integer.class));
    }

    @Override
    @Transactional
    public void renameTag(String org, long communityId, String from, String to) {

        declarations.renameTag(org, communityId, from, to);
    }

    @Override
    public void removeTag(String org, long communityId, String tag) {

        declarations.removeTag(org, communityId, tag);
    }
}

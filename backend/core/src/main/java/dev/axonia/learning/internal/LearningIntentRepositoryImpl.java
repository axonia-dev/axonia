package dev.axonia.learning.internal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.MemberTagTable;
import dev.axonia.learning.LearningIntent;

import static dev.axonia.MemberTagTable.scope;

@Repository
class LearningIntentRepositoryImpl implements LearningIntentRepository {

    private static final String CURRENT_IN_SQL = """
            SELECT i.community_id, i.user_id, i.tag, i.declared_at
              FROM learning_intent i
              JOIN identity_user u
                     ON u.organization_id = i.organization_id
                    AND u.user_id         = i.user_id
                    AND u.active          = true
              JOIN memberships m
                     ON m.organization_id = i.organization_id
                    AND m.community_id    = i.community_id
                    AND m.user_id         = i.user_id
             WHERE i.organization_id = :org AND i.community_id = :communityId
             ORDER BY i.declared_at, i.user_id, i.tag
            """;

    private static final String COUNT_IN_SQL = """
            SELECT count(DISTINCT i.user_id)
              FROM learning_intent i
             WHERE i.organization_id = :org AND i.community_id = :communityId AND i.tag = :tag
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final MemberTagTable<LearningIntent> intents;

    LearningIntentRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
        this.intents = new MemberTagTable<>(jdbc, "learning_intent", LearningIntent::new);
    }

    @Override
    public void lockLearner(String org, long communityId, String userId) {

        intents.lockMember(org, communityId, userId);
    }

    @Override
    public List<LearningIntent> inCommunity(String org, long communityId, String userId) {

        return intents.inCommunity(org, communityId, userId);
    }

    @Override
    public void insert(String org, LearningIntent intent) {

        intents.insert(org, intent);
    }

    @Override
    public void delete(String org, long communityId, String userId, Set<String> tags) {

        intents.delete(org, communityId, userId, tags);
    }

    @Override
    public void deleteAllIn(String org, long communityId, String userId) {

        intents.deleteAllIn(org, communityId, userId);
    }

    @Override
    public List<LearningIntent> currentIn(String org, long communityId) {

        return intents.query(CURRENT_IN_SQL, scope(org, communityId));
    }

    @Override
    public List<String> tagsIn(String org, long communityId) {

        return intents.tagsIn(org, communityId);
    }

    @Override
    public int countIn(String org, long communityId, String tag) {

        return Objects.requireNonNull(
                jdbc.queryForObject(COUNT_IN_SQL, scope(org, communityId).addValue("tag", tag), Integer.class));
    }

    @Override
    @Transactional
    public void renameTag(String org, long communityId, String from, String to) {

        intents.renameTag(org, communityId, from, to);
    }

    @Override
    public void removeTag(String org, long communityId, String tag) {

        intents.removeTag(org, communityId, tag);
    }
}

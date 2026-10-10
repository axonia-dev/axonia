package dev.axonia.learning.adapter.out.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.expertise.api.MemberTagKind;
import dev.axonia.expertise.api.MemberTagStore;
import dev.axonia.expertise.api.MemberTagStores;
import dev.axonia.learning.application.port.out.LearningIntentRepository;
import dev.axonia.learning.domain.LearningIntent;

@Repository
public class LearningIntentRepositoryImpl implements LearningIntentRepository {

    private static final String COUNT_IN_SQL = """
            SELECT count(DISTINCT i.user_id)
              FROM learning_intent i
             WHERE i.organization_id = :org AND i.community_id = :communityId AND i.tag = :tag
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final MemberTagStore<LearningIntent> intents;

    LearningIntentRepositoryImpl(NamedParameterJdbcTemplate jdbc, MemberTagStores memberTagStores) {

        this.jdbc = jdbc;
        this.intents = memberTagStores.of(MemberTagKind.LEARNING_INTENT, LearningIntent::new);
    }

    @Override
    @Transactional
    public void lockLearner(String org, long communityId, String userId) {

        intents.lockMember(org, communityId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningIntent> inCommunity(String org, long communityId, String userId) {

        return intents.inCommunity(org, communityId, userId);
    }

    @Override
    @Transactional
    public void insert(String org, LearningIntent intent) {

        intents.insert(org, intent);
    }

    @Override
    @Transactional
    public void delete(String org, long communityId, String userId, Set<String> tags) {

        intents.delete(org, communityId, userId, tags);
    }

    @Override
    @Transactional
    public void deleteAllIn(String org, long communityId, String userId) {

        intents.deleteAllIn(org, communityId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningIntent> currentIn(String org, long communityId) {

        return intents.activeIn(org, communityId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> tagsIn(String org, long communityId) {

        return intents.tagsIn(org, communityId);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional
    public void removeTag(String org, long communityId, String tag) {

        intents.removeTag(org, communityId, tag);
    }

    private static MapSqlParameterSource scope(String org, long communityId) {

        return new MapSqlParameterSource("org", org).addValue("communityId", communityId);
    }
}

package dev.axonia.learning.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class LearningDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private LearningDataPurger purger;

    @Test
    void purgingAnOrganizationDeletesItsIntentsAndLeavesAnotherOrganizations() {

        // given
        seedIntent(ORG, "alice");
        seedIntent(ORG, "bob");
        seedIntent(OTHER_ORG, "alice");

        // when
        purger.purge(ORG);

        // then
        assertThat(count(ORG)).isZero();
        assertThat(count(OTHER_ORG)).isEqualTo(1);
    }

    private void seedIntent(String org, String userId) {

        jdbc.update("INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (:org, 1, :user, 'kafka', now())", Map.of("org", org, "user", userId));
    }

    private long count(String org) {

        return jdbc.queryForObject("SELECT count(*) FROM learning_intent WHERE organization_id = :org",
                Map.of("org", org), Long.class);
    }
}

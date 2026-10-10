package dev.axonia.bookmarks.adapter.out.privacy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class BookmarksDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private BookmarksDataPurger purger;

    @Test
    void purgeEmptiesTheBookmarkTableForTheOrganization() {

        // given
        seedBookmark();

        // when
        purger.purge(ORG);

        // then
        assertThat(count("bookmark")).isZero();
    }

    /**
     * Seeds one bookmark row for org "default".
     */
    private void seedBookmark() {

        jdbc.update("INSERT INTO bookmark (organization_id, community_id, saveable_type, saveable_id, member_user_id, "
                + "source_author_user_id, created_at, updated_at, version) VALUES (:org, 1, 'ARTIFACT', 1, "
                + "'member-user', 'author-user', now(), now(), 0)", Map.of("org", ORG));
    }

    private long count(String table) {

        return switch (table) {
            case "bookmark" -> jdbc.queryForObject("SELECT count(*) FROM bookmark WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            default -> throw new IllegalArgumentException("Unexpected org-scoped table: " + table);
        };
    }
}

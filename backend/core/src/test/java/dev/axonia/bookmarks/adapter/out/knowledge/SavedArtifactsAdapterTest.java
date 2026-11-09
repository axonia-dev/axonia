package dev.axonia.bookmarks.adapter.out.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class SavedArtifactsAdapterTest extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private SavedArtifactsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new SavedArtifactsAdapter(jdbc);
    }

    @Test
    void savedIn_answersOnlyTheMembersArtifactSavesInThatCommunity() {

        // given
        bookmark("default", 7L, "ARTIFACT", 100L, "bob");
        bookmark("default", 7L, "ARTIFACT", 101L, "bob");
        bookmark("default", 7L, "SESSION", 102L, "bob");
        bookmark("default", 8L, "ARTIFACT", 103L, "bob");
        bookmark("default", 7L, "ARTIFACT", 104L, "carol");
        bookmark("other", 7L, "ARTIFACT", 105L, "bob");

        // when
        var actual = sut.savedIn("default", "bob", 7L);

        // then
        assertThat(actual).containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    void savedIn_ofNothingSavedIsEmpty() {

        assertThat(sut.savedIn("default", "bob", 7L)).isEmpty();
    }

    private void bookmark(String organizationId, long communityId, String saveableType, long saveableId,
            String memberUserId) {

        jdbc.update("""
                INSERT INTO bookmark (organization_id, community_id, saveable_type, saveable_id, member_user_id,
                        source_author_user_id, created_at, updated_at, version)
                VALUES (:organizationId, :communityId, :saveableType, :saveableId, :memberUserId, 'alice', now(),
                        now(), 0)
                """,
                new MapSqlParameterSource().addValue("organizationId", organizationId)
                        .addValue("communityId", communityId).addValue("saveableType", saveableType)
                        .addValue("saveableId", saveableId).addValue("memberUserId", memberUserId));
    }
}

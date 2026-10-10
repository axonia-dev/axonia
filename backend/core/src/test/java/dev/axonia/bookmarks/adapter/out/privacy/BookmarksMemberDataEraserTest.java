package dev.axonia.bookmarks.adapter.out.privacy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class BookmarksMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";
    private static final String SURROGATE = "erased:test-surrogate";
    private static final Instant ERASED_AT = Instant.parse("2026-07-27T10:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private BookmarksMemberDataEraser eraser;

    @Test
    void deletesTheMembersOwnSavesAndAnonymisesTheirAuthorshipOnOtherPeoplesSaves() {

        seedBookmark(TARGET, TARGET); // their own save, of their own artifact
        seedBookmark(NEIGHBOUR, TARGET); // a neighbour's save, of the target's artifact
        seedBookmark(NEIGHBOUR, NEIGHBOUR); // a neighbour's save, untouched

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(counts.deleted()).isEqualTo(1);
        assertThat(counts.anonymised()).isEqualTo(1);
        assertThat(rows(TARGET, TARGET)).isZero();
        assertThat(rows(NEIGHBOUR, SURROGATE)).isEqualTo(1);
        assertThat(rows(NEIGHBOUR, NEIGHBOUR)).isEqualTo(1);
    }

    /**
     * An anonymised row must also come out of the erasure with a fresh {@code version} and an {@code updated_at} taken
     * from the erasure's own instant. Leaving the version alone lets a request that loaded the row first still satisfy
     * its own optimistic-lock predicate and write the pre-erasure subject straight back.
     */
    @Test
    void anAnonymisedSaveIsVersionBumpedAndStampedWithTheErasuresOwnInstant() {

        seedBookmark(NEIGHBOUR, TARGET);

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        var row = jdbc.queryForMap("SELECT version, updated_at FROM bookmark WHERE organization_id = :org "
                + "AND source_author_user_id = :surrogate", Map.of("org", ORG, "surrogate", SURROGATE));
        assertThat(row).containsEntry("version", 1L).containsEntry("updated_at", Timestamp.from(ERASED_AT));
    }

    private void seedBookmark(String member, String author) {

        // saveable_id distinguishes the artifact being saved; the unique constraint is per (org, member, saveable),
        // so it must vary whenever the same member saves a second, different artifact.
        var saveableId = author.equals(TARGET) ? 1 : 2;
        jdbc.update("""
                INSERT INTO bookmark (organization_id, community_id, member_user_id, source_author_user_id,
                                      saveable_type, saveable_id, created_at, updated_at, version)
                VALUES (:org, 1, :member, :author, 'ARTIFACT', :saveableId,
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, Map.of("org", ORG, "member", member, "author", author, "saveableId", saveableId));
    }

    private long rows(String member, String author) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM bookmark WHERE organization_id = :org "
                        + "AND member_user_id = :member AND source_author_user_id = :author",
                Map.of("org", ORG, "member", member, "author", author), Long.class);
    }
}

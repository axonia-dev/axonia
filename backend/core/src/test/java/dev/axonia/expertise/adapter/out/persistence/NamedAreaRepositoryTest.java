package dev.axonia.expertise.adapter.out.persistence;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.expertise.domain.NamedArea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Runs as the Testcontainers table owner, which bypasses RLS, so every org and community boundary asserted here is the
 * repository's own predicate rather than the policy's.
 */
class NamedAreaRepositoryTest extends ExpertiseJdbcTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";
    private static final long COMMUNITY = 1L;
    private static final long OTHER_COMMUNITY = 2L;
    private static final long UNKNOWN_ID = 999_999L;

    @Autowired
    private NamedAreaRepositoryImpl repo;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void findAll_returnsEachNamedAreaWithItsTagsOrderedByNameThenId() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        var design = repo.create(ORG, COMMUNITY, "Design");
        var secondDesign = repo.create(ORG, COMMUNITY, "Design");
        repo.place(ORG, COMMUNITY, streaming, "kafka");
        repo.place(ORG, COMMUNITY, streaming, "flink");
        repo.place(ORG, COMMUNITY, design, "ddd");

        assertThat(repo.findAll(ORG, COMMUNITY)).containsExactly(new NamedArea(design, "Design", List.of("ddd")),
                new NamedArea(secondDesign, "Design", List.of()),
                new NamedArea(streaming, "Streaming", List.of("flink", "kafka")));
    }

    @Test
    void findAll_isBoundedToOneCommunityOfOneOrganization() {

        var mine = repo.create(ORG, COMMUNITY, "Streaming");
        repo.create(ORG, OTHER_COMMUNITY, "Elsewhere");
        repo.create(OTHER_ORG, COMMUNITY, "Another tenant");

        assertThat(repo.findAll(ORG, COMMUNITY)).containsExactly(new NamedArea(mine, "Streaming", List.of()));
    }

    @Test
    void findAll_withNoNamedAreas_isEmpty() {

        assertThat(repo.findAll(ORG, COMMUNITY)).isEmpty();
    }

    @Test
    void create_returnsTheNewId_andRenameChangesOnlyTheNamedRow() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        var design = repo.create(ORG, COMMUNITY, "Design");

        var renamed = repo.rename(ORG, COMMUNITY, streaming, "Event streaming");

        assertThat(renamed).isTrue();
        assertThat(repo.findAll(ORG, COMMUNITY)).extracting(NamedArea::id, NamedArea::name)
                .containsExactly(tuple(design, "Design"), tuple(streaming, "Event streaming"));
    }

    @Test
    void rename_ofAnUnknownId_isFalse() {

        assertThat(repo.rename(ORG, COMMUNITY, UNKNOWN_ID, "Anything")).isFalse();
    }

    @Test
    void rename_ofAnotherCommunitysNamedArea_isFalseAndLeavesItsName() {

        var elsewhere = repo.create(ORG, OTHER_COMMUNITY, "Elsewhere");

        assertThat(repo.rename(ORG, COMMUNITY, elsewhere, "Taken over")).isFalse();
        assertThat(repo.findAll(ORG, OTHER_COMMUNITY)).extracting(NamedArea::name).containsExactly("Elsewhere");
    }

    @Test
    void delete_removesTheMembershipRowsWithIt_andLeavesAnotherNamedAreaIntact() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        var design = repo.create(ORG, COMMUNITY, "Design");
        repo.place(ORG, COMMUNITY, streaming, "kafka");
        repo.place(ORG, COMMUNITY, design, "ddd");

        var deleted = repo.delete(ORG, COMMUNITY, streaming);

        assertThat(deleted).isTrue();
        assertThat(repo.findAll(ORG, COMMUNITY)).containsExactly(new NamedArea(design, "Design", List.of("ddd")));
        assertThat(membershipRows()).containsExactly("ddd");
    }

    @Test
    void delete_ofAnUnknownId_isFalse() {

        assertThat(repo.delete(ORG, COMMUNITY, UNKNOWN_ID)).isFalse();
    }

    @Test
    void delete_ofAnotherCommunitysNamedArea_isFalseAndLeavesIt() {

        var elsewhere = repo.create(ORG, OTHER_COMMUNITY, "Elsewhere");

        assertThat(repo.delete(ORG, COMMUNITY, elsewhere)).isFalse();
        assertThat(repo.findAll(ORG, OTHER_COMMUNITY)).extracting(NamedArea::id).containsExactly(elsewhere);
    }

    @Test
    void place_movesATagAnotherNamedAreaHolds_inOneCall() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        var messaging = repo.create(ORG, COMMUNITY, "Messaging");
        repo.place(ORG, COMMUNITY, streaming, "kafka");

        var placed = repo.place(ORG, COMMUNITY, messaging, "kafka");

        assertThat(placed).isTrue();
        assertThat(repo.findAll(ORG, COMMUNITY)).containsExactly(
                new NamedArea(messaging, "Messaging", List.of("kafka")),
                new NamedArea(streaming, "Streaming", List.of()));
    }

    @Test
    void place_isIdempotentForATagTheSameNamedAreaHolds() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        repo.place(ORG, COMMUNITY, streaming, "kafka");

        var placed = repo.place(ORG, COMMUNITY, streaming, "kafka");

        assertThat(placed).isTrue();
        assertThat(repo.findAll(ORG, COMMUNITY))
                .containsExactly(new NamedArea(streaming, "Streaming", List.of("kafka")));
    }

    @Test
    void place_intoAnUnknownId_isFalseAndWritesNothing() {

        assertThat(repo.place(ORG, COMMUNITY, UNKNOWN_ID, "kafka")).isFalse();
        assertThat(membershipRows()).isEmpty();
    }

    @Test
    void place_intoAnotherCommunitysNamedArea_isFalseAndWritesNothing() {

        var elsewhere = repo.create(ORG, OTHER_COMMUNITY, "Elsewhere");

        assertThat(repo.place(ORG, COMMUNITY, elsewhere, "kafka")).isFalse();
        assertThat(membershipRows()).isEmpty();
    }

    @Test
    void place_intoAnotherOrganizationsNamedArea_isFalseAndWritesNothing() {

        var foreign = repo.create(OTHER_ORG, COMMUNITY, "Another tenant");

        assertThat(repo.place(ORG, COMMUNITY, foreign, "kafka")).isFalse();
        assertThat(membershipRows()).isEmpty();
    }

    @Test
    void remove_takesOneTagOutAndLeavesTheRest() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        repo.place(ORG, COMMUNITY, streaming, "kafka");
        repo.place(ORG, COMMUNITY, streaming, "flink");

        repo.remove(ORG, COMMUNITY, "kafka");

        assertThat(repo.findAll(ORG, COMMUNITY))
                .containsExactly(new NamedArea(streaming, "Streaming", List.of("flink")));
    }

    @Test
    void remove_leavesTheSameTagInAnotherCommunity() {

        var elsewhere = repo.create(ORG, OTHER_COMMUNITY, "Elsewhere");
        repo.place(ORG, OTHER_COMMUNITY, elsewhere, "kafka");

        repo.remove(ORG, COMMUNITY, "kafka");

        assertThat(repo.findAll(ORG, OTHER_COMMUNITY))
                .containsExactly(new NamedArea(elsewhere, "Elsewhere", List.of("kafka")));
    }

    @Test
    void removeNaming_clearsTheTagWhereverItSits() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        var design = repo.create(ORG, COMMUNITY, "Design");
        repo.place(ORG, COMMUNITY, streaming, "kafka");
        repo.place(ORG, COMMUNITY, design, "ddd");

        repo.removeNaming(ORG, COMMUNITY, "kafka");

        assertThat(repo.findAll(ORG, COMMUNITY)).containsExactly(new NamedArea(design, "Design", List.of("ddd")),
                new NamedArea(streaming, "Streaming", List.of()));
    }

    @Test
    void renameTag_carriesTheMembershipToTheNewName() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        repo.place(ORG, COMMUNITY, streaming, "apache-kafka");

        repo.renameTag(ORG, COMMUNITY, "apache-kafka", "kafka");

        assertThat(repo.findAll(ORG, COMMUNITY))
                .containsExactly(new NamedArea(streaming, "Streaming", List.of("kafka")));
    }

    @Test
    void renameTag_ontoANamePlacedElsewhere_keepsThatPlacementAndDropsTheOldRow() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        var messaging = repo.create(ORG, COMMUNITY, "Messaging");
        repo.place(ORG, COMMUNITY, streaming, "apache-kafka");
        repo.place(ORG, COMMUNITY, messaging, "kafka");

        repo.renameTag(ORG, COMMUNITY, "apache-kafka", "kafka");

        assertThat(repo.findAll(ORG, COMMUNITY)).containsExactly(
                new NamedArea(messaging, "Messaging", List.of("kafka")),
                new NamedArea(streaming, "Streaming", List.of()));
    }

    @Test
    void renameTag_ontoItself_keepsThePlacement() {

        var streaming = repo.create(ORG, COMMUNITY, "Streaming");
        repo.place(ORG, COMMUNITY, streaming, "kafka");

        repo.renameTag(ORG, COMMUNITY, "kafka", "kafka");

        assertThat(repo.findAll(ORG, COMMUNITY))
                .containsExactly(new NamedArea(streaming, "Streaming", List.of("kafka")));
    }

    @Test
    void renameTag_leavesTheSameTagInAnotherCommunity() {

        var elsewhere = repo.create(ORG, OTHER_COMMUNITY, "Elsewhere");
        repo.place(ORG, OTHER_COMMUNITY, elsewhere, "apache-kafka");

        repo.renameTag(ORG, COMMUNITY, "apache-kafka", "kafka");

        assertThat(repo.findAll(ORG, OTHER_COMMUNITY))
                .containsExactly(new NamedArea(elsewhere, "Elsewhere", List.of("apache-kafka")));
    }

    private List<String> membershipRows() {

        return jdbc.queryForList("SELECT canonical_tag FROM expertise_named_area_tag ORDER BY canonical_tag", Map.of(),
                String.class);
    }
}

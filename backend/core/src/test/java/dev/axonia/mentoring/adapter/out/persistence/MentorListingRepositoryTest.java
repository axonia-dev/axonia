package dev.axonia.mentoring.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.mentoring.domain.ListingChange;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentorListing;

import static org.assertj.core.api.Assertions.assertThat;

@Import(MentorListingRepositoryImpl.class)
class MentorListingRepositoryTest extends MentoringRepositoryTestSupport {

    private static final Instant EARLY = Instant.parse("2026-11-20T09:00:00Z");
    private static final Instant LATE = Instant.parse("2026-11-21T09:00:00Z");

    @Autowired
    private MentorListingRepositoryImpl repo;

    @Test
    void setSwitchUpserts() {

        // given
        var community = seedCommunity("platform");
        repo.setSwitch(ORG, change(community, "mia", ListingSwitch.ON, EARLY));

        // when
        repo.setSwitch(ORG, change(community, "mia", ListingSwitch.OFF, LATE));

        // then
        assertThat(repo.in(ORG, community)).containsOnlyKeys("mia")
                .containsValue(new MentorListing(community, "mia", ListingSwitch.OFF, null, null));
        assertThat(updatedAt(community, "mia")).isEqualTo(LATE);
    }

    @Test
    void setSwitchBackToDefaultStoresNoState() {

        // given
        var community = seedCommunity("platform");
        repo.setSwitch(ORG, change(community, "mia", ListingSwitch.OFF, EARLY));

        // when
        repo.setSwitch(ORG, change(community, "mia", ListingSwitch.DEFAULT, LATE));

        // then
        assertThat(repo.in(ORG, community).get("mia").state()).isEqualTo(ListingSwitch.DEFAULT);
        assertThat(jdbc.queryForObject("SELECT state FROM mentoring_mentor_listing WHERE user_id = 'mia'", Map.of(),
                String.class)).isNull();
    }

    @Test
    void markAskedKeepsState() {

        // given
        var community = seedCommunity("platform");
        repo.setSwitch(ORG, change(community, "mia", ListingSwitch.ON, EARLY));

        // when
        repo.markAsked(ORG, community, "mia", LATE);
        repo.markTold(ORG, community, "mia", LATE);

        // then
        assertThat(repo.in(ORG, community).get("mia"))
                .isEqualTo(new MentorListing(community, "mia", ListingSwitch.ON, LATE, LATE));
    }

    @Test
    void markAskedAndToldCreateADefaultRowAndKeepTheFirstMoment() {

        // given
        var community = seedCommunity("platform");
        repo.markAsked(ORG, community, "mia", EARLY);
        repo.markTold(ORG, community, "leo", EARLY);

        // when
        repo.markAsked(ORG, community, "mia", LATE);
        repo.markTold(ORG, community, "leo", LATE);

        // then
        assertThat(repo.in(ORG, community)).containsOnly(
                Map.entry("mia", new MentorListing(community, "mia", ListingSwitch.DEFAULT, EARLY, null)),
                Map.entry("leo", new MentorListing(community, "leo", ListingSwitch.DEFAULT, null, EARLY)));
    }

    @Test
    void inReturnsByUser() {

        // given
        var community = seedCommunity("platform");
        var elsewhere = seedCommunity("data");
        repo.setSwitch(ORG, change(community, "mia", ListingSwitch.ON, EARLY));
        repo.setSwitch(ORG, change(community, "leo", ListingSwitch.OFF, EARLY));
        repo.setSwitch(ORG, change(elsewhere, "zoe", ListingSwitch.ON, EARLY));
        repo.setSwitch("other-org", change(community, "max", ListingSwitch.ON, EARLY));

        // when
        var actual = repo.in(ORG, community);

        // then
        assertThat(actual).containsOnly(
                Map.entry("mia", new MentorListing(community, "mia", ListingSwitch.ON, null, null)),
                Map.entry("leo", new MentorListing(community, "leo", ListingSwitch.OFF, null, null)));
    }

    private static ListingChange change(long community, String userId, ListingSwitch state, Instant at) {

        return ListingChange.builder().communityId(community).userId(userId).state(state).at(at).build();
    }

    private Instant updatedAt(long community, String userId) {

        return jdbc.queryForObject(
                "SELECT updated_at FROM mentoring_mentor_listing WHERE organization_id = :org "
                        + "AND community_id = :community AND user_id = :user",
                Map.of("org", ORG, "community", community, "user", userId), Timestamp.class).toInstant();
    }
}

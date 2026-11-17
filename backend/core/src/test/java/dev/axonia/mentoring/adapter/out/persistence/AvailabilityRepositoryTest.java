package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.mentoring.domain.Availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(AvailabilityRepositoryImpl.class)
class AvailabilityRepositoryTest extends MentoringRepositoryTestSupport {

    private static final Instant EARLY = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant LATE = Instant.parse("2026-10-02T09:00:00Z");

    @Autowired
    private AvailabilityRepositoryImpl repo;

    @Test
    void upsertCreatesThenReplacesTheSameRow() {

        // given
        var community = seedCommunity("platform");
        repo.upsert(ORG, availability(community, "alice", true, 3, false, EARLY));

        // when
        repo.upsert(ORG, availability(community, "alice", false, null, true, LATE));

        // then
        assertThat(repo.find(ORG, community, "alice"))
                .contains(availability(community, "alice", false, null, true, LATE));
    }

    @Test
    void findIsEmptyForAnUnknownMember() {

        var community = seedCommunity("platform");

        assertThat(repo.find(ORG, community, "nobody")).isEmpty();
    }

    @Test
    void theMenteeLimitStaysWithinOneToTen() {

        var community = seedCommunity("platform");

        assertThatThrownBy(() -> repo.upsert(ORG, availability(community, "alice", true, 11, false, EARLY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listedInSkipsInactiveAndFormerMembers() {

        // given
        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        seedMember(community, "carol", false);
        seedUser("bob", true);
        repo.upsert(ORG, availability(community, "alice", true, null, false, EARLY));
        repo.upsert(ORG, availability(community, "bob", true, null, false, EARLY));
        repo.upsert(ORG, availability(community, "carol", true, null, false, EARLY));

        // when
        var actual = repo.listedIn(ORG, community);

        // then
        assertThat(actual).extracting(Availability::userId).containsExactly("alice");
    }

    @Test
    void listedInKeepsOnlyThoseOfferingOrSeeking() {

        // given
        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        seedMember(community, "bob", true);
        seedMember(community, "dora", true);
        repo.upsert(ORG, availability(community, "alice", true, null, false, EARLY));
        repo.upsert(ORG, availability(community, "bob", false, null, true, LATE));
        repo.upsert(ORG, availability(community, "dora", false, null, false, LATE));

        // when
        var actual = repo.listedIn(ORG, community);

        // then
        assertThat(actual).extracting(Availability::userId).containsExactly("alice", "bob");
    }

    @Test
    void findListedReadsOneActiveMemberUnderTheRulesOfTheListing() {

        // given
        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        seedMember(community, "carol", false);
        seedMember(community, "dora", true);
        repo.upsert(ORG, availability(community, "alice", true, null, false, EARLY));
        repo.upsert(ORG, availability(community, "carol", true, null, false, EARLY));
        repo.upsert(ORG, availability(community, "dora", false, null, false, EARLY));

        // when / then
        assertThat(repo.findListed(ORG, community, "alice")).map(Availability::userId).contains("alice");
        assertThat(repo.findListed(ORG, community, "carol")).isEmpty();
        assertThat(repo.findListed(ORG, community, "dora")).isEmpty();
        assertThat(repo.findListed(ORG, community, "nobody")).isEmpty();
    }

    @Test
    void deleteInRemovesOneMembersRowInOneCommunity() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.upsert(ORG, availability(platform, "alice", true, null, false, EARLY));
        repo.upsert(ORG, availability(data, "alice", true, null, false, EARLY));

        // when
        repo.deleteIn(ORG, platform, "alice");

        // then
        assertThat(repo.find(ORG, platform, "alice")).isEmpty();
        assertThat(repo.find(ORG, data, "alice")).isPresent();
    }

    private static Availability availability(long community, String user, boolean taking, Integer limit,
            boolean looking, Instant at) {

        return Availability.builder().communityId(community).userId(user).takingMentees(taking).menteeLimit(limit)
                .lookingForMentor(looking).updatedAt(at).build();
    }
}

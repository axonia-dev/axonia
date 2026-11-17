package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;

import static org.assertj.core.api.Assertions.assertThat;

@Import(ProposalRepositoryImpl.class)
class ProposalRepositoryTest extends MentoringRepositoryTestSupport {

    private static final Instant EARLY = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant LATE = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    private ProposalRepositoryImpl repo;

    @Test
    void insertThenFindRoundTripsTheTopicsAndTheMessage() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, proposal(community, "mia", "max", List.of("kafka", "on, call"), EARLY));

        // when
        var actual = repo.find(ORG, id);

        // then
        assertThat(actual).contains(Proposal.builder().id(id).communityId(community).mentorUserId("mia")
                .menteeUserId("max").proposedBy(Role.MENTEE).topics(List.of("kafka", "on, call")).message("Hello")
                .status(ProposalStatus.OPEN).createdAt(EARLY).build());
    }

    @Test
    void findIsEmptyForAnUnknownId() {

        assertThat(repo.find(ORG, 424242L)).isEmpty();
    }

    @Test
    void betweenReturnsEveryProposalOfThePairEitherWayNewestFirst() {

        // given
        var community = seedCommunity("platform");
        var first = repo.insert(ORG, proposal(community, "mia", "max", List.of(), EARLY));
        var newest = repo.insert(ORG, proposal(community, "mia", "max", List.of(), LATE));
        repo.insert(ORG, proposal(community, "mia", "zoe", List.of(), Instant.parse("2026-10-09T09:00:00Z")));

        // when
        var crossed = repo.insert(ORG,
                proposal(community, "max", "mia", List.of(), Instant.parse("2026-10-07T09:00:00Z")));

        // when
        var actual = repo.between(ORG, community, "mia", "max");

        // then
        assertThat(actual).map(Proposal::id).containsExactly(crossed, newest, first);
        assertThat(repo.between(ORG, community, "max", "mia")).map(Proposal::id).containsExactly(crossed, newest,
                first);
        assertThat(repo.between(ORG, community, "mia", "nobody")).isEmpty();
    }

    @Test
    void involvingListsBothSidesNewestFirst() {

        // given
        var community = seedCommunity("platform");
        var first = repo.insert(ORG, proposal(community, "mia", "max", List.of(), EARLY));
        var second = repo.insert(ORG, proposal(community, "max", "zoe", List.of(), LATE));
        repo.insert(ORG, proposal(community, "mia", "zoe", List.of(), LATE));

        // when
        var actual = repo.involving(ORG, "max");

        // then
        assertThat(actual).extracting(Proposal::id).containsExactly(second, first);
    }

    @Test
    void involvingInListsBothSidesInOneGuildNewestFirst() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        var first = repo.insert(ORG, proposal(platform, "mia", "max", List.of(), EARLY));
        var second = repo.insert(ORG, proposal(platform, "max", "zoe", List.of(), LATE));
        repo.insert(ORG, proposal(platform, "mia", "zoe", List.of(), LATE));
        repo.insert(ORG, proposal(data, "mia", "max", List.of(), LATE));

        // when
        var actual = repo.involvingIn(ORG, platform, "max");

        // then
        assertThat(actual).extracting(Proposal::id).containsExactly(second, first);
    }

    @Test
    void answerSetsTheStatusAndTheMoment() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, proposal(community, "mia", "max", List.of(), EARLY));

        // when
        var actual = repo.answer(ORG, id, ProposalStatus.DECLINED, LATE);

        // then
        assertThat(actual).isTrue();
        assertThat(repo.find(ORG, id)).get().satisfies(p -> {
            assertThat(p.status()).isEqualTo(ProposalStatus.DECLINED);
            assertThat(p.answeredAt()).isEqualTo(LATE);
        });
    }

    @Test
    void answerLeavesAProposalAlreadyAnsweredAlone() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, proposal(community, "mia", "max", List.of(), EARLY));
        repo.answer(ORG, id, ProposalStatus.WITHDRAWN, EARLY);

        // when
        var actual = repo.answer(ORG, id, ProposalStatus.ACCEPTED, LATE);

        // then
        assertThat(actual).isFalse();
        assertThat(repo.find(ORG, id)).get().satisfies(p -> {
            assertThat(p.status()).isEqualTo(ProposalStatus.WITHDRAWN);
            assertThat(p.answeredAt()).isEqualTo(EARLY);
        });
    }

    @Test
    void reopenOpensADeclinedProposalOnceAndStampsTheMoment() {

        // given
        var community = seedCommunity("platform");
        var declined = repo.insert(ORG, proposal(community, "mia", "max", List.of("kafka"), EARLY));
        repo.answer(ORG, declined, ProposalStatus.DECLINED, EARLY.plusSeconds(60));
        var open = repo.insert(ORG, proposal(community, "mia", "zoe", List.of("kafka"), EARLY));

        // when
        var first = repo.reopen(ORG, declined, LATE);
        var second = repo.reopen(ORG, declined, LATE.plusSeconds(60));
        var notDeclined = repo.reopen(ORG, open, LATE);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(notDeclined).isFalse();
        assertThat(repo.find(ORG, declined)).get()
                .extracting(Proposal::status, Proposal::answeredAt, Proposal::reopenedAt)
                .containsExactly(ProposalStatus.OPEN, null, LATE);
        assertThat(repo.find(ORG, open)).get().extracting(Proposal::reopenedAt).isNull();
    }

    @Test
    void withdrawOthersBetweenReachesBothDirectionsAndNobodyElse() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        var accepted = repo.insert(ORG, proposal(platform, "mia", "max", List.of(), EARLY));
        var sameWay = repo.insert(ORG, proposal(platform, "mia", "max", List.of(), LATE));
        var crossed = repo.insert(ORG, proposal(platform, "max", "mia", List.of(), LATE));
        var elsewhere = repo.insert(ORG, proposal(data, "mia", "max", List.of(), LATE));
        var someoneElse = repo.insert(ORG, proposal(platform, "mia", "zoe", List.of(), LATE));
        repo.answer(ORG, accepted, ProposalStatus.ACCEPTED, LATE);

        // when
        repo.withdrawOthersBetween(ORG, repo.find(ORG, accepted).orElseThrow(), LATE);

        // then
        assertThat(status(accepted)).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(status(sameWay)).isEqualTo(ProposalStatus.WITHDRAWN);
        assertThat(status(crossed)).isEqualTo(ProposalStatus.WITHDRAWN);
        assertThat(status(elsewhere)).isEqualTo(ProposalStatus.OPEN);
        assertThat(status(someoneElse)).isEqualTo(ProposalStatus.OPEN);
    }

    @Test
    void withdrawOpenInvolvingInOneCommunityLeavesTheOthersAndTheAnswered() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        var inPlatform = repo.insert(ORG, proposal(platform, "mia", "max", List.of(), EARLY));
        var inData = repo.insert(ORG, proposal(data, "mia", "max", List.of(), EARLY));
        var declined = repo.insert(ORG, proposal(platform, "mia", "zoe", List.of(), EARLY));
        repo.answer(ORG, declined, ProposalStatus.DECLINED, EARLY);

        // when
        repo.withdrawOpenInvolving(ORG, platform, "mia", LATE);

        // then
        assertThat(status(inPlatform)).isEqualTo(ProposalStatus.WITHDRAWN);
        assertThat(status(inData)).isEqualTo(ProposalStatus.OPEN);
        assertThat(status(declined)).isEqualTo(ProposalStatus.DECLINED);
    }

    @Test
    void withdrawOpenInvolvingWithNoCommunityReachesThemAll() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        var inPlatform = repo.insert(ORG, proposal(platform, "mia", "max", List.of(), EARLY));
        var inData = repo.insert(ORG, proposal(data, "zoe", "mia", List.of(), EARLY));
        var others = repo.insert(ORG, proposal(data, "zoe", "max", List.of(), EARLY));

        // when
        repo.withdrawOpenInvolving(ORG, null, "mia", LATE);

        // then
        assertThat(status(inPlatform)).isEqualTo(ProposalStatus.WITHDRAWN);
        assertThat(status(inData)).isEqualTo(ProposalStatus.WITHDRAWN);
        assertThat(status(others)).isEqualTo(ProposalStatus.OPEN);
    }

    private ProposalStatus status(long id) {

        return repo.find(ORG, id).orElseThrow().status();
    }

    private static Proposal proposal(long community, String mentor, String mentee, List<String> topics, Instant at) {

        return Proposal.builder().communityId(community).mentorUserId(mentor).menteeUserId(mentee)
                .proposedBy(Role.MENTEE).topics(topics).message("Hello").status(ProposalStatus.OPEN).createdAt(at)
                .build();
    }
}

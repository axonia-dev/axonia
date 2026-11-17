package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.NextMeeting;
import dev.axonia.mentoring.domain.Pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

@Import(PairingRepositoryImpl.class)
class PairingRepositoryTest extends MentoringRepositoryTestSupport {

    private static final Instant ENDED = Instant.parse("2026-11-01T09:00:00Z");

    @Autowired
    private PairingRepositoryImpl repo;

    @Test
    void insertThenFindRoundTripsAnOngoingPairing() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, pairing(community, "mia", "max").build());

        // when
        var actual = repo.find(ORG, id);

        // then
        assertThat(actual).contains(pairing(community, "mia", "max").id(id).build());
    }

    @Test
    void anAlreadyEndedPairingRoundTripsItsReason() {

        // given
        var community = seedCommunity("platform");
        var ended = pairing(community, "mia", "max").endedAt(ENDED).endReason(EndReason.MENTEE).build();
        var id = repo.insert(ORG, ended);

        // when
        var actual = repo.find(ORG, id);

        // then
        assertThat(actual)
                .contains(pairing(community, "mia", "max").id(id).endedAt(ENDED).endReason(EndReason.MENTEE).build());
    }

    @Test
    void findIsEmptyForAnUnknownId() {

        assertThat(repo.find(ORG, 424242L)).isEmpty();
    }

    @Test
    void theOngoingPairIndexRefusesASecondOngoingPairing() {

        // given
        var community = seedCommunity("platform");
        repo.insert(ORG, pairing(community, "mia", "max").build());

        // when / then
        assertThatThrownBy(() -> repo.insert(ORG, pairing(community, "mia", "max").build()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void anEndedPairingNoLongerBlocksTheSameTwoFromPairingAgain() {

        // given
        var community = seedCommunity("platform");
        var first = repo.insert(ORG, pairing(community, "mia", "max").build());
        repo.end(ORG, first, EndReason.MENTOR, ENDED);

        // when / then
        assertThatCode(() -> repo.insert(ORG, pairing(community, "mia", "max").build())).doesNotThrowAnyException();
    }

    @Test
    void endRecordsTheReasonAndTheMoment() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, pairing(community, "mia", "max").build());

        // when
        var actual = repo.end(ORG, id, EndReason.LEFT, ENDED);

        // then
        assertThat(actual).isTrue();
        assertThat(repo.find(ORG, id)).get().satisfies(p -> {
            assertThat(p.endedAt()).isEqualTo(ENDED);
            assertThat(p.endReason()).isEqualTo(EndReason.LEFT);
        });
    }

    @Test
    void theNextMeetingIsSetChangedAndCleared() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, pairing(community, "mia", "max").build());
        var at = Instant.parse("2026-11-16T10:00:00.123Z");

        // when
        repo.setNextMeeting(ORG, id, new NextMeeting(LocalDate.parse("2026-11-19"), "max", at));

        // then
        assertThat(repo.find(ORG, id)).get().satisfies(p -> {
            assertThat(p.nextMeetingOn()).isEqualTo(LocalDate.parse("2026-11-19"));
            assertThat(p.nextMeetingSetBy()).isEqualTo("max");
            assertThat(p.nextMeetingSetAt()).isEqualTo(at);
        });

        // and when
        repo.clearNextMeeting(ORG, id);

        // then
        assertThat(repo.find(ORG, id)).get().satisfies(p -> {
            assertThat(p.nextMeetingOn()).isNull();
            assertThat(p.nextMeetingSetBy()).isNull();
            assertThat(p.nextMeetingSetAt()).isNull();
        });
    }

    @Test
    void withNextMeetingBetweenReadsOngoingPairingsInTheWindowOnly() {

        // given
        var community = seedCommunity("platform");
        var inside = repo.insert(ORG, pairing(community, "mia", "max").build());
        var lastDay = repo.insert(ORG, pairing(community, "mia", "lea").build());
        var after = repo.insert(ORG, pairing(community, "mia", "zoe").build());
        var ended = repo.insert(ORG, pairing(community, "mia", "ana").build());
        repo.insert(ORG, pairing(community, "mia", "tom").build());
        var at = Instant.parse("2026-11-16T10:00:00Z");
        repo.setNextMeeting(ORG, inside, new NextMeeting(LocalDate.parse("2026-11-18"), "max", at));
        repo.setNextMeeting(ORG, lastDay, new NextMeeting(LocalDate.parse("2026-11-20"), "lea", at));
        repo.setNextMeeting(ORG, after, new NextMeeting(LocalDate.parse("2026-11-21"), "zoe", at));
        repo.setNextMeeting(ORG, ended, new NextMeeting(LocalDate.parse("2026-11-19"), "ana", at));
        repo.end(ORG, ended, EndReason.MENTOR, at);

        // when
        var actual = repo.withNextMeetingBetween(ORG, LocalDate.parse("2026-11-18"), LocalDate.parse("2026-11-20"));

        // then
        assertThat(actual).extracting(Pairing::id).containsExactly(inside, lastDay);
        assertThat(repo.withNextMeetingBetween("another-org", LocalDate.parse("2026-11-18"),
                LocalDate.parse("2026-11-20"))).isEmpty();
    }

    @Test
    void endingClearsTheNextMeeting() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, pairing(community, "mia", "max").build());
        repo.setNextMeeting(ORG, id, new NextMeeting(LocalDate.parse("2026-11-19"), "max", ENDED.minusSeconds(60)));

        // when
        repo.end(ORG, id, EndReason.MENTOR, ENDED);

        // then
        assertThat(repo.find(ORG, id)).get().satisfies(p -> {
            assertThat(p.nextMeetingOn()).isNull();
            assertThat(p.nextMeetingSetBy()).isNull();
            assertThat(p.nextMeetingSetAt()).isNull();
        });
    }

    @Test
    void endingAnEndedPairingChangesNothingAndSaysSo() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, pairing(community, "mia", "max").build());
        repo.end(ORG, id, EndReason.MENTOR, ENDED);

        // when
        var actual = repo.end(ORG, id, EndReason.DEACTIVATED, ENDED.plusSeconds(60));

        // then
        assertThat(actual).isFalse();
        assertThat(repo.find(ORG, id)).get().satisfies(p -> {
            assertThat(p.endedAt()).isEqualTo(ENDED);
            assertThat(p.endReason()).isEqualTo(EndReason.MENTOR);
        });
    }

    @Test
    void ongoingAsMentorCountsOnlyOngoingInTheCommunity() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, pairing(platform, "mia", "max").build());
        repo.insert(ORG, pairing(platform, "mia", "zoe").build());
        var ended = repo.insert(ORG, pairing(platform, "mia", "lea").build());
        repo.end(ORG, ended, EndReason.MENTEE, ENDED);
        repo.insert(ORG, pairing(data, "mia", "max").build());
        repo.insert(ORG, pairing(platform, "max", "mia").build());

        // when / then
        assertThat(repo.ongoingAsMentor(ORG, platform, "mia")).isEqualTo(2);
        assertThat(repo.ongoingAsMentor(ORG, platform, "nobody")).isZero();
    }

    @Test
    void involvingListsBothRolesNewestFirstIncludingEnded() {

        // given
        var community = seedCommunity("platform");
        var older = repo.insert(ORG, pairing(community, "mia", "max").build());
        repo.end(ORG, older, EndReason.MENTOR, ENDED);
        var newer = repo.insert(ORG,
                pairing(community, "zoe", "mia").startedAt(Instant.parse("2026-10-20T09:00:00Z")).build());
        repo.insert(ORG, pairing(community, "zoe", "max").build());

        // when
        var actual = repo.involving(ORG, "mia");

        // then
        assertThat(actual).extracting(Pairing::id).containsExactly(newer, older);
    }

    @Test
    void ongoingInvolvingFiltersByCommunityOnlyWhenGiven() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        var inPlatform = repo.insert(ORG, pairing(platform, "mia", "max").build());
        var inData = repo.insert(ORG,
                pairing(data, "zoe", "mia").startedAt(Instant.parse("2026-10-20T09:00:00Z")).build());
        var ended = repo.insert(ORG, pairing(platform, "mia", "lea").build());
        repo.end(ORG, ended, EndReason.MENTOR, ENDED);

        // when / then
        assertThat(repo.ongoingInvolving(ORG, platform, "mia")).extracting(Pairing::id).containsExactly(inPlatform);
        assertThat(repo.ongoingInvolving(ORG, null, "mia")).extracting(Pairing::id).containsExactly(inData, inPlatform);
    }

    @Test
    void findForWriteReadsThePairingOfTheOrganization() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, pairing(community, "mia", "max").build());

        // when / then
        assertThat(repo.findForWrite(ORG, id)).contains(pairing(community, "mia", "max").id(id).build());
        assertThat(repo.findForWrite("other-org", id)).isEmpty();
    }

    @Test
    void ongoingAsMentorInCountsEachMentorsOngoingPairingsInOneGuild() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, pairing(platform, "mia", "max").build());
        repo.insert(ORG, pairing(platform, "mia", "noah").build());
        repo.insert(ORG, pairing(platform, "lea", "max").build());
        repo.insert(ORG, pairing(platform, null, "zoe").build());
        repo.end(ORG, repo.insert(ORG, pairing(platform, "tom", "max").build()), EndReason.MENTOR, ENDED);
        repo.insert(ORG, pairing(data, "tom", "max").build());

        // when
        var actual = repo.ongoingAsMentorIn(ORG, platform);

        // then
        assertThat(actual).containsOnly(entry("mia", 2), entry("lea", 1));
    }

    @Test
    void anyMentoringInvolvingSeesAPairingOrAProposalOfEitherSide() {

        // given
        var community = seedCommunity("platform");
        repo.end(ORG, repo.insert(ORG, pairing(community, "mia", "max").build()), EndReason.MENTOR, ENDED);
        jdbc.update("""
                INSERT INTO mentoring_proposal (organization_id, community_id, mentor_user_id, mentee_user_id,
                                                proposed_by, topics, message, status, created_at)
                VALUES (:org, :community, 'lea', 'noah', 'MENTEE', ARRAY['Kafka'], 'Hello', 'OPEN', :at)
                """, new MapSqlParameterSource("org", ORG).addValue("community", community).addValue("at",
                java.sql.Timestamp.from(ENDED)));

        // when / then
        assertThat(repo.anyMentoringInvolving(ORG, "max")).isTrue();
        assertThat(repo.anyMentoringInvolving(ORG, "mia")).isTrue();
        assertThat(repo.anyMentoringInvolving(ORG, "lea")).isTrue();
        assertThat(repo.anyMentoringInvolving(ORG, "noah")).isTrue();
        assertThat(repo.anyMentoringInvolving(ORG, "zoe")).isFalse();
        assertThat(repo.anyMentoringInvolving("other-org", "max")).isFalse();
    }

    @Test
    void lockMemberIsReentrantInOneTransaction() {

        var community = seedCommunity("platform");

        assertThatCode(() -> {
            repo.lockMember(ORG, community, "mia");
            repo.lockMember(ORG, community, "mia");
        }).doesNotThrowAnyException();
    }

    @Test
    void anErasedSideReadsBackAsNull() {

        // given
        var community = seedCommunity("platform");
        var id = repo.insert(ORG, pairing(community, "mia", null).build());

        // when
        var actual = repo.find(ORG, id);

        // then
        assertThat(actual).get().extracting(Pairing::menteeUserId).isNull();
    }
}

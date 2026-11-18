package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.Note;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@Import({MeetingRepositoryImpl.class, JournalRepositoryImpl.class, PairingRepositoryImpl.class})
class MeetingRepositoryTest extends MentoringRepositoryTestSupport {

    private static final Instant PLANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-11-26");

    @Autowired
    private MeetingRepositoryImpl repo;

    @Autowired
    private JournalRepositoryImpl journal;

    @Autowired
    private PairingRepositoryImpl pairings;

    private long community;

    @BeforeEach
    void seed() {

        community = seedCommunity("platform");
    }

    @Test
    void insertedMeetingReadsBackWithItsTopics() {

        // given
        var pairing = seedPairing("mia", "max");
        var first = topic(pairing, "Kafka");
        var second = topic(pairing, "Feedback");
        var id = repo.insert(ORG, meeting(pairing, DAY, first, second));

        // when
        var actual = repo.find(ORG, id);

        // then
        assertThat(actual).contains(meeting(pairing, DAY, first, second).toBuilder().id(id).build());
    }

    @Test
    void findIsEmptyForAnUnknownMeeting() {

        assertThat(repo.find(ORG, 424242L)).isEmpty();
    }

    @Test
    void updateMovesTheMeetingAndReplacesItsTopics() {

        // given
        var pairing = seedPairing("mia", "max");
        var kept = topic(pairing, "Kafka");
        var dropped = topic(pairing, "Feedback");
        var id = repo.insert(ORG, meeting(pairing, DAY, kept, dropped));

        // when
        repo.update(ORG, meeting(pairing, DAY.plusDays(2), kept).toBuilder().id(id).plannedBy("max").build());

        // then
        assertThat(repo.find(ORG, id))
                .contains(meeting(pairing, DAY.plusDays(2), kept).toBuilder().id(id).plannedBy("max").build());
    }

    @Test
    void aMeetingNamesNoTopicOfAnotherPairing() {

        // given
        var pairing = seedPairing("mia", "max");
        var foreign = topic(seedPairing("mia", "lea"), "Elsewhere");
        var own = topic(pairing, "Kafka");

        // when
        var id = repo.insert(ORG, meeting(pairing, DAY, own, foreign));

        // then
        assertThat(repo.find(ORG, id)).get().extracting(PlannedMeeting::topicIds).isEqualTo(List.of(own));
    }

    @Test
    void aSecondMeetingOnTheSameDayIsRefused() {

        // given
        var pairing = seedPairing("mia", "max");
        repo.insert(ORG, meeting(pairing, DAY));

        // when / then
        assertThatThrownBy(() -> repo.insert(ORG, meeting(pairing, DAY))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aMeetingWithANoteOnItsDayIsHeld() {

        // given
        var pairing = seedPairing("mia", "max");
        var held = repo.insert(ORG, meeting(pairing, DAY));
        var open = repo.insert(ORG, meeting(pairing, DAY.plusDays(1)));
        note(pairing, DAY);

        // when
        var actual = repo.meetingsOf(ORG, pairing);

        // then
        assertThat(actual).extracting(PlannedMeeting::id, PlannedMeeting::held).containsExactly(tuple(held, true),
                tuple(open, false));
    }

    @Test
    void aNoteOfAnotherPairingHoldsNothing() {

        // given
        var pairing = seedPairing("mia", "max");
        var id = repo.insert(ORG, meeting(pairing, DAY));
        note(seedPairing("mia", "lea"), DAY);

        // when / then
        assertThat(repo.find(ORG, id)).get().extracting(PlannedMeeting::held).isEqualTo(false);
    }

    @Test
    void aDeletedTopicLeavesItsMeetings() {

        // given
        var pairing = seedPairing("mia", "max");
        var gone = topic(pairing, "Kafka");
        var stays = topic(pairing, "Feedback");
        var id = repo.insert(ORG, meeting(pairing, DAY, gone, stays));

        // when
        journal.deleteTopic(ORG, pairing, gone);

        // then
        assertThat(repo.find(ORG, id)).get().extracting(PlannedMeeting::topicIds).isEqualTo(List.of(stays));
    }

    @Test
    void aDoneTopicStaysInItsMeetings() {

        // given
        var pairing = seedPairing("mia", "max");
        var done = topic(pairing, "Kafka");
        var id = repo.insert(ORG, meeting(pairing, DAY, done));

        // when
        journal.updateTopic(ORG, journal.topicsOf(ORG, pairing).get(0).toBuilder().state(TopicState.DONE).build());

        // then
        assertThat(repo.find(ORG, id)).get().extracting(PlannedMeeting::topicIds).isEqualTo(List.of(done));
    }

    @Test
    void deleteRemovesOnlyTheMeetingOfThatPairing() {

        // given
        var pairing = seedPairing("mia", "max");
        var other = seedPairing("mia", "lea");
        var id = repo.insert(ORG, meeting(pairing, DAY));

        // when
        repo.delete(ORG, other, id);

        // then
        assertThat(repo.find(ORG, id)).isPresent();

        // when
        repo.delete(ORG, pairing, id);

        // then
        assertThat(repo.find(ORG, id)).isEmpty();
    }

    @Test
    void meetingsOfAPairingComeDayAscending() {

        // given
        var pairing = seedPairing("mia", "max");
        repo.insert(ORG, meeting(pairing, DAY.plusDays(5)));
        repo.insert(ORG, meeting(pairing, DAY));

        // when
        var actual = repo.meetingsOf(ORG, pairing);

        // then
        assertThat(actual).extracting(PlannedMeeting::on).containsExactly(DAY, DAY.plusDays(5));
    }

    @Test
    void meetingsOfManyPairingsGroupsByPairing() {

        // given
        var first = seedPairing("mia", "max");
        var second = seedPairing("mia", "lea");
        var none = seedPairing("mia", "zoe");
        repo.insert(ORG, meeting(first, DAY.plusDays(1)));
        repo.insert(ORG, meeting(first, DAY));
        repo.insert(ORG, meeting(second, DAY));

        // when
        var actual = repo.meetingsOf(ORG, List.of(first, second, none));

        // then
        assertThat(actual).containsOnlyKeys(first, second);
        assertThat(actual.get(first)).extracting(PlannedMeeting::on).containsExactly(DAY, DAY.plusDays(1));
        assertThat(actual.get(second)).extracting(PlannedMeeting::on).containsExactly(DAY);
    }

    @Test
    void meetingsOfNoPairingsIsEmpty() {

        assertThat(repo.meetingsOf(ORG, List.of())).isEmpty();
    }

    @Test
    void notHeldBetweenSkipsEndedPairingsAndHeldDays() {

        // given
        var ongoing = seedPairing("mia", "max");
        var ended = seedPairing("mia", "lea");
        var wanted = repo.insert(ORG, meeting(ongoing, DAY));
        repo.insert(ORG, meeting(ongoing, DAY.plusDays(1)));
        repo.insert(ORG, meeting(ongoing, DAY.plusDays(9)));
        repo.insert(ORG, meeting(ended, DAY));
        note(ongoing, DAY.plusDays(1));
        pairings.end(ORG, ended, EndReason.MENTOR, PLANNED_AT);

        // when
        var actual = repo.notHeldBetween(ORG, DAY.minusDays(1), DAY.plusDays(2));

        // then
        assertThat(actual).extracting(PlannedMeeting::id).containsExactly(wanted);
    }

    @Test
    void deleteUpcomingKeepsPassedAndHeld() {

        // given
        var pairing = seedPairing("mia", "max");
        var other = seedPairing("mia", "lea");
        var passed = repo.insert(ORG, meeting(pairing, DAY.minusDays(1)));
        var held = repo.insert(ORG, meeting(pairing, DAY));
        repo.insert(ORG, meeting(pairing, DAY.plusDays(1)));
        var elsewhere = repo.insert(ORG, meeting(other, DAY.plusDays(1)));
        note(pairing, DAY);

        // when
        repo.deleteUpcoming(ORG, pairing, DAY);

        // then
        assertThat(repo.meetingsOf(ORG, pairing)).extracting(PlannedMeeting::id).containsExactly(passed, held);
        assertThat(repo.find(ORG, elsewhere)).isPresent();
    }

    private long seedPairing(String mentor, String mentee) {

        return pairings.insert(ORG, pairing(community, mentor, mentee).build());
    }

    private long topic(long pairing, String label) {

        return journal.insertTopic(ORG, Topic.builder().pairingId(pairing).label(label).state(TopicState.TODO)
                .position(0).createdAt(PLANNED_AT).build());
    }

    private void note(long pairing, LocalDate day) {

        journal.insertNote(ORG, Note.builder().pairingId(pairing).authorUserId("mia").body("Met").meetingOn(day)
                .writtenAt(PLANNED_AT).build());
    }

    private static PlannedMeeting meeting(long pairing, LocalDate day, Long... topicIds) {

        return PlannedMeeting.builder().pairingId(pairing).on(day).topicIds(List.of(topicIds)).plannedBy("mia")
                .plannedAt(PLANNED_AT).build();
    }
}

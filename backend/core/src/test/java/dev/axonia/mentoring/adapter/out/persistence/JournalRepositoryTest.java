package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.mentoring.domain.Note;
import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.PointerKind;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicBoard;
import dev.axonia.mentoring.domain.TopicState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

@Import({JournalRepositoryImpl.class, PairingRepositoryImpl.class, WordsRepositoryImpl.class})
class JournalRepositoryTest extends MentoringRepositoryTestSupport {

    private static final Instant EARLY = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant LATE = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    private JournalRepositoryImpl repo;

    @Autowired
    private PairingRepositoryImpl pairings;

    @Autowired
    private WordsRepositoryImpl words;

    @Test
    void topicsRoundTripInPositionOrder() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        repo.insertTopic(ORG, topic(pairing, "Second", "kafka", 1));
        var first = repo.insertTopic(ORG, topic(pairing, "First", null, 0));

        // when
        var actual = repo.topicsOf(ORG, pairing);

        // then
        assertThat(actual).extracting(Topic::label).containsExactly("First", "Second");
        assertThat(actual.get(0)).isEqualTo(topic(pairing, "First", null, 0).toBuilder().id(first).build());
    }

    @Test
    void updateTopicChangesLabelAreaStateAndPosition() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var id = repo.insertTopic(ORG, topic(pairing, "Old", null, 0));

        // when
        repo.updateTopic(ORG, topic(pairing, "New", "kafka", 4).toBuilder().id(id).state(TopicState.DONE).build());

        // then
        assertThat(repo.topicsOf(ORG, pairing)).singleElement().satisfies(t -> {
            assertThat(t.label()).isEqualTo("New");
            assertThat(t.area()).isEqualTo("kafka");
            assertThat(t.state()).isEqualTo(TopicState.DONE);
            assertThat(t.position()).isEqualTo(4);
        });
    }

    @Test
    void concurrentPlacementsKeepEachColumnDense() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var a = repo.insertTopic(ORG, topic(pairing, "A", null, 0));
        var b = repo.insertTopic(ORG, topic(pairing, "B", null, 1));
        var c = repo.insertTopic(ORG, topic(pairing, "C", null, 2));
        var placed = TopicBoard.place(repo.topicsOf(ORG, pairing), c, TopicState.TODO, 0);

        // when
        repo.placeTopics(ORG, pairing, placed);
        jdbc.update("SET CONSTRAINTS uq_mentoring_topic_slot IMMEDIATE", Map.of());

        // then
        assertThat(repo.topicsOf(ORG, pairing)).extracting(Topic::id, Topic::position).containsExactly(tuple(c, 0),
                tuple(a, 1), tuple(b, 2));

        // and when - a second writer that read the board before the first one placed C
        jdbc.update("SET CONSTRAINTS uq_mentoring_topic_slot DEFERRED", Map.of());
        repo.placeTopics(ORG, pairing, List.of(topic(pairing, "B", null, 0).toBuilder().id(b).build()));

        // then
        assertThatThrownBy(() -> jdbc.update("SET CONSTRAINTS uq_mentoring_topic_slot IMMEDIATE", Map.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void placeTopicsMovesATopicToAnotherColumn() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var id = repo.insertTopic(ORG, topic(pairing, "A", null, 0));

        // when
        repo.placeTopics(ORG, pairing,
                List.of(topic(pairing, "A", null, 0).toBuilder().id(id).state(TopicState.DONE).build()));

        // then
        assertThat(repo.topicsOf(ORG, pairing)).singleElement().extracting(Topic::state).isEqualTo(TopicState.DONE);
    }

    @Test
    void deleteTopicIsScopedToItsPairing() {

        // given
        var community = seedCommunity("platform");
        var pairing = seedPairing(community);
        var other = seedPairing(community, "zoe", "lea");
        var id = repo.insertTopic(ORG, topic(pairing, "Gone", null, 0));

        // when
        repo.deleteTopic(ORG, other, id);
        assertThat(repo.topicsOf(ORG, pairing)).hasSize(1);
        repo.deleteTopic(ORG, pairing, id);

        // then
        assertThat(repo.topicsOf(ORG, pairing)).isEmpty();
    }

    @Test
    void trailsOfReadsManyPairingsInOneQuery() {

        // given
        var community = seedCommunity("platform");
        var walked = seedPairing(community, "mia", "max");
        var started = seedPairing(community, "mia", "zoe");
        var bare = seedPairing(community, "lea", "max");
        repo.insertTopic(ORG, topic(walked, "Later", null, 0));
        repo.insertTopic(ORG, topic(walked, "Now", null, 0).toBuilder().state(TopicState.IN_PROGRESS).build());
        repo.insertTopic(ORG, topic(walked, "Second done", null, 1).toBuilder().state(TopicState.DONE).build());
        repo.insertTopic(ORG, topic(walked, "First done", null, 0).toBuilder().state(TopicState.DONE).build());
        repo.insertTopic(ORG, topic(started, "Kafka", null, 0));

        // when
        var actual = repo.trailsOf(ORG, List.of(walked, started, bare));

        // then
        assertThat(actual).containsOnlyKeys(walked, started);
        assertThat(actual.get(walked)).extracting(Topic::state, Topic::label).containsExactly(
                tuple(TopicState.DONE, "First done"), tuple(TopicState.DONE, "Second done"),
                tuple(TopicState.IN_PROGRESS, "Now"), tuple(TopicState.TODO, "Later"));
        assertThat(actual.get(started)).extracting(Topic::state, Topic::label)
                .containsExactly(tuple(TopicState.TODO, "Kafka"));
        assertThat(repo.trailsOf("another-org", List.of(walked))).isEmpty();
        assertThat(repo.trailsOf(ORG, List.of())).isEmpty();
    }

    @Test
    void tallyOfCountsNotesAndDistinctMeetingDays() {

        // given
        var community = seedCommunity("platform");
        var met = seedPairing(community, "mia", "max");
        var silent = seedPairing(community, "mia", "zoe");
        var firstMeeting = LocalDate.parse("2026-11-02");
        var secondMeeting = LocalDate.parse("2026-11-09");
        repo.insertNote(ORG, note(met, "mentor's", EARLY, firstMeeting));
        repo.insertNote(ORG, note(met, "mentee's", LATE, firstMeeting).toBuilder().authorUserId("max").build());
        repo.insertNote(ORG, note(met, "again", LATE, secondMeeting));

        // when
        var actual = repo.tallyOf(ORG, List.of(met, silent));

        // then
        assertThat(actual).containsOnly(entry(met, new NoteTally(3, 2, secondMeeting)));
        assertThat(repo.tallyOf("another-org", List.of(met))).isEmpty();
        assertThat(repo.tallyOf(ORG, List.of())).isEmpty();
    }

    @Test
    void notesRoundTripNewestFirstAndCanBeEditedAndDeleted() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var older = repo.insertNote(ORG, note(pairing, "older", EARLY, null));
        var newer = repo.insertNote(ORG, note(pairing, "newer", LATE, LocalDate.parse("2026-11-20")));

        // when
        repo.updateNote(ORG, note(pairing, "edited", LATE, null).toBuilder().id(older).editedAt(LATE).build());

        // then
        var actual = repo.notesOf(ORG, pairing);
        assertThat(actual).extracting(Note::id).containsExactly(newer, older);
        assertThat(actual.get(0).meetingOn()).isEqualTo(LocalDate.parse("2026-11-20"));
        assertThat(actual.get(1)).satisfies(n -> {
            assertThat(n.body()).isEqualTo("edited");
            assertThat(n.editedAt()).isEqualTo(LATE);
        });

        // and when
        repo.deleteNote(ORG, pairing, older);

        // then
        assertThat(repo.notesOf(ORG, pairing)).extracting(Note::id).containsExactly(newer);
    }

    @Test
    void notesReadByMeetingDayBeforeWhenTheyWereWritten() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var writtenLateAboutAnEarlyMeeting = repo.insertNote(ORG,
                note(pairing, "late", LATE, LocalDate.parse("2026-10-01")));
        var writtenEarlyAboutALaterMeeting = repo.insertNote(ORG,
                note(pairing, "early", EARLY, LocalDate.parse("2026-10-02")));
        var sameDayWrittenLater = repo.insertNote(ORG, note(pairing, "later", LATE, LocalDate.parse("2026-10-02")));

        // when
        var actual = repo.notesOf(ORG, pairing);

        // then
        assertThat(actual).extracting(Note::id).containsExactly(sameDayWrittenLater, writtenEarlyAboutALaterMeeting,
                writtenLateAboutAnEarlyMeeting);
    }

    @Test
    void aNoteNamesTopicsOfItsPairingAndEditingReplacesThem() {

        // given
        var community = seedCommunity("platform");
        var pairing = seedPairing(community);
        var other = seedPairing(community, "zoe", "lea");
        var kafka = repo.insertTopic(ORG, topic(pairing, "Kafka", null, 0));
        var helm = repo.insertTopic(ORG, topic(pairing, "Helm", null, 1));
        var elsewhere = repo.insertTopic(ORG, topic(other, "Elsewhere", null, 0));
        var noteId = repo.insertNote(ORG, note(pairing, "note", EARLY, null));
        var bare = repo.insertNote(ORG, note(pairing, "bare", EARLY, null));

        // when
        repo.setNoteTopics(ORG, pairing, noteId, List.of(kafka, helm, elsewhere));

        // then
        assertThat(repo.noteTopicsOf(ORG, pairing)).containsOnly(entry(noteId, List.of(kafka, helm)))
                .doesNotContainKey(bare);

        // and when
        repo.setNoteTopics(ORG, pairing, noteId, List.of(helm));
        repo.setNoteTopics(ORG, other, noteId, List.of(elsewhere));

        // then
        assertThat(repo.noteTopicsOf(ORG, pairing)).containsOnly(entry(noteId, List.of(helm)));
        assertThat(repo.noteTopicsOf(ORG, other)).isEmpty();
    }

    @Test
    void removingATopicUnlinksItFromNotes() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var kafka = repo.insertTopic(ORG, topic(pairing, "Kafka", null, 0));
        var helm = repo.insertTopic(ORG, topic(pairing, "Helm", null, 1));
        var noteId = repo.insertNote(ORG, note(pairing, "note", EARLY, null));
        repo.setNoteTopics(ORG, pairing, noteId, List.of(kafka, helm));

        // when
        repo.deleteTopic(ORG, pairing, kafka);

        // then
        assertThat(repo.noteTopicsOf(ORG, pairing)).containsOnly(entry(noteId, List.of(helm)));
        assertThat(repo.notesOf(ORG, pairing)).extracting(Note::id).containsExactly(noteId);
    }

    @Test
    void pointersRoundTripOnATopicAndOnANote() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var topicId = repo.insertTopic(ORG, topic(pairing, "Topic", null, 0));
        var noteId = repo.insertNote(ORG, note(pairing, "note", EARLY, null));
        var onTopic = repo.insertPointer(ORG, Pointer.builder().pairingId(pairing).topicId(topicId)
                .kind(PointerKind.SESSION).targetId(7L).createdAt(EARLY).build());
        var onNote = repo.insertPointer(ORG, Pointer.builder().pairingId(pairing).noteId(noteId).kind(PointerKind.LINK)
                .url("https://example.test/a").createdAt(LATE).build());

        // when
        var actual = repo.pointersOf(ORG, pairing);

        // then
        assertThat(actual).extracting(Pointer::id).containsExactly(onTopic, onNote);
        assertThat(actual.get(0)).satisfies(p -> {
            assertThat(p.topicId()).isEqualTo(topicId);
            assertThat(p.noteId()).isNull();
            assertThat(p.targetId()).isEqualTo(7L);
        });
        assertThat(actual.get(1)).satisfies(p -> {
            assertThat(p.noteId()).isEqualTo(noteId);
            assertThat(p.url()).isEqualTo("https://example.test/a");
        });

        // and when
        repo.deletePointer(ORG, pairing, onTopic);

        // then
        assertThat(repo.pointersOf(ORG, pairing)).extracting(Pointer::id).containsExactly(onNote);
    }

    @Test
    void aPointerBelongsToExactlyOneOfTopicOrNote() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));

        // when / then
        assertThatThrownBy(() -> repo.insertPointer(ORG, Pointer.builder().pairingId(pairing).kind(PointerKind.LINK)
                .url("https://example.test").createdAt(EARLY).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPointerCannotHangOffBothATopicAndANote() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var topicId = repo.insertTopic(ORG, topic(pairing, "Topic", null, 0));
        var noteId = repo.insertNote(ORG, note(pairing, "note", EARLY, null));

        // when / then
        assertThatThrownBy(() -> repo.insertPointer(ORG,
                Pointer.builder().pairingId(pairing).topicId(topicId).noteId(noteId).kind(PointerKind.LINK)
                        .url("https://example.test").createdAt(EARLY).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingATopicOrNoteCascadesToItsPointers() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var topicId = repo.insertTopic(ORG, topic(pairing, "Topic", null, 0));
        var noteId = repo.insertNote(ORG, note(pairing, "note", EARLY, null));
        repo.insertPointer(ORG, Pointer.builder().pairingId(pairing).topicId(topicId).kind(PointerKind.ARTIFACT)
                .targetId(4L).url("/communities/platform/library?focus=4").createdAt(EARLY).build());
        repo.insertPointer(ORG, Pointer.builder().pairingId(pairing).noteId(noteId).kind(PointerKind.SESSION)
                .targetId(3L).url("/communities/platform/sessions/3").createdAt(EARLY).build());

        // when
        repo.deleteTopic(ORG, pairing, topicId);
        repo.deleteNote(ORG, pairing, noteId);

        // then
        assertThat(repo.pointersOf(ORG, pairing)).isEmpty();
    }

    @Test
    void deletingAPairingCascadesToItsJournalAndWords() {

        // given
        var pairing = seedPairing(seedCommunity("platform"));
        var topicId = repo.insertTopic(ORG, topic(pairing, "Topic", null, 0));
        repo.insertNote(ORG, note(pairing, "note", EARLY, null));
        repo.insertPointer(ORG, Pointer.builder().pairingId(pairing).topicId(topicId).kind(PointerKind.LINK)
                .url("https://example.test").createdAt(EARLY).build());
        words.upsert(ORG,
                dev.axonia.mentoring.domain.Words.builder().pairingId(pairing).body("thanks").writtenAt(EARLY).build());

        // when
        jdbc.update("DELETE FROM mentoring_pairing WHERE id = :id", java.util.Map.of("id", pairing));

        // then
        assertThat(repo.topicsOf(ORG, pairing)).isEmpty();
        assertThat(repo.notesOf(ORG, pairing)).isEmpty();
        assertThat(repo.pointersOf(ORG, pairing)).isEmpty();
        assertThat(words.find(ORG, pairing)).isEmpty();
    }

    @Test
    void renameAreaRewritesThisCommunityOnly() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        var inPlatform = seedPairing(platform);
        var inData = seedPairing(data);
        repo.insertTopic(ORG, topic(inPlatform, "One", "k8s", 0));
        repo.insertTopic(ORG, topic(inData, "Two", "k8s", 0));

        // when
        repo.renameArea(ORG, platform, "k8s", "kubernetes");

        // then
        assertThat(repo.topicsOf(ORG, inPlatform)).extracting(Topic::area).containsExactly("kubernetes");
        assertThat(repo.topicsOf(ORG, inData)).extracting(Topic::area).containsExactly("k8s");
    }

    @Test
    void unlinkAreaClearsTheAreaButKeepsTheTopic() {

        // given
        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        var inPlatform = seedPairing(platform);
        var inData = seedPairing(data);
        repo.insertTopic(ORG, topic(inPlatform, "One", "kafka", 0));
        repo.insertTopic(ORG, topic(inData, "Two", "kafka", 0));

        // when
        repo.unlinkArea(ORG, platform, "kafka");

        // then
        assertThat(repo.topicsOf(ORG, inPlatform)).singleElement().extracting(Topic::area).isNull();
        assertThat(repo.topicsOf(ORG, inData)).extracting(Topic::area).containsExactly("kafka");
    }

    @Test
    void hasNoteOnAnswersForThatPairingAndDayAlone() {

        // given
        var community = seedCommunity("platform");
        var pairing = seedPairing(community);
        var other = seedPairing(community, "mia", "lea");
        var day = LocalDate.parse("2026-10-03");
        repo.insertNote(ORG, note(pairing, "Met", LATE, day));

        // when / then
        assertThat(repo.hasNoteOn(ORG, pairing, day)).isTrue();
        assertThat(repo.hasNoteOn(ORG, pairing, day.plusDays(1))).isFalse();
        assertThat(repo.hasNoteOn(ORG, other, day)).isFalse();
        assertThat(repo.hasNoteOn("another-org", pairing, day)).isFalse();
    }

    private long seedPairing(long community) {

        return seedPairing(community, "mia", "max");
    }

    private long seedPairing(long community, String mentor, String mentee) {

        return pairings.insert(ORG, pairing(community, mentor, mentee).build());
    }

    private static Topic topic(long pairing, String label, String area, int position) {

        return Topic.builder().pairingId(pairing).label(label).area(area).state(TopicState.TODO).position(position)
                .createdAt(EARLY).build();
    }

    private static Note note(long pairing, String body, Instant writtenAt, LocalDate meetingOn) {

        return Note.builder().pairingId(pairing).authorUserId("mia").body(body)
                .meetingOn(meetingOn == null ? LocalDate.ofInstant(writtenAt, ZoneOffset.UTC) : meetingOn)
                .writtenAt(writtenAt).build();
    }
}

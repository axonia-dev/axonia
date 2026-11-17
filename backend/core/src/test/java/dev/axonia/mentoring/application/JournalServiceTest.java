package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.mentoring.api.NextMeetingDay;
import dev.axonia.mentoring.api.NextMeetingView;
import dev.axonia.mentoring.api.NoteRequest;
import dev.axonia.mentoring.api.NoteView;
import dev.axonia.mentoring.api.PointerRequest;
import dev.axonia.mentoring.api.PointerView;
import dev.axonia.mentoring.api.TopicPlacement;
import dev.axonia.mentoring.api.TopicRequest;
import dev.axonia.mentoring.api.TopicView;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.PointerTitles;
import dev.axonia.mentoring.application.port.out.Vocabulary;
import dev.axonia.mentoring.application.port.out.WordsRepository;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.Note;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.PointerKind;
import dev.axonia.mentoring.domain.PointerTarget;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;
import dev.axonia.mentoring.domain.Words;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.error.ValidationException;
import dev.axonia.shared.api.web.PublicUrls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalServiceTest {

    private static final String ORG = "org-1";
    private static final long PLATFORM = 7L;
    private static final long DATA = 8L;
    private static final long PAIRING = 90L;
    private static final String ME = "max";
    private static final String MIA = "mia";
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00.123456Z");
    private static final Instant TODAY = Instant.parse("2026-11-16T10:00:00.123Z");
    private static final LocalDate DAY = LocalDate.parse("2026-11-16");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String BASE = "https://acme.example.test";
    private static final Map<Long, String> SLUGS = Map.of(PLATFORM, "platform", DATA, "data");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PairingRepository pairings;
    @Mock
    private JournalRepository journal;
    @Mock
    private Vocabulary vocabulary;
    @Mock
    private WordsRepository words;
    @Mock
    private PointerTitles pointerTitles;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private PublicUrls publicUrls;
    @Mock
    private MemberClocks memberClocks;

    private JournalService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ME);
        when(publicUrls.publicBaseUrl()).thenReturn(BASE);
        when(communityAccess.communityById(PLATFORM))
                .thenReturn(new MentoringCommunity(PLATFORM, "platform", "Platform", false, true));
        when(communityAccess.communityById(DATA)).thenReturn(new MentoringCommunity(DATA, "data", "Data", true, false));
        when(personDirectory.resolve(anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            return ids.stream()
                    .collect(Collectors.toMap(id -> id, id -> new MentoringPerson(id, name(id), null, null)));
        });
        when(vocabulary.normalized(anyString())).thenAnswer(inv -> inv.<String>getArgument(0).strip().toLowerCase());
        when(vocabulary.areasOf(anyLong())).thenReturn(new CommunityAreas(Map.of("k8s", "kubernetes")));
        when(vocabulary.areasIn("platform")).thenReturn(Set.of("kubernetes", "kafka"));
        when(pointerTitles.resolve(anyList())).thenReturn(Map.of());
        when(communityAccess.slugsOf(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().filter(SLUGS::containsKey).collect(Collectors.toMap(id -> id, SLUGS::get));
        });
        when(memberClocks.zoneOf(anyString(), anyString())).thenReturn(UTC);
        service = serviceAt(NOW);
    }

    private JournalService serviceAt(Instant now) {

        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var reads = new JournalReads(communityAccess, journal, words, pointerTitles, personDirectory, memberClocks,
                clock);
        return new JournalService(new PairingParties(organizationContext, currentUser, pairings), communityAccess,
                journal, vocabulary, reads, memberClocks, publicUrls, clock);
    }

    private static String name(String id) {

        return id.substring(0, 1).toUpperCase() + id.substring(1);
    }

    private static Pairing.PairingBuilder pairing(long community, String mentor, String mentee) {

        return Pairing.builder().id(PAIRING).communityId(community).mentorUserId(mentor).menteeUserId(mentee)
                .startedAt(NOW.minus(Duration.ofDays(20)));
    }

    private void store(Pairing pairing) {

        when(pairings.find(ORG, pairing.id())).thenReturn(Optional.of(pairing));
        when(pairings.findForWrite(ORG, pairing.id())).thenReturn(Optional.of(pairing));
    }

    private static Topic topic(long id, String label, String area, int position) {

        return Topic.builder().id(id).pairingId(PAIRING).label(label).area(area).state(TopicState.TODO)
                .position(position).createdAt(TODAY).build();
    }

    private static Topic placed(Topic topic, TopicState state) {

        return topic.toBuilder().state(state).build();
    }

    private static Note note(long id, String author) {

        return Note.builder().id(id).pairingId(PAIRING).authorUserId(author).body("Covered unit tests").meetingOn(DAY)
                .writtenAt(TODAY).build();
    }

    private static Pointer.PointerBuilder pointer(long id, PointerKind kind) {

        return Pointer.builder().id(id).pairingId(PAIRING).topicId(1L).kind(kind).createdAt(TODAY);
    }

    private static void assertCode(Executable call, Class<? extends RuntimeException> type, String code) {

        assertThatThrownBy(call::execute).isInstanceOf(type).hasFieldOrPropertyWithValue("code", code);
    }

    private List<Executable> everyCall() {

        var topic = new TopicRequest("Kafka", null);
        var note = new NoteRequest("Met", null, null);
        return List.of(() -> service.journal(PAIRING), () -> service.addTopic(PAIRING, topic),
                () -> service.updateTopic(PAIRING, 1L, topic), () -> service.removeTopic(PAIRING, 1L),
                () -> service.placeTopic(PAIRING, 1L, new TopicPlacement("DONE", 0)),
                () -> service.addNote(PAIRING, note), () -> service.updateNote(PAIRING, 2L, note),
                () -> service.removeNote(PAIRING, 2L),
                () -> service.addPointer(PAIRING, new PointerRequest(1L, null, "https://example.com")),
                () -> service.removePointer(PAIRING, 3L));
    }

    @Test
    void aPairingReadsAsMissingToAnyoneOutsideIt() {

        // given
        store(pairing(PLATFORM, MIA, "noah").build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(note(2L, MIA)));

        // when / then
        for (var call : everyCall()) {
            assertThatThrownBy(call::execute).isInstanceOf(NotFoundException.class);
        }
        assertThatThrownBy(() -> service.journal(404L)).isInstanceOf(NotFoundException.class);
        verify(journal, never()).insertTopic(any(), any());
        verify(journal, never()).insertNote(any(), any());
        verify(journal, never()).insertPointer(any(), any());
    }

    @Test
    void aDelegatedSessionNeitherReadsNorWrites() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        for (var call : everyCall()) {
            assertThatThrownBy(call::execute).isInstanceOf(OwnSignInRequiredException.class);
        }
        verifyNoInteractions(pairings, journal);
    }

    @Test
    void eitherPersonWritesTopicsAndNotes() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING))
                .thenReturn(List.of(topic(1L, "Kafka", null, 0), topic(2L, "Helm", null, 4)));

        // when
        service.addTopic(PAIRING, new TopicRequest("  Giving feedback ", null));
        service.addNote(PAIRING, new NoteRequest(" Met, covered Lombok ", LocalDate.parse("2026-11-12"), null));

        // then
        verify(journal).insertTopic(ORG, Topic.builder().pairingId(PAIRING).label("Giving feedback")
                .state(TopicState.TODO).position(5).createdAt(TODAY).build());
        verify(journal).insertNote(ORG, Note.builder().pairingId(PAIRING).authorUserId(ME).body("Met, covered Lombok")
                .meetingOn(LocalDate.parse("2026-11-12")).writtenAt(TODAY).build());
    }

    @Test
    void theFirstTopicTakesTheFirstPosition() {

        // given
        store(pairing(PLATFORM, ME, MIA).build());

        // when
        service.addTopic(PAIRING, new TopicRequest("Unit tests", ""));

        // then
        verify(journal).insertTopic(ORG, Topic.builder().pairingId(PAIRING).label("Unit tests").state(TopicState.TODO)
                .position(0).createdAt(TODAY).build());
        verifyNoInteractions(vocabulary);
    }

    @Test
    void aNewTopicIsToDoAndLast() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0), topic(2L, "Helm", null, 1),
                placed(topic(3L, "Docker", null, 6), TopicState.DONE)));

        // when
        service.addTopic(PAIRING, new TopicRequest("Giving feedback", null));

        // then
        verify(journal).insertTopic(ORG, Topic.builder().pairingId(PAIRING).label("Giving feedback")
                .state(TopicState.TODO).position(2).createdAt(TODAY).build());
    }

    @Test
    void eitherPersonRenamesRelinksAndRemovesATopic() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", "kafka", 0)));

        // when
        service.updateTopic(PAIRING, 1L, new TopicRequest("Kubernetes", "K8s"));
        service.removeTopic(PAIRING, 1L);

        // then
        verify(journal).updateTopic(ORG, topic(1L, "Kubernetes", "kubernetes", 0));
        verify(journal).deleteTopic(ORG, PAIRING, 1L);
        verify(journal).placeTopics(ORG, PAIRING, List.of());
        verify(vocabulary).shareLock(ORG, PLATFORM);
    }

    @Test
    void placingATopicMovesItAndRenumbers() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0), topic(2L, "Helm", null, 1),
                topic(3L, "Docker", null, 2), placed(topic(4L, "Unit tests", null, 0), TopicState.DONE)));

        // when
        service.placeTopic(PAIRING, 1L, new TopicPlacement("DONE", 0));

        // then
        verify(journal).placeTopics(ORG, PAIRING,
                List.of(placed(topic(1L, "Kafka", null, 0), TopicState.DONE),
                        placed(topic(4L, "Unit tests", null, 1), TopicState.DONE), topic(2L, "Helm", null, 0),
                        topic(3L, "Docker", null, 1)));
    }

    @Test
    void aPlacementWithoutAPositionGoesLast() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING))
                .thenReturn(List.of(topic(1L, "Kafka", null, 0), topic(2L, "Helm", null, 1)));

        // when
        service.placeTopic(PAIRING, 1L, new TopicPlacement("TODO", null));

        // then
        verify(journal).placeTopics(ORG, PAIRING, List.of(topic(2L, "Helm", null, 0), topic(1L, "Kafka", null, 1)));
        verify(pairings).findForWrite(ORG, PAIRING);
    }

    @Test
    void placingOnAnEndedPairingIsRefused() {

        // given
        store(pairing(PLATFORM, MIA, ME).endedAt(NOW.minusSeconds(5)).endReason(EndReason.MENTEE).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));

        // when / then
        assertCode(() -> service.placeTopic(PAIRING, 1L, new TopicPlacement("DONE", 0)), ConflictException.class,
                "MENTORING_PAIRING_ENDED");
        verify(journal, never()).placeTopics(any(), anyLong(), anyList());
    }

    @Test
    void anUnknownStateIsRefused() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));

        // when / then
        assertCode(() -> service.placeTopic(PAIRING, 1L, new TopicPlacement("LONG_TERM", 0)), ValidationException.class,
                "field_invalid");
        assertCode(() -> service.placeTopic(PAIRING, 1L, new TopicPlacement(null, 0)), ValidationException.class,
                "field_invalid");
        assertCode(() -> service.placeTopic(PAIRING, 1L, new TopicPlacement("DONE", -1)), ValidationException.class,
                "field_invalid");
        verify(journal, never()).placeTopics(any(), anyLong(), anyList());
    }

    @Test
    void removingATopicClosesItsGap() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0), topic(2L, "Helm", null, 1),
                placed(topic(3L, "Docker", null, 0), TopicState.DONE)));

        // when
        service.removeTopic(PAIRING, 1L);

        // then
        var order = org.mockito.Mockito.inOrder(journal);
        order.verify(journal).deleteTopic(ORG, PAIRING, 1L);
        order.verify(journal).placeTopics(ORG, PAIRING, List.of(topic(2L, "Helm", null, 0)));
    }

    @Test
    void changingAStateKeepsTheLinkAndLocksTheVocabularyBeforeReadingTheTopic() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", "kafka", 0)));

        // when
        service.updateTopic(PAIRING, 1L, new TopicRequest("Kafka", "kafka"));

        // then
        var order = org.mockito.Mockito.inOrder(vocabulary, journal);
        order.verify(vocabulary).shareLock(ORG, PLATFORM);
        order.verify(journal).topicsOf(ORG, PAIRING);
        verify(journal).updateTopic(ORG, topic(1L, "Kafka", "kafka", 0));
        verifyNoMoreInteractions(vocabulary);
    }

    @Test
    void aMissingTopicIsNotFound() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));

        // when / then
        assertThatThrownBy(() -> service.updateTopic(PAIRING, 9L, new TopicRequest("Kafka", null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.placeTopic(PAIRING, 9L, new TopicPlacement("DONE", 0)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.removeTopic(PAIRING, 9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aTopicLinksToAKnownAreaOrStaysFree() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());

        // when
        service.addTopic(PAIRING, new TopicRequest("Giving feedback", null));

        // then
        assertCode(() -> service.addTopic(PAIRING, new TopicRequest("Cooking", "cooking")), ValidationException.class,
                "field_invalid");
        var saved = ArgumentCaptor.forClass(Topic.class);
        verify(journal).insertTopic(any(), saved.capture());
        assertThat(saved.getValue().area()).isNull();
        assertThat(saved.getValue().label()).isEqualTo("Giving feedback");
    }

    @Test
    void aTopicNeedsAShortName() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());

        // when / then
        assertCode(() -> service.addTopic(PAIRING, new TopicRequest("a".repeat(81), null)), ValidationException.class,
                "MENTORING_TOO_LONG");
        assertCode(() -> service.addTopic(PAIRING, new TopicRequest(" ", null)), ValidationException.class,
                "field_invalid");
        service.addTopic(PAIRING, new TopicRequest("a".repeat(80), null));
        verify(journal).insertTopic(any(), any());
    }

    @Test
    void onlyTheAuthorChangesOrRemovesANote() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(note(2L, ME), note(3L, MIA)));

        // when
        service.updateNote(PAIRING, 2L, new NoteRequest("Covered decoupling", LocalDate.parse("2026-11-10"), null));
        service.removeNote(PAIRING, 2L);

        // then
        verify(journal).updateNote(ORG, note(2L, ME).toBuilder().body("Covered decoupling")
                .meetingOn(LocalDate.parse("2026-11-10")).editedAt(TODAY).build());
        verify(journal).deleteNote(ORG, PAIRING, 2L);
        assertThatThrownBy(() -> service.updateNote(PAIRING, 3L, new NoteRequest("Mine now", null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.removeNote(PAIRING, 3L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.removeNote(PAIRING, 9L)).isInstanceOf(NotFoundException.class);
        verify(journal, never()).deleteNote(ORG, PAIRING, 3L);
    }

    @Test
    void aNoteDefaultsToTodayInTheAuthorsZone() {

        // given
        var lateInUtc = Instant.parse("2026-11-15T12:30:00Z");
        store(pairing(PLATFORM, MIA, ME).build());
        when(memberClocks.zoneOf(ORG, ME)).thenReturn(ZoneId.of("Pacific/Auckland"));

        // when
        serviceAt(lateInUtc).addNote(PAIRING, new NoteRequest("Met", null, null));

        // then
        verify(journal).insertNote(ORG, Note.builder().pairingId(PAIRING).authorUserId(ME).body("Met")
                .meetingOn(LocalDate.parse("2026-11-16")).writtenAt(lateInUtc).build());
    }

    @Test
    void aNoteBeforeThePairingStartedIsRefused() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        var startDay = LocalDate.parse("2026-10-27");

        // when
        service.addNote(PAIRING, new NoteRequest("First day", startDay, null));

        // then
        assertCode(() -> service.addNote(PAIRING, new NoteRequest("Too early", startDay.minusDays(1), null)),
                ValidationException.class, "MENTORING_MEETING_DAY");
        assertCode(() -> service.addNote(PAIRING, new NoteRequest("Tomorrow", DAY.plusDays(1), null)),
                ValidationException.class, "MENTORING_MEETING_DAY");
        verify(journal).insertNote(eq(ORG), any());
    }

    @Test
    void editingANoteKeepsItsOwnDayAfterTheAuthorsZoneMoved() {

        // given: the note's day was the pairing's first day in an earlier zone, before it in today's
        var earlierFirstDay = LocalDate.parse("2026-10-26");
        store(pairing(PLATFORM, MIA, ME).build());
        var mine = note(2L, ME).toBuilder().meetingOn(earlierFirstDay).build();
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(mine));

        // when
        service.updateNote(PAIRING, 2L, new NoteRequest("Body only", earlierFirstDay, null));

        // then
        verify(journal).updateNote(ORG, mine.toBuilder().body("Body only").editedAt(TODAY).build());
        assertCode(() -> service.updateNote(PAIRING, 2L, new NoteRequest("Moved", earlierFirstDay.minusDays(1), null)),
                ValidationException.class, "MENTORING_MEETING_DAY");
    }

    @Test
    void aNoteNamesTopicsOfItsPairingOnly() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING))
                .thenReturn(List.of(topic(1L, "Kafka", null, 0), topic(2L, "Helm", null, 1)));
        when(journal.insertNote(eq(ORG), any())).thenReturn(30L);

        // when
        service.addNote(PAIRING, new NoteRequest("Met", null, List.of(2L, 1L, 2L)));

        // then
        verify(journal).setNoteTopics(ORG, PAIRING, 30L, List.of(2L, 1L));
        assertCode(() -> service.addNote(PAIRING, new NoteRequest("Met", null, List.of(1L, 77L))),
                ValidationException.class, "field_invalid");
        verify(journal).insertNote(eq(ORG), any());
    }

    @Test
    void editingANoteReplacesItsTopics() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING))
                .thenReturn(List.of(topic(1L, "Kafka", null, 0), topic(2L, "Helm", null, 1)));
        var mine = note(2L, ME).toBuilder().meetingOn(LocalDate.parse("2026-11-02")).build();
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(mine));

        // when
        service.updateNote(PAIRING, 2L, new NoteRequest("Covered Helm", null, List.of(2L)));
        service.updateNote(PAIRING, 2L, new NoteRequest("Covered nothing", null, List.of()));

        // then
        verify(journal).updateNote(ORG, mine.toBuilder().body("Covered Helm").editedAt(TODAY).build());
        verify(journal).setNoteTopics(ORG, PAIRING, 2L, List.of(2L));
        verify(journal).setNoteTopics(ORG, PAIRING, 2L, List.of());
    }

    @Test
    void theJournalCarriesTodayAndTheStartDayInTheCallersZone() {

        // given
        store(pairing(PLATFORM, MIA, ME).startedAt(Instant.parse("2026-10-31T23:30:00Z")).build());
        when(memberClocks.zoneOf(ORG, ME)).thenReturn(ZoneId.of("Pacific/Auckland"));

        // when
        var actual = serviceAt(Instant.parse("2026-11-15T12:30:00Z")).journal(PAIRING);

        // then
        assertThat(actual.today()).isEqualTo(LocalDate.parse("2026-11-16"));
        assertThat(actual.startedOn()).isEqualTo(LocalDate.parse("2026-11-01"));
    }

    @Test
    void theJournalCarriesTheNextMeetingAndWhetherItPassed() {

        // given
        store(pairing(PLATFORM, MIA, ME).nextMeetingOn(DAY.plusDays(3)).nextMeetingSetBy(MIA).nextMeetingSetAt(TODAY)
                .build());

        // when
        var upcoming = service.journal(PAIRING).nextMeeting();
        store(pairing(PLATFORM, MIA, ME).nextMeetingOn(DAY.minusDays(1)).nextMeetingSetBy(ME).nextMeetingSetAt(TODAY)
                .build());
        var passed = service.journal(PAIRING).nextMeeting();
        store(pairing(PLATFORM, MIA, ME).build());
        var none = service.journal(PAIRING).nextMeeting();

        // then
        assertThat(upcoming).isEqualTo(new NextMeetingView(DAY.plusDays(3), MIA, "Mia", false));
        assertThat(passed).isEqualTo(new NextMeetingView(DAY.minusDays(1), ME, "Max", true));
        assertThat(none).isNull();
    }

    @Test
    void theJournalsPairingGlancesAtTheTopicsAndNotesItLoads() {

        // given
        store(pairing(PLATFORM, MIA, ME).nextMeetingOn(DAY.plusDays(3)).nextMeetingSetBy(MIA).nextMeetingSetAt(TODAY)
                .build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0),
                placed(topic(4L, "Giving feedback", null, 0), TopicState.IN_PROGRESS),
                placed(topic(7L, "Docker", null, 0), TopicState.DONE)));
        when(journal.notesOf(ORG, PAIRING)).thenReturn(
                List.of(note(3L, ME), note(2L, MIA), note(1L, MIA).toBuilder().meetingOn(DAY.minusDays(7)).build()));

        // when
        var actual = service.journal(PAIRING).pairing();

        // then
        assertThat(actual.stops()).containsExactly("DONE", "IN_PROGRESS", "TODO");
        assertThat(actual.nowTopic()).isEqualTo("Giving feedback");
        assertThat(actual.nextMeeting()).isEqualTo(new NextMeetingDay(DAY.plusDays(3), false));
        assertThat(actual.meetings()).isEqualTo(2);
        assertThat(actual.notes()).isEqualTo(3);
        assertThat(actual.lastMetOn()).isEqualTo(DAY);
        verify(journal, never()).trailsOf(any(), any());
        verify(journal, never()).tallyOf(any(), any());
    }

    @Test
    void aNoteNeedsABodyOfAtMost2000Characters() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());

        // when / then
        assertCode(() -> service.addNote(PAIRING, new NoteRequest("a".repeat(2001), null, null)),
                ValidationException.class, "MENTORING_TOO_LONG");
        assertCode(() -> service.addNote(PAIRING, new NoteRequest(null, null, null)), ValidationException.class,
                "field_invalid");
        service.addNote(PAIRING, new NoteRequest("a".repeat(2000), null, null));
        verify(journal).insertNote(any(), any());
    }

    @Test
    void anEndedPairingIsReadOnly() {

        // given
        store(pairing(PLATFORM, MIA, ME).endedAt(NOW.minusSeconds(5)).endReason(EndReason.MENTOR).build());

        // when
        var actual = service.journal(PAIRING);

        // then
        assertThat(actual.writable()).isFalse();
        for (var write : everyCall().subList(1, everyCall().size())) {
            assertCode(write, ConflictException.class, "MENTORING_PAIRING_ENDED");
        }
    }

    @Test
    void aSwitchedOffOrArchivedGuildKeepsTheJournalWritable() {

        // given
        store(pairing(DATA, MIA, ME).build());

        // when
        var actual = service.journal(PAIRING);
        service.addNote(PAIRING, new NoteRequest("Still meeting", null, null));

        // then
        assertThat(actual.writable()).isTrue();
        assertThat(actual.pairing().communityName()).isEqualTo("Data");
        verify(journal).insertNote(any(), any());
    }

    @Test
    void anOutsideAddressIsAPlainLink() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(note(2L, MIA)));

        // when
        service.addPointer(PAIRING, new PointerRequest(null, 2L, "https://martinfowler.com/articles/mocks"));

        // then
        verify(journal).insertPointer(ORG, Pointer.builder().pairingId(PAIRING).noteId(2L).kind(PointerKind.LINK)
                .url("https://martinfowler.com/articles/mocks").createdAt(TODAY).build());
    }

    @Test
    void anAxoniaAddressPointsAtWhatItShows() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));

        // when
        service.addPointer(PAIRING, new PointerRequest(1L, null, BASE + "/communities/data/sessions/42"));

        // then
        verify(journal).insertPointer(ORG, Pointer.builder().pairingId(PAIRING).topicId(1L).kind(PointerKind.SESSION)
                .targetId(42L).url("/communities/data/sessions/42").createdAt(TODAY).build());
    }

    @Test
    void aPointerHangsOffExactlyOneTopicOrNoteOfThePairing() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(note(2L, MIA)));
        var url = "https://example.com";

        // when / then
        assertCode(() -> service.addPointer(PAIRING, new PointerRequest(null, null, url)), ValidationException.class,
                "field_invalid");
        assertCode(() -> service.addPointer(PAIRING, new PointerRequest(1L, 2L, url)), ValidationException.class,
                "field_invalid");
        assertThatThrownBy(() -> service.addPointer(PAIRING, new PointerRequest(9L, null, url)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.addPointer(PAIRING, new PointerRequest(null, 9L, url)))
                .isInstanceOf(NotFoundException.class);
        assertCode(() -> service.addPointer(PAIRING, new PointerRequest(1L, null, "javascript:alert(1)")),
                ValidationException.class, "field_invalid");
        verify(journal, never()).insertPointer(any(), any());
    }

    @Test
    void eitherPersonRemovesAPointerOfThePairing() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.pointersOf(ORG, PAIRING)).thenReturn(List.of(pointer(3L, PointerKind.LINK).build()));

        // when
        service.removePointer(PAIRING, 3L);

        // then
        verify(journal).deletePointer(ORG, PAIRING, 3L);
        assertThatThrownBy(() -> service.removePointer(PAIRING, 9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theJournalReadsTopicsInOrderAndNotesNewestFirstEachWithItsPointers() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(placed(topic(7L, "Docker", null, 0), TopicState.DONE),
                topic(4L, "Giving feedback", null, 1), topic(1L, "Kafka", "kafka", 0)));
        var newer = note(3L, ME);
        var older = note(2L, MIA).toBuilder().writtenAt(TODAY.minusSeconds(3600)).editedAt(TODAY).build();
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(newer, older));
        when(journal.noteTopicsOf(ORG, PAIRING)).thenReturn(Map.of(3L, List.of(1L, 4L)));
        var link = pointer(5L, PointerKind.LINK).url("https://example.com").build();
        var onNote = pointer(6L, PointerKind.ARTIFACT).topicId(null).noteId(2L).targetId(70L)
                .url("/communities/data/library?focus=70").build();
        when(journal.pointersOf(ORG, PAIRING)).thenReturn(List.of(link, onNote));
        when(pointerTitles.resolve(List.of(link, onNote)))
                .thenReturn(Map.of(6L, new PointerTarget("Schema registry notes", DATA)));
        when(words.find(ORG, PAIRING)).thenReturn(Optional.of(new Words(PAIRING, "Thank you", TODAY, null, null)));

        // when
        var actual = service.journal(PAIRING);

        // then
        assertThat(actual.pairing().partnerName()).isEqualTo("Mia");
        assertThat(actual.pairing().myRole()).isEqualTo("MENTEE");
        assertThat(actual.pairing().stops()).containsExactly("DONE", "TODO", "TODO");
        assertThat(actual.writable()).isTrue();
        assertThat(actual.topics()).containsExactly(
                new TopicView(1L, "Kafka", "kafka", "TODO", 0,
                        List.of(new PointerView(5L, "LINK", null, "https://example.com", true))),
                new TopicView(4L, "Giving feedback", null, "TODO", 1, List.of()),
                new TopicView(7L, "Docker", null, "DONE", 0, List.of()));
        assertThat(actual.notes()).containsExactly(
                NoteView.builder().id(3L).authorId(ME).authorName("Max").mine(true).body("Covered unit tests")
                        .meetingOn(DAY).topicIds(List.of(1L, 4L)).writtenAt(TODAY).pointers(List.of()).build(),
                NoteView.builder().id(2L).authorId(MIA).authorName("Mia").mine(false).body("Covered unit tests")
                        .meetingOn(DAY).topicIds(List.of()).writtenAt(TODAY.minusSeconds(3600)).editedAt(TODAY)
                        .pointers(List.of(new PointerView(6L, "ARTIFACT", "Schema registry notes",
                                "/communities/data/library?focus=70", true)))
                        .build());
        assertThat(actual.wordsWritten()).isTrue();
        assertThat(actual.canWriteWords()).isTrue();
        assertThat(actual.myWords()).isEqualTo("Thank you");
    }

    @Test
    void theMentorHasNoWordsToWriteAndReadsNoneHere() {

        // given
        store(pairing(PLATFORM, ME, MIA).build());
        when(words.find(ORG, PAIRING)).thenReturn(Optional.of(new Words(PAIRING, "Thank you", TODAY, null, null)));

        // when
        var actual = service.journal(PAIRING);

        // then
        assertThat(actual.pairing().myRole()).isEqualTo("MENTOR");
        assertThat(actual.wordsWritten()).isTrue();
        assertThat(actual.canWriteWords()).isFalse();
        assertThat(actual.myWords()).isNull();
    }

    @Test
    void newWordsWaitForTheEndAndThenOpenToTheMentee() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());

        // when
        var ongoing = service.journal(PAIRING);
        store(pairing(PLATFORM, MIA, ME).endedAt(NOW.minus(Duration.ofDays(365))).endReason(EndReason.MENTOR).build());
        var ended = service.journal(PAIRING);

        // then
        assertThat(ongoing.canWriteWords()).isFalse();
        assertThat(ended.canWriteWords()).isTrue();
    }

    @Test
    void aMenteeWritesWordsUntilAYearAfterTheEndAndHasNoneBeforeWritingThem() {

        // given
        store(pairing(PLATFORM, MIA, ME).endedAt(NOW.minus(Duration.ofDays(366))).endReason(EndReason.MENTEE).build());

        // when
        var actual = service.journal(PAIRING);

        // then
        assertThat(actual.canWriteWords()).isFalse();
        assertThat(actual.wordsWritten()).isFalse();
        assertThat(actual.myWords()).isNull();
    }

    @Test
    void aPointerToADeletedArtifactReadsUnavailable() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));
        when(journal.pointersOf(ORG, PAIRING)).thenReturn(List
                .of(pointer(5L, PointerKind.ARTIFACT).targetId(70L).url("/communities/data/library?focus=70").build()));

        // when
        var actual = service.journal(PAIRING);

        // then
        assertThat(actual.topics()).singleElement().extracting(TopicView::pointers)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(PointerView.class))
                .containsExactly(new PointerView(5L, "ARTIFACT", null, null, false));
    }

    @Test
    void aPointerToAnotherGuildsTalkResolvesItsTitle() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Giving feedback", null, 0)));
        var talk = pointer(5L, PointerKind.SESSION).targetId(42L).url("/communities/data/sessions/42").build();
        when(journal.pointersOf(ORG, PAIRING)).thenReturn(List.of(talk));
        when(pointerTitles.resolve(List.of(talk)))
                .thenReturn(Map.of(5L, new PointerTarget("Radical candor in reviews", DATA)));

        // when
        var actual = service.journal(PAIRING);

        // then
        Consumer<TopicView> resolved = t -> assertThat(t.pointers()).containsExactly(
                new PointerView(5L, "SESSION", "Radical candor in reviews", "/communities/data/sessions/42", true));
        assertThat(actual.topics()).singleElement().satisfies(resolved);
    }

    @Test
    void anErasedPartnerReadsAsDeletedMemberWithNoId() {

        // given
        store(pairing(PLATFORM, null, ME).build());

        // when
        var actual = service.journal(PAIRING);

        // then
        assertThat(actual.pairing().partnerId()).isNull();
        assertThat(actual.pairing().partnerName()).isEqualTo("Deleted member");
    }

    @Test
    void aMenteeCannotWriteWordsToAnErasedMentor() {

        // given
        store(pairing(PLATFORM, null, ME).endedAt(NOW.minus(Duration.ofDays(2))).endReason(EndReason.LEFT).build());

        // when
        var actual = service.journal(PAIRING);

        // then
        assertThat(actual.canWriteWords()).isFalse();
    }

    @Test
    void aPointerNamingATopicOfAnotherPairingIsNotFoundAndNothingIsWritten() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));

        // when / then
        assertThatThrownBy(() -> service.addPointer(PAIRING, new PointerRequest(77L, null, "https://example.com")))
                .isInstanceOf(NotFoundException.class);
        verify(journal, never()).insertPointer(any(), any());
    }

    @Test
    void aResolvedPointerLinksIntoTheGuildItsTargetLivesInNotThePastedOne() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(List.of(topic(1L, "Kafka", null, 0)));
        var pasted = pointer(5L, PointerKind.SESSION).targetId(42L).url("/communities/elsewhere/sessions/42").build();
        var orphan = pointer(6L, PointerKind.ARTIFACT).targetId(70L).url("/communities/data/library?focus=70").build();
        when(journal.pointersOf(ORG, PAIRING)).thenReturn(List.of(pasted, orphan));
        when(pointerTitles.resolve(List.of(pasted, orphan))).thenReturn(Map.of(5L,
                new PointerTarget("Kafka night", PLATFORM), 6L, new PointerTarget("Schema registry notes", 404L)));

        // when
        var actual = service.journal(PAIRING);

        // then
        Consumer<TopicView> resolved = t -> assertThat(t.pointers()).containsExactly(
                new PointerView(5L, "SESSION", "Kafka night", "/communities/platform/sessions/42", true),
                new PointerView(6L, "ARTIFACT", null, null, false));
        assertThat(actual.topics()).singleElement().satisfies(resolved);
    }

    @Test
    void aWriteReadsThePairingUnderItsRowLock() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());

        // when
        service.addNote(PAIRING, new NoteRequest("Met", null, null));

        // then
        verify(pairings).findForWrite(ORG, PAIRING);
        verify(pairings, never()).find(ORG, PAIRING);
    }

    @Test
    void aJournalTakesThirtyTopicsAndNoMore() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        var full = java.util.stream.IntStream.range(0, 30).mapToObj(i -> topic(i, "Topic " + i, null, i)).toList();
        when(journal.topicsOf(ORG, PAIRING)).thenReturn(full.subList(0, 29), full);

        // when
        service.addTopic(PAIRING, new TopicRequest("The thirtieth", null));

        // then
        assertCode(() -> service.addTopic(PAIRING, new TopicRequest("One too many", null)), ConflictException.class,
                "MENTORING_JOURNAL_FULL");
        verify(journal).insertTopic(any(), any());
    }

    @Test
    void aJournalTakesTwoHundredNotesAndNoMore() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        var full = java.util.stream.LongStream.range(0, 200).mapToObj(i -> note(i, ME)).toList();
        when(journal.notesOf(ORG, PAIRING)).thenReturn(full.subList(0, 199), full);

        // when
        service.addNote(PAIRING, new NoteRequest("The two hundredth", null, null));

        // then
        assertCode(() -> service.addNote(PAIRING, new NoteRequest("One too many", null, null)), ConflictException.class,
                "MENTORING_JOURNAL_FULL");
        verify(journal).insertNote(any(), any());
    }

    @Test
    void aTopicOrANoteHoldsTenLinksAndNoMoreCountingItsOwnOnly() {

        // given
        store(pairing(PLATFORM, MIA, ME).build());
        when(journal.topicsOf(ORG, PAIRING))
                .thenReturn(List.of(topic(1L, "Kafka", null, 0), topic(2L, "Helm", null, 1)));
        when(journal.notesOf(ORG, PAIRING)).thenReturn(List.of(note(3L, MIA)));
        var onTopic = java.util.stream.LongStream.range(0, 10)
                .mapToObj(i -> pointer(10 + i, PointerKind.LINK).url("https://example.test/" + i).build());
        var onNote = java.util.stream.LongStream.range(0, 9).mapToObj(
                i -> pointer(30 + i, PointerKind.LINK).topicId(null).noteId(3L).url("https://example.test").build());
        when(journal.pointersOf(ORG, PAIRING)).thenReturn(java.util.stream.Stream.concat(onTopic, onNote).toList());
        var url = "https://example.test/more";

        // when
        service.addPointer(PAIRING, new PointerRequest(2L, null, url));
        service.addPointer(PAIRING, new PointerRequest(null, 3L, url));

        // then
        assertCode(() -> service.addPointer(PAIRING, new PointerRequest(1L, null, url)), ConflictException.class,
                "MENTORING_JOURNAL_FULL");
        verify(journal, org.mockito.Mockito.times(2)).insertPointer(any(), any());
    }
}

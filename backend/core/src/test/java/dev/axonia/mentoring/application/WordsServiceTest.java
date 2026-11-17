package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.api.WordsView;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.WordsRepository;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Words;
import dev.axonia.mentoring.domain.WordsForMentor;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WordsServiceTest {

    private static final String ORG = "org-1";
    private static final long COMMUNITY = 7L;
    private static final long PAIRING = 90L;
    private static final String MIA = "mia";
    private static final String MAX = "max";
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00.123456Z");
    private static final Instant TODAY = Instant.parse("2026-11-16T10:00:00.123Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PairingRepository pairings;
    @Mock
    private WordsRepository words;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private MentoringNotices notices;

    private WordsService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(communityAccess.communityById(COMMUNITY))
                .thenReturn(new MentoringCommunity(COMMUNITY, "platform", "Platform", false, true));
        when(personDirectory.resolve(anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            return ids.stream().collect(Collectors.toMap(id -> id, id -> new MentoringPerson(id,
                    id.substring(0, 1).toUpperCase() + id.substring(1), null, "/a/" + id)));
        });
        service = new WordsService(organizationContext, currentUser, communityAccess, pairings, words, personDirectory,
                notices, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void signedInAs(String userId) {

        when(currentUser.userId()).thenReturn(userId);
    }

    private void pairing(Pairing.PairingBuilder builder) {

        when(pairings.find(ORG, PAIRING)).thenReturn(java.util.Optional.of(builder.build()));
    }

    private static Pairing.PairingBuilder ongoing() {

        return Pairing.builder().id(PAIRING).communityId(COMMUNITY).mentorUserId(MIA).menteeUserId(MAX)
                .startedAt(NOW.minus(Duration.ofDays(60)));
    }

    private static Pairing.PairingBuilder endedDaysAgo(long days) {

        return ongoing().endedAt(NOW.minus(Duration.ofDays(days))).endReason(EndReason.MENTOR);
    }

    @Test
    void theMenteeWritesChangesAndTakesBackWords() {

        // given
        signedInAs(MAX);
        pairing(endedDaysAgo(1));
        when(words.upsert(eq(ORG), any())).thenReturn(true, false);

        // when
        service.write(PAIRING, "  Thank you for the patience  ");
        service.write(PAIRING, "Thank you for the patience and the books");
        service.takeBack(PAIRING);

        // then
        var captor = ArgumentCaptor.forClass(Words.class);
        verify(words, org.mockito.Mockito.times(2)).upsert(eq(ORG), captor.capture());
        assertThat(captor.getAllValues()).extracting(Words::body).containsExactly("Thank you for the patience",
                "Thank you for the patience and the books");
        assertThat(captor.getValue().writtenAt()).isEqualTo(TODAY);
        verify(words).delete(ORG, PAIRING);
    }

    @Test
    void theMentorOrAStrangerCannotWriteOrTakeBackWords() {

        // given
        pairing(ongoing());

        for (var who : List.of(MIA, "zed")) {
            signedInAs(who);

            // when
            var write = assertThatThrownBy(() -> service.write(PAIRING, "hello"));
            var takeBack = assertThatThrownBy(() -> service.takeBack(PAIRING));

            // then
            write.isInstanceOf(NotFoundException.class);
            takeBack.isInstanceOf(NotFoundException.class);
        }
        verify(words, never()).upsert(any(), any());
        verify(words, never()).delete(any(), anyLong());
    }

    @Test
    void anUnknownPairingIsNotFound() {

        // given
        signedInAs(MAX);
        when(pairings.find(ORG, 5L)).thenReturn(java.util.Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.write(5L, "hello")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void afterTheEndTheMenteeWritesForAYear() {

        // given
        signedInAs(MAX);
        when(words.upsert(eq(ORG), any())).thenReturn(true);

        // when
        pairing(endedDaysAgo(365));
        service.write(PAIRING, "late but fine");

        // then
        verify(words).upsert(eq(ORG), any());

        // when
        pairing(endedDaysAgo(366));

        // then
        assertThatThrownBy(() -> service.write(PAIRING, "too late")).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("MENTORING_WORDS_CLOSED");
        verify(words).upsert(eq(ORG), any());
    }

    @Test
    void takingBackIsAlwaysAllowed() {

        // given
        signedInAs(MAX);

        // when
        pairing(endedDaysAgo(500));
        service.takeBack(PAIRING);
        pairing(ongoing());
        service.takeBack(PAIRING);

        // then
        verify(words, org.mockito.Mockito.times(2)).delete(ORG, PAIRING);
    }

    @Test
    void aMenteeWhoLeftTheGuildStillWrites() {

        // given
        signedInAs(MAX);
        pairing(endedDaysAgo(10).endReason(EndReason.LEFT));
        when(words.upsert(eq(ORG), any())).thenReturn(true);

        // when
        service.write(PAIRING, "I left, but thank you");

        // then
        verify(words).upsert(eq(ORG), any());
        verify(communityAccess, never()).isMember(anyLong(), anyString());
    }

    @Test
    void theFirstWordsStillTellTheMentorOnce() {

        // given
        signedInAs(MAX);
        pairing(endedDaysAgo(1));
        when(words.upsert(eq(ORG), any())).thenReturn(true, false);

        // when
        service.write(PAIRING, "first");
        service.write(PAIRING, "changed");

        // then
        verify(notices, org.mockito.Mockito.times(1)).wordsReceived(any(),
                eq(new MentoringGuild("platform", "Platform")), eq("Max"));
    }

    @Test
    void newWordsWaitForTheEnd() {

        // given
        signedInAs(MAX);
        pairing(ongoing());
        when(words.find(ORG, PAIRING)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.write(PAIRING, "too early")).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("MENTORING_WORDS_NOT_YET");
        verify(words, never()).upsert(any(), any());
        verifyNoInteractions(notices);
    }

    @Test
    void wordsWrittenBeforeTheEndStayEditableWhileOngoing() {

        // given
        signedInAs(MAX);
        pairing(ongoing());
        when(words.find(ORG, PAIRING)).thenReturn(Optional
                .of(Words.builder().pairingId(PAIRING).body("written early").writtenAt(NOW.minusSeconds(60)).build()));
        when(words.upsert(eq(ORG), any())).thenReturn(false);

        // when
        service.write(PAIRING, "changed while ongoing");

        // then
        verify(words).upsert(ORG,
                Words.builder().pairingId(PAIRING).body("changed while ongoing").writtenAt(TODAY).build());
        verifyNoInteractions(notices);
    }

    @Test
    void theMentorOpensOnceAndAChangeSealsItAgain() {

        // given
        signedInAs(MIA);
        pairing(endedDaysAgo(1));
        var changed = NOW.minus(Duration.ofDays(1));
        when(words.toMentor(ORG, MIA)).thenReturn(List.of(WordsForMentor.builder().pairingId(PAIRING)
                .communityId(COMMUNITY).menteeUserId(MAX).body("Changed").writtenAt(NOW.minus(Duration.ofDays(3)))
                .updatedAt(changed).openedAt(null).startedAt(NOW.minus(Duration.ofDays(90))).build()));

        // when
        service.open(PAIRING);
        service.open(PAIRING);
        var actual = service.mine();

        // then
        verify(words, org.mockito.Mockito.times(2)).markOpened(ORG, PAIRING, TODAY);
        assertThat(actual).singleElement().satisfies(w -> {
            assertThat(w.writtenAt()).isEqualTo(changed);
            assertThat(w.openedAt()).isNull();
        });
    }

    @Test
    void onlyTheMentorOpens() {

        // given
        pairing(endedDaysAgo(1));

        for (var who : List.of(MAX, "zed")) {
            signedInAs(who);

            // when / then
            assertThatThrownBy(() -> service.open(PAIRING)).isInstanceOf(NotFoundException.class);
        }
        verify(words, never()).markOpened(any(), anyLong(), any());
    }

    @Test
    void wordsAreBoundedAndNeverBlank() {

        // given
        signedInAs(MAX);
        pairing(endedDaysAgo(1));
        when(words.upsert(eq(ORG), any())).thenReturn(true);

        // when
        service.write(PAIRING, "x".repeat(1000));

        // then
        verify(words).upsert(eq(ORG), any());
        assertThatThrownBy(() -> service.write(PAIRING, "x".repeat(1001))).isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getCode()).isEqualTo("MENTORING_TOO_LONG");
        assertThatThrownBy(() -> service.write(PAIRING, "   ")).isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getCode()).isEqualTo("field_invalid");
        assertThatThrownBy(() -> service.write(PAIRING, null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void onlyTheMentorReadsWords() {

        // given
        signedInAs(MIA);
        var written = NOW.minus(Duration.ofDays(3));
        when(words.toMentor(ORG, MIA)).thenReturn(List.of(
                WordsForMentor.builder().pairingId(91L).communityId(COMMUNITY).menteeUserId(MAX).body("Thank you")
                        .writtenAt(written).startedAt(NOW.minus(Duration.ofDays(90)))
                        .endedAt(NOW.minus(Duration.ofDays(5))).openedAt(TODAY).build(),
                WordsForMentor.builder().pairingId(92L).communityId(COMMUNITY).menteeUserId(null).body("Erased mentee")
                        .writtenAt(written.minus(Duration.ofDays(1))).startedAt(NOW.minus(Duration.ofDays(200)))
                        .build()));

        // when
        var actual = service.mine();

        // then
        verify(words).toMentor(ORG, MIA);
        verify(words, never()).toMentor(ORG, MAX);
        assertThat(actual).extracting(WordsView::pairingId).containsExactly(91L, 92L);
        assertThat(actual.get(0)).isEqualTo(WordsView.builder().pairingId(91L).communitySlug("platform")
                .communityName("Platform").menteeId(MAX).menteeName("Max").menteeAvatarUrl("/a/max")
                .startedAt(NOW.minus(Duration.ofDays(90))).endedAt(NOW.minus(Duration.ofDays(5))).body("Thank you")
                .writtenAt(written).openedAt(TODAY).build());
        assertThat(actual.get(1)).satisfies(w -> {
            assertThat(w.menteeId()).isNull();
            assertThat(w.menteeName()).isEqualTo("Deleted member");
            assertThat(w.endedAt()).isNull();
        });
    }

    @Test
    void aDelegatedSessionTouchesNothing() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.write(PAIRING, "hello")).isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.takeBack(PAIRING)).isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.open(PAIRING)).isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.mine()).isInstanceOf(OwnSignInRequiredException.class);
        verifyNoInteractions(pairings, words, notices);
    }

    @Test
    void wordsToAnErasedMentorAreClosed() {

        // given
        signedInAs(MAX);
        pairing(endedDaysAgo(10).mentorUserId(null).endReason(EndReason.LEFT));

        // when / then
        assertThatThrownBy(() -> service.write(PAIRING, "hello")).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("MENTORING_WORDS_CLOSED");
        verify(words, never()).upsert(any(), any());
        verifyNoInteractions(notices);
    }
}

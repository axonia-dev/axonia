package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentorHistory;
import dev.axonia.mentoring.domain.MentorListing;
import dev.axonia.mentoring.domain.MentoringGuild;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class MentorListingNoticesTest {

    private static final String ORG = "org-1";
    private static final long COMMUNITY = 7L;
    private static final MentoringGuild GUILD = new MentoringGuild("platform", "Platform");
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00.123456Z");
    private static final Instant NOW_MILLIS = Instant.parse("2026-11-24T10:00:00.123Z");

    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PairingRepository pairings;
    @Mock
    private MentorListingRepository listings;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private MentoringNotices notices;

    private final Map<String, MentorListing> stored = new HashMap<>();

    private MentorListingNotices sut;

    @BeforeEach
    void setUp() {

        when(communityAccess.mentoringCommunityIdsIn(ORG)).thenReturn(List.of(COMMUNITY));
        when(communityAccess.guildOf(ORG, COMMUNITY)).thenReturn(GUILD);
        when(communityAccess.memberIdsOf(ORG, COMMUNITY)).thenReturn(Set.of("mia", "noah", "lea"));
        when(personDirectory.presentOf(eq(ORG), any())).thenAnswer(invocation -> Set.copyOf(invocation.getArgument(1)));
        when(listings.in(ORG, COMMUNITY)).thenAnswer(invocation -> Map.copyOf(stored));
        doAnswer(invocation -> mark(invocation.getArgument(2), true, invocation.getArgument(3))).when(listings)
                .markAsked(eq(ORG), eq(COMMUNITY), anyString(), any());
        doAnswer(invocation -> mark(invocation.getArgument(2), false, invocation.getArgument(3))).when(listings)
                .markTold(eq(ORG), eq(COMMUNITY), anyString(), any());
        sut = new MentorListingNotices(communityAccess, pairings, listings, personDirectory, notices,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Object mark(String userId, boolean asked, Instant at) {

        var current = stored.getOrDefault(userId,
                new MentorListing(COMMUNITY, userId, ListingSwitch.DEFAULT, null, null));
        stored.put(userId, new MentorListing(COMMUNITY, userId, current.state(), asked ? at : current.askedAt(),
                asked ? current.toldAt() : at));
        return null;
    }

    private void histories(MentorHistory... histories) {

        when(pairings.mentorHistoriesIn(ORG, COMMUNITY)).thenReturn(List.of(histories));
    }

    private void switched(String userId, ListingSwitch state) {

        stored.put(userId, new MentorListing(COMMUNITY, userId, state, null, null));
    }

    private static MentorHistory unshownAlone(String mentorId) {

        return new MentorHistory(mentorId, false, false, true);
    }

    private static MentorHistory shown(String mentorId) {

        return new MentorHistory(mentorId, true, false, false);
    }

    @Test
    void asksOnceForUnshownAlone() {

        // given
        histories(unshownAlone("mia"));

        // when
        sut.sweep(ORG);

        // then
        verify(notices).listingAsk(ORG, COMMUNITY, GUILD, "mia");
        verify(listings).markAsked(ORG, COMMUNITY, "mia", NOW_MILLIS);
        verify(notices, never()).listed(anyLong(), any(), anyString());
    }

    @Test
    void neverAsksWhenOnOrOff() {

        // given
        histories(unshownAlone("mia"), unshownAlone("noah"));
        switched("mia", ListingSwitch.ON);
        switched("noah", ListingSwitch.OFF);

        // when
        sut.sweep(ORG);

        // then
        verify(notices, never()).listingAsk(anyString(), anyLong(), any(), anyString());
        verify(notices, never()).listed(anyLong(), any(), anyString());
    }

    @Test
    void tellsOnceWhenListedByDefault() {

        // given
        histories(shown("mia"), new MentorHistory("noah", false, true, true));

        // when
        sut.sweep(ORG);

        // then
        verify(notices).listed(COMMUNITY, GUILD, "mia");
        verify(notices).listed(COMMUNITY, GUILD, "noah");
        verify(listings).markTold(ORG, COMMUNITY, "mia", NOW_MILLIS);
        verify(listings).markTold(ORG, COMMUNITY, "noah", NOW_MILLIS);
        verify(notices, never()).listingAsk(anyString(), anyLong(), any(), anyString());
    }

    @Test
    void neverTellsWhenOff() {

        // given
        histories(shown("mia"));
        switched("mia", ListingSwitch.OFF);

        // when
        sut.sweep(ORG);

        // then
        verify(notices, never()).listed(anyLong(), any(), anyString());
        verify(listings, never()).markTold(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void neverTellsWhenOn() {

        // given
        histories(shown("mia"));
        switched("mia", ListingSwitch.ON);

        // when
        sut.sweep(ORG);

        // then
        verify(notices, never()).listed(anyLong(), any(), anyString());
    }

    @Test
    void secondSweepSendsNothing() {

        // given
        histories(unshownAlone("mia"), shown("noah"));
        sut.sweep(ORG);

        // when
        sut.sweep(ORG);

        // then
        verify(notices).listingAsk(ORG, COMMUNITY, GUILD, "mia");
        verify(notices).listed(COMMUNITY, GUILD, "noah");
    }

    @Test
    void skipsErasedAndFormerMembers() {

        // given
        histories(shown("gone"), shown("lea"), unshownAlone("noah"));
        when(personDirectory.presentOf(eq(ORG), any())).thenReturn(Set.of("noah"));

        // when
        sut.sweep(ORG);

        // then
        verify(personDirectory).presentOf(eq(ORG), argThat(ids -> Set.copyOf(ids).equals(Set.of("lea", "noah"))));
        verify(notices).listingAsk(ORG, COMMUNITY, GUILD, "noah");
        verify(notices, never()).listed(anyLong(), any(), anyString());
    }

    @Test
    void aMentorAskedBeforeIsToldOnceAShownPairingListsThem() {

        // given
        histories(new MentorHistory("mia", true, false, true));
        stored.put("mia", new MentorListing(COMMUNITY, "mia", ListingSwitch.DEFAULT, NOW_MILLIS, null));

        // when
        sut.sweep(ORG);

        // then
        verify(notices).listed(COMMUNITY, GUILD, "mia");
    }

    @Test
    void aCommunityWithoutMentorsReadsNothingElse() {

        // given
        histories();

        // when
        sut.sweep(ORG);

        // then
        verify(listings, never()).in(anyString(), anyLong());
        verifyNoInteractions(notices, personDirectory);
    }

    @Test
    void aMentorWithNothingToShowIsNeitherAskedNorTold() {

        // given
        histories(new MentorHistory("mia", false, false, false));

        // when
        sut.sweep(ORG);

        // then
        verifyNoInteractions(notices);
    }
}

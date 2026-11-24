package dev.axonia.mentoring.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import dev.axonia.mentoring.api.MentorFace;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentorHistory;
import dev.axonia.mentoring.domain.MentorListing;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MentorRosterReadsTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "platform";
    private static final String ME = "me";
    private static final long COMMUNITY = 7L;
    private static final PageRequest FIRST = PageRequest.of(0, 20);

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PairingRepository pairings;
    @Mock
    private MentorListingRepository listings;
    @Mock
    private PersonDirectory personDirectory;

    private MentorRosterReads reads;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ME);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(true));
        when(communityAccess.isMember(COMMUNITY, ME)).thenReturn(true);
        when(communityAccess.memberIdsOf(ORG, COMMUNITY))
                .thenReturn(Set.of(ME, "zoe", "ana", "bob", "leo", "max", "eve"));
        when(listings.in(ORG, COMMUNITY)).thenReturn(Map.of());
        when(personDirectory.presentOf(any(), anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(1);
            return Set.copyOf(ids);
        });
        when(personDirectory.resolve(anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            return ids.stream()
                    .collect(Collectors.toMap(id -> id, id -> new MentoringPerson(id, name(id), null, "avatar/" + id)));
        });
        reads = new MentorRosterReads(organizationContext, currentUser, communityAccess, pairings, listings,
                personDirectory);
    }

    @Test
    void ordersMentoringNowThenName() {

        // given
        when(pairings.mentorHistoriesIn(ORG, COMMUNITY)).thenReturn(List.of(now("zoe"), here("ana"), now("bob")));

        // when
        var actual = reads.page(SLUG, FIRST);

        // then
        assertThat(actual.getContent())
                .extracting(MentorFace::userId, MentorFace::displayName, MentorFace::avatarUrl, MentorFace::status)
                .containsExactly(tuple("bob", "Bob", "avatar/bob", "MENTORING_NOW"),
                        tuple("zoe", "Zoe", "avatar/zoe", "MENTORING_NOW"),
                        tuple("ana", "Ana", "avatar/ana", "MENTORED_HERE"));
        assertThat(actual.getTotalElements()).isEqualTo(3);
    }

    @Test
    void namesCompareWithoutCaseThenById() {

        // given
        when(communityAccess.memberIdsOf(ORG, COMMUNITY)).thenReturn(Set.of("bob", "eve", "eve2"));
        when(personDirectory.resolve(anyCollection())).thenReturn(Map.of("bob",
                new MentoringPerson("bob", "bob", null, null), "eve", new MentoringPerson("eve", "Eve", null, null),
                "eve2", new MentoringPerson("eve2", "eve", null, null)));
        when(pairings.mentorHistoriesIn(ORG, COMMUNITY)).thenReturn(List.of(here("eve2"), here("eve"), here("bob")));

        // when
        var actual = reads.page(SLUG, FIRST);

        // then
        assertThat(actual.getContent()).extracting(MentorFace::userId).containsExactly("bob", "eve", "eve2");
    }

    @Test
    void theSwitchDecides() {

        // given
        when(pairings.mentorHistoriesIn(ORG, COMMUNITY))
                .thenReturn(List.of(now("zoe"), unshown("ana"), unshown("bob"), here("leo")));
        when(listings.in(ORG, COMMUNITY)).thenReturn(Map.of("zoe", listing("zoe", ListingSwitch.OFF), "ana",
                listing("ana", ListingSwitch.ON), "leo", listing("leo", ListingSwitch.DEFAULT)));

        // when
        var actual = reads.page(SLUG, FIRST);

        // then
        assertThat(actual.getContent()).extracting(MentorFace::userId).containsExactly("ana", "leo");
    }

    @Test
    void excludesTheCaller() {

        // given
        when(pairings.mentorHistoriesIn(ORG, COMMUNITY)).thenReturn(List.of(now(ME), now("zoe")));

        // when
        var actual = reads.page(SLUG, FIRST);

        // then
        assertThat(actual.getContent()).extracting(MentorFace::userId).containsExactly("zoe");
        assertThat(actual.getTotalElements()).isEqualTo(1);
    }

    @Test
    void excludesFormerMembers() {

        // given
        when(communityAccess.memberIdsOf(ORG, COMMUNITY)).thenReturn(Set.of(ME, "zoe", "max"));
        when(personDirectory.presentOf(any(), anyCollection())).thenReturn(Set.of("zoe"));
        when(pairings.mentorHistoriesIn(ORG, COMMUNITY)).thenReturn(List.of(now("zoe"), here("gone"), here("max")));

        // when
        var actual = reads.page(SLUG, FIRST);

        // then
        assertThat(actual.getContent()).extracting(MentorFace::userId).containsExactly("zoe");
    }

    @Test
    void nobodyListedReadsNoPeople() {

        // when
        var actual = reads.page(SLUG, FIRST);

        // then
        assertThat(actual.getContent()).isEmpty();
        assertThat(actual.getTotalElements()).isZero();
        verifyNoInteractions(personDirectory);
    }

    @Test
    void ignoresClientSort() {

        // given
        when(pairings.mentorHistoriesIn(ORG, COMMUNITY)).thenReturn(List.of(now("zoe"), here("ana"), now("bob")));

        // when
        var actual = reads.page(SLUG, PageRequest.of(0, 20, Sort.by("displayName").descending()));

        // then
        assertThat(actual.getContent()).extracting(MentorFace::userId).containsExactly("bob", "zoe", "ana");
        assertThat(actual.getSort().isSorted()).isFalse();
    }

    @Test
    void pagesThroughInTheSameOrder() {

        // given
        when(pairings.mentorHistoriesIn(ORG, COMMUNITY)).thenReturn(List.of(now("zoe"), here("ana"), now("bob")));

        // when
        var actual = reads.page(SLUG, PageRequest.of(1, 2));

        // then
        assertThat(actual.getContent()).extracting(MentorFace::userId).containsExactly("ana");
        assertThat(actual.getTotalElements()).isEqualTo(3);
        assertThat(actual.getTotalPages()).isEqualTo(2);
    }

    @Test
    void pageBeyondTheEndIsEmpty() {

        // given
        when(pairings.mentorHistoriesIn(ORG, COMMUNITY)).thenReturn(List.of(now("zoe"), here("ana"), now("bob")));

        // when
        var actual = reads.page(SLUG, PageRequest.of(99, 20));

        // then
        assertThat(actual.getContent()).isEmpty();
        assertThat(actual.getTotalElements()).isEqualTo(3);
        assertThat(actual.getTotalPages()).isEqualTo(1);
    }

    @Test
    void refusesDelegated() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> reads.page(SLUG, FIRST)).isInstanceOf(OwnSignInRequiredException.class);
        verifyNoInteractions(pairings, listings, personDirectory);
    }

    @Test
    void refusesNonMembers() {

        // given
        when(communityAccess.isMember(COMMUNITY, ME)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> reads.page(SLUG, FIRST)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(pairings, listings, personDirectory);
    }

    @Test
    void refusesWhenMentoringIsOff() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(false));

        // when / then
        assertThatThrownBy(() -> reads.page(SLUG, FIRST)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("switched off");
        verifyNoInteractions(pairings, listings, personDirectory);
    }

    private static MentoringCommunity community(boolean enabled) {

        return new MentoringCommunity(COMMUNITY, SLUG, "Platform", false, enabled);
    }

    private static MentorHistory now(String id) {

        return new MentorHistory(id, true, false, false);
    }

    private static MentorHistory here(String id) {

        return new MentorHistory(id, false, true, false);
    }

    private static MentorHistory unshown(String id) {

        return new MentorHistory(id, false, false, true);
    }

    private static MentorListing listing(String id, ListingSwitch state) {

        return new MentorListing(COMMUNITY, id, state, null, null);
    }

    private static String name(String id) {

        return id.substring(0, 1).toUpperCase() + id.substring(1);
    }
}

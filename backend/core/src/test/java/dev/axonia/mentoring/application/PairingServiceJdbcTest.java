package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.mentoring.adapter.out.persistence.AvailabilityRepositoryImpl;
import dev.axonia.mentoring.adapter.out.persistence.PairingRepositoryImpl;
import dev.axonia.mentoring.adapter.out.persistence.ProposalRepositoryImpl;
import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.Availability;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The lifecycle against the real tables: ending frees the mentor's slot, leaving touches one guild only. */
@Import({ProposalRepositoryImpl.class, PairingRepositoryImpl.class, AvailabilityRepositoryImpl.class})
class PairingServiceJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final long PLATFORM = 7L;
    private static final long DATA = 8L;
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @Autowired
    private ProposalRepository proposals;
    @Autowired
    private PairingRepository pairings;
    @Autowired
    private AvailabilityRepository availabilities;

    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final CommunityAccess communityAccess = mock(CommunityAccess.class);
    private final PersonDirectory personDirectory = mock(PersonDirectory.class);

    private PairingService service;

    @BeforeEach
    void setUp() {

        OrganizationContext organizationContext = () -> ORG;
        when(communityAccess.nameOf(ORG, PLATFORM)).thenReturn("Platform");
        when(communityAccess.nameOf(ORG, DATA)).thenReturn("Data");
        when(personDirectory.displayName(ORG, "leo")).thenReturn("Leo");
        service = new PairingService(organizationContext, currentUser, communityAccess, pairings, proposals,
                mock(JournalRepository.class), availabilities, personDirectory, mock(MentoringNotices.class),
                mock(MemberClocks.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private long pair(long community, String mentor, String mentee) {

        return pairings.insert(ORG, Pairing.builder().communityId(community).mentorUserId(mentor).menteeUserId(mentee)
                .startedAt(NOW.minusSeconds(3600)).build());
    }

    private long propose(long community, String mentor, String mentee, Role by) {

        return proposals.insert(ORG,
                Proposal.builder().communityId(community).mentorUserId(mentor).menteeUserId(mentee).proposedBy(by)
                        .topics(List.of("Kafka")).message("Hello").status(ProposalStatus.OPEN)
                        .createdAt(NOW.minusSeconds(60)).build());
    }

    @Test
    void endingByChoiceFreesTheMentorsSlot() {

        // given
        when(currentUser.userId()).thenReturn("mia");
        var id = pair(PLATFORM, "mia", "leo");
        assertThat(pairings.ongoingAsMentor(ORG, PLATFORM, "mia")).isEqualTo(1);

        // when
        service.end(id);

        // then
        assertThat(pairings.ongoingAsMentor(ORG, PLATFORM, "mia")).isZero();
        assertThat(pairings.find(ORG, id)).get().extracting(Pairing::endReason, Pairing::endedAt)
                .containsExactly(EndReason.MENTOR, NOW);
    }

    @Test
    void leavingOneGuildLeavesTheOtherUntouched() {

        // given
        var here = pair(PLATFORM, "mia", "leo");
        var there = pair(DATA, "mia", "leo");
        var openHere = propose(PLATFORM, "noah", "leo", Role.MENTEE);
        var openThere = propose(DATA, "noah", "leo", Role.MENTEE);
        availabilities.upsert(ORG, Availability.builder().communityId(PLATFORM).userId("leo").lookingForMentor(true)
                .updatedAt(NOW).build());
        availabilities.upsert(ORG,
                Availability.builder().communityId(DATA).userId("leo").lookingForMentor(true).updatedAt(NOW).build());

        // when
        service.departedFromGuild(ORG, PLATFORM, "leo");

        // then
        assertThat(pairings.find(ORG, here)).get().extracting(Pairing::endReason).isEqualTo(EndReason.LEFT);
        assertThat(pairings.find(ORG, there)).get().extracting(Pairing::endedAt).isNull();
        assertThat(proposals.find(ORG, openHere)).get().extracting(Proposal::status)
                .isEqualTo(ProposalStatus.WITHDRAWN);
        assertThat(proposals.find(ORG, openThere)).get().extracting(Proposal::status).isEqualTo(ProposalStatus.OPEN);
        assertThat(availabilities.find(ORG, PLATFORM, "leo")).isEmpty();
        assertThat(availabilities.find(ORG, DATA, "leo")).isPresent();
    }

    @Test
    void deactivationEndsEveryOngoingPairingAndFreesTheSlot() {

        // given
        var here = pair(PLATFORM, "leo", "mia");
        var there = pair(DATA, "noah", "leo");
        var open = propose(DATA, "noah", "leo", Role.MENTEE);

        // when
        service.deactivated(ORG, "leo");

        // then
        assertThat(pairings.ongoingAsMentor(ORG, PLATFORM, "leo")).isZero();
        assertThat(pairings.find(ORG, here)).get().extracting(Pairing::endReason).isEqualTo(EndReason.DEACTIVATED);
        assertThat(pairings.find(ORG, there)).get().extracting(Pairing::endReason).isEqualTo(EndReason.DEACTIVATED);
        assertThat(proposals.find(ORG, open)).get().extracting(Proposal::status).isEqualTo(ProposalStatus.WITHDRAWN);
    }
}

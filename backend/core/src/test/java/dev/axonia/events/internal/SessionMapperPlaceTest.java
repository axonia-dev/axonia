package dev.axonia.events.internal;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.MessageOutcomePort;
import dev.axonia.events.CommunityAccess;
import dev.axonia.events.MeetingPlace;
import dev.axonia.events.Session;
import dev.axonia.events.mother.SeriesEntityMother;
import dev.axonia.events.mother.SessionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The three place columns as {@link SessionMapper} actually reads them off a row: through {@link MeetingPlaces} and
 * {@link MeetingPlace#resolve}, from real {@link SessionEntity} and {@link SeriesEntity} instances rather than from
 * values handed straight to the resolver. {@code MeetingPlaceTest} already proves the resolution rule in isolation;
 * what is worth a second test is that the mapping path obeys it, because that is where a part read out of order or a
 * column resolved on its own would show up.
 */
@MockitoSettings
class SessionMapperPlaceTest {

    @Mock
    private SectionTypeRepository sectionTypeRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private MessageOutcomePort messageOutcomePort;

    private SessionMapper sut;

    @BeforeEach
    void wire() {

        sut = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeRepository, attendanceRepository, seriesRepository));
    }

    private static final MeetingPlace STANDING = new MeetingPlace("Aurora room, HQ",
            "https://teams.microsoft.com/l/abc", "ID 391 2244");

    private Session mapped(SessionEntity.SessionEntityBuilder session) {

        when(attendanceRepository.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        when(seriesRepository.findByOrganizationIdAndIdIn("default", Set.of(1L)))
                .thenReturn(List.of(SeriesEntityMother.any().id(1L).location(STANDING.location())
                        .joinUrl(STANDING.joinUrl()).joiningDetails(STANDING.joiningDetails()).build()));
        return sut.toSession(session.id(5L).sections(List.of()).build());
    }

    @Test
    @DisplayName("a session naming only a location does not inherit the series' link or details")
    void ownLocationDetachesTheBlock() {

        var actual = mapped(SessionEntityMother.any().location("Room 4B, Gasperich"));

        assertThat(actual.place()).isEqualTo(new MeetingPlace("Room 4B, Gasperich", null, null));
    }

    @Test
    @DisplayName("the override and the standing place stay apart so a form can tell inheritance from a copy")
    void overrideAndStandingStayApart() {

        var inheriting = mapped(SessionEntityMother.any());

        assertThat(inheriting.placeOverride()).isEqualTo(MeetingPlace.NONE);
        assertThat(inheriting.seriesPlace()).isEqualTo(STANDING);
        assertThat(inheriting.place()).isEqualTo(STANDING);
    }

    @Test
    @DisplayName("an overriding session still reports the standing place it turned down")
    void anOverrideKeepsTheStandingPlaceReadable() {

        var own = new MeetingPlace("Room 4B, Gasperich", "https://meet.example/this-week", "PIN 0042");

        var actual = mapped(SessionEntityMother.any().location(own.location()).joinUrl(own.joinUrl())
                .joiningDetails(own.joiningDetails()));

        assertThat(actual.placeOverride()).isEqualTo(own);
        assertThat(actual.seriesPlace()).isEqualTo(STANDING);
        assertThat(actual.place()).isEqualTo(own);
    }
}

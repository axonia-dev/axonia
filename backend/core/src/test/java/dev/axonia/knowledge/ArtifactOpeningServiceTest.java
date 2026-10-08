package dev.axonia.knowledge;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.knowledge.internal.ArtifactEntity;
import dev.axonia.knowledge.internal.ArtifactOpeningWriter;
import dev.axonia.knowledge.internal.ArtifactRepository;
import dev.axonia.knowledge.internal.RecordOpeningCommand;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactOpeningServiceTest {

    /**
     * A non-UTC zone, deliberately given to both the injected {@link Clock} and (in {@link #pinJvmDefaultZone()}) the
     * JVM's default zone, so that either kind of "forgot to hard-code UTC" bug computes the same wrong date rather than
     * accidentally agreeing with the correct one.
     */
    private static final ZoneId NON_UTC_ZONE = ZoneId.of("America/Los_Angeles");

    /**
     * Just past midnight UTC on the 16th, so that {@link #NON_UTC_ZONE} (seven hours behind) computes the 15th - a
     * different date than the correct, UTC-anchored answer. A bug that reads {@code clock.getZone()} instead of a
     * hard-coded {@code ZoneOffset.UTC} is caught this way, and so is a bug that reads {@code ZoneId.systemDefault()}:
     * {@link #pinJvmDefaultZone()} makes that zone {@link #NON_UTC_ZONE} too, rather than trusting that whichever
     * machine runs this test happens to sit west of UTC. It does not, reliably: this repository's own dev workstation
     * sits in Europe and its CI defaults to UTC, and neither would have tripped a {@code ZoneId.systemDefault()} bug
     * against this instant without the explicit pin.
     */
    private static final Instant NOW = Instant.parse("2026-04-16T00:30:00Z");

    private static final LocalDate EXPECTED_OPENED_ON = LocalDate.of(2026, 4, 16);

    private final ArtifactOpeningWriter writer = mock(ArtifactOpeningWriter.class);
    private final ArtifactRepository artifacts = mock(ArtifactRepository.class);
    private final CommunityAccess communityAccess = mock(CommunityAccess.class);

    private TimeZone originalDefaultZone;

    private ArtifactOpeningService sut;

    @BeforeEach
    void setUp() {

        pinJvmDefaultZone();
        sut = new ArtifactOpeningService(writer, artifacts, communityAccess, () -> "bob", () -> "default",
                Clock.fixed(NOW, NON_UTC_ZONE));
    }

    @AfterEach
    void restoreJvmDefaultZone() {

        TimeZone.setDefault(originalDefaultZone);
    }

    private void pinJvmDefaultZone() {

        originalDefaultZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(NON_UTC_ZONE));
    }

    @Test
    void record_writesTheOpeningForAMember() {

        // given
        when(artifacts.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(ArtifactEntity.builder().id(3L)
                .communityId(7L).contributorUserId("alice").organizationId("default").build()));
        when(communityAccess.isMember(7L, "bob")).thenReturn(true);

        // when
        sut.record(3L);

        // then
        verify(writer).record(expectedOpening());
    }

    @Test
    void record_recordsAContributorOpeningTheirOwnArtifact() {

        // given - prospective regression guard: self-exclusion belongs to the aggregate, not the write, or the
        // member's own data export would be incomplete
        when(artifacts.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(ArtifactEntity.builder().id(3L)
                .communityId(7L).contributorUserId("bob").organizationId("default").build()));
        when(communityAccess.isMember(7L, "bob")).thenReturn(true);

        // when
        sut.record(3L);

        // then
        verify(writer).record(expectedOpening());
    }

    @Test
    void record_refusesANonMember() {

        // given
        when(artifacts.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(ArtifactEntity.builder().id(3L)
                .communityId(7L).contributorUserId("alice").organizationId("default").build()));
        when(communityAccess.isMember(7L, "bob")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.record(3L)).isInstanceOf(ForbiddenException.class);
        verify(writer, never()).record(any());
    }

    @Test
    void record_refusesAnArtifactThatDoesNotExist() {

        // given
        when(artifacts.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.record(3L)).isInstanceOf(NotFoundException.class);
        verify(writer, never()).record(any());
    }

    private RecordOpeningCommand expectedOpening() {

        return RecordOpeningCommand.builder().organizationId("default").communityId(7L).artifactId(3L)
                .memberUserId("bob").createdAt(NOW).openedOn(EXPECTED_OPENED_ON).build();
    }
}

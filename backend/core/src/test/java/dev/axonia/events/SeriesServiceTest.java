package dev.axonia.events;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.events.internal.SectionTypeCatalog;
import dev.axonia.events.internal.SeriesRepository;
import dev.axonia.events.mother.SeriesEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@MockitoSettings
class SeriesServiceTest {

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private SectionTypeCatalog sectionTypeCatalog;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @InjectMocks
    private SeriesService sut;

    @Test
    void start() {

        // given
        var request = new CreateSeriesRequest("Frontend Dojo", new Cadence(1, CadenceUnit.WEEK),
                LocalDate.parse("2026-06-03"), java.time.LocalTime.of(18, 0), "UTC", null, 60,
                List.of(new SectionTemplate(1L, "Main talk", 30)));
        var saved = SeriesEntityMother.any().id(7L).defaultStructure(List.of(dev.axonia.events.internal.SectionTypeRef
                .builder().sectionTypeId(1L).title("Main talk").durationMinutes(30).build())).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isOrganizer(1L, "dev-user")).thenReturn(true);
        // sectionTypeCatalog.require() is a mocked void call: it no-ops by default, exactly the "type exists" case
        when(seriesRepository.save(any())).thenReturn(saved);

        // when
        var actual = sut.start("frontend", request);

        // then
        assertThat(actual.title()).isEqualTo("Frontend Dojo");
        assertThat(actual.defaultStructure()).containsExactly(new SectionTemplate(1L, "Main talk", 30));
    }

    /**
     * A recurring guild reuses one place for years, so the place the organizer types when starting the series has to
     * reach the stored row and come back on the read. Saved through the real builder rather than a canned entity, so a
     * service that accepted the field and dropped it on the way to persistence would fail here.
     */
    @Test
    void start_storesTheStandingPlaceAndReadsItBack() {

        // given
        var request = new CreateSeriesRequest("Frontend Dojo", new Cadence(1, CadenceUnit.WEEK),
                LocalDate.parse("2026-06-03"), java.time.LocalTime.of(18, 0), "UTC",
                new MeetingPlace(null, "https://meet.example/dojo", null), 60, List.of());
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isOrganizer(1L, "dev-user")).thenReturn(true);
        when(seriesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.start("frontend", request);

        // then
        assertThat(actual.place().joinUrl()).isEqualTo("https://meet.example/dojo");
    }

    @Test
    void update_replacesTheStandingPlace() {

        // given a series that already meets somewhere, moving to a new place
        var request = new UpdateSeriesRequest("Renamed", new Cadence(1, CadenceUnit.MONTH),
                LocalDate.parse("2026-06-03"), null, "UTC",
                new MeetingPlace(null, "https://meet.example/new-room", null), null, List.of());
        var entity = SeriesEntityMother.any().id(7L).joinUrl("https://meet.example/old-room").build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(seriesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.update(7L, request);

        // then
        assertThat(actual.place().joinUrl()).isEqualTo("https://meet.example/new-room");
    }

    @Test
    void start_memberBlockedWhenCurated() {

        // given
        var request = new CreateSeriesRequest("Frontend Dojo", new Cadence(1, CadenceUnit.WEEK),
                LocalDate.parse("2026-06-03"), null, "UTC", null, null, List.of());
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(ContributionPolicy.curated());

        // when / then
        assertThatThrownBy(() -> sut.start("frontend", request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void start_memberAllowedWhenPolicyOpen() {

        // given
        var request = new CreateSeriesRequest("Frontend Dojo", new Cadence(1, CadenceUnit.WEEK),
                LocalDate.parse("2026-06-03"), null, "UTC", null, null, List.of());
        var saved = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(true, false, false, false));
        when(communityAccess.isMember(1L, "member")).thenReturn(true);
        when(seriesRepository.save(any())).thenReturn(saved);

        // when
        var actual = sut.start("frontend", request);

        // then
        assertThat(actual.id()).isEqualTo(7L);
    }

    @Test
    void start_nonMemberBlockedEvenWhenPolicyOpen() {

        // given
        var request = new CreateSeriesRequest("Frontend Dojo", new Cadence(1, CadenceUnit.WEEK),
                LocalDate.parse("2026-06-03"), null, "UTC", null, null, List.of());
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("stranger");
        when(communityAccess.isOrganizer(1L, "stranger")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(true, false, false, false));
        when(communityAccess.isMember(1L, "stranger")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.start("frontend", request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void start_unknownSectionTypeRejected() {

        // given
        var request = new CreateSeriesRequest("Frontend Dojo", new Cadence(1, CadenceUnit.WEEK),
                LocalDate.parse("2026-06-03"), null, "UTC", null, null, List.of(new SectionTemplate(999L, null, null)));
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isOrganizer(1L, "dev-user")).thenReturn(true);
        doThrow(new NotFoundException("Section type not found: 999")).when(sectionTypeCatalog).require("default", 999L);

        // when / then
        assertThatThrownBy(() -> sut.start("frontend", request)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_memberBlockedWhenCurated() {

        // given
        var request = new UpdateSeriesRequest("Renamed", new Cadence(1, CadenceUnit.MONTH),
                LocalDate.parse("2026-06-03"), null, "UTC", null, null, List.of());
        var entity = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(ContributionPolicy.curated());

        // when / then
        assertThatThrownBy(() -> sut.update(7L, request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_memberAllowedWhenPolicyOpen() {

        // given
        var request = new UpdateSeriesRequest("Renamed", new Cadence(1, CadenceUnit.MONTH),
                LocalDate.parse("2026-06-03"), null, "UTC", null, null, List.of());
        var entity = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, true, false, false));
        when(communityAccess.isMember(1L, "member")).thenReturn(true);
        when(seriesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.update(7L, request);

        // then
        assertThat(actual.title()).isEqualTo("Renamed");
    }

    @Test
    void pause_memberBlockedEvenWhenPolicyFullyOpen() {

        // given
        var entity = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        lenient().when(communityAccess.contributionPolicy(1L))
                .thenReturn(new ContributionPolicy(true, true, true, false));

        // when / then
        assertThatThrownBy(() -> sut.pause(7L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void resume_memberBlockedEvenWhenPolicyFullyOpen() {

        // given
        var entity = SeriesEntityMother.any().id(7L).status(SeriesStatus.PAUSED).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        lenient().when(communityAccess.contributionPolicy(1L))
                .thenReturn(new ContributionPolicy(true, true, true, false));

        // when / then
        assertThatThrownBy(() -> sut.resume(7L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void end_memberBlockedEvenWhenPolicyFullyOpen() {

        // given
        var entity = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        lenient().when(communityAccess.contributionPolicy(1L))
                .thenReturn(new ContributionPolicy(true, true, true, false));

        // when / then
        assertThatThrownBy(() -> sut.end(7L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void get() {

        // given
        var entity = SeriesEntityMother.any().id(7L).defaultStructure(
                List.of(dev.axonia.events.internal.SectionTypeRef.builder().sectionTypeId(1L).build())).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));

        // when
        var actual = sut.get(7L);

        // then
        assertThat(actual.id()).isEqualTo(7L);
        assertThat(actual.defaultStructure()).containsExactly(new SectionTemplate(1L, null, null));
        assertThat(actual.nextSuggestedAt()).isNotNull();
    }

    @Test
    void list() {

        // given
        var entity = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(seriesRepository.findByOrganizationIdAndCommunityId("default", 1L, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        // when
        var actual = sut.list("frontend", Pageable.unpaged());

        // then
        assertThat(actual.getTotalElements()).isEqualTo(1L);
        assertThat(actual.getContent().getFirst().title()).isEqualTo("Frontend Dojo");
    }

    @Test
    void update() {

        // given
        var request = new UpdateSeriesRequest("Renamed", new Cadence(1, CadenceUnit.MONTH),
                LocalDate.parse("2026-06-03"), java.time.LocalTime.of(9, 0), "UTC", null, 45, List.of());
        var entity = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(seriesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.update(7L, request);

        // then
        assertThat(actual.title()).isEqualTo("Renamed");
        assertThat(actual.cadence()).isEqualTo(new Cadence(1, CadenceUnit.MONTH));
        assertThat(actual.durationMinutes()).isEqualTo(45);
        assertThat(actual.defaultStructure()).isEmpty();
    }

    @Test
    void pause() {

        // given
        var entity = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(seriesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.pause(7L);

        // then
        assertThat(actual.status()).isEqualTo(SeriesStatus.PAUSED);
        assertThat(actual.nextSuggestedAt()).isNull();
    }

    @Test
    void resume() {

        // given
        var entity = SeriesEntityMother.any().id(7L).status(SeriesStatus.PAUSED).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(seriesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.resume(7L);

        // then
        assertThat(actual.status()).isEqualTo(SeriesStatus.ACTIVE);
        assertThat(actual.nextSuggestedAt()).isNotNull();
    }

    @Test
    void end() {

        // given
        var entity = SeriesEntityMother.any().id(7L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(seriesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.end(7L);

        // then
        assertThat(actual.status()).isEqualTo(SeriesStatus.ENDED);
        assertThat(actual.nextSuggestedAt()).isNull();
    }
}

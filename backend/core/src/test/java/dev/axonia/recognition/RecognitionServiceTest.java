package dev.axonia.recognition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.recognition.internal.LoveEntity;
import dev.axonia.recognition.internal.LoveRepository;
import dev.axonia.recognition.internal.RecognizedContributionEntity;
import dev.axonia.recognition.internal.RecognizedContributionRepository;
import dev.axonia.recognition.mother.LoveEntityMother;
import dev.axonia.recognition.mother.RecognizedContributionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class RecognitionServiceTest {

    @Mock
    private RecognizedContributionRepository recognizedContributionRepository;

    @Mock
    private LoveRepository loveRepository;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private SlotSourceAccess slotSourceAccess;

    @Mock
    private ArtifactSourceAccess artifactSourceAccess;

    @Mock
    private ArtifactTypeAccess artifactTypeAccess;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private RecognitionService sut;

    private static LoveRequest request() {

        return new LoveRequest(ContributionType.SLOT_PRESENTED, 100L);
    }

    private static ContributionSource slotSource() {

        return ContributionSource.builder().communityId(1L).contributorUserIds(List.of("alice")).title("Talk")
                .occurredAt(Instant.parse("2026-06-01T10:00:00Z")).build();
    }

    @Test
    void love_createsRecognizedContributionAndLove() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(slotSource()));
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.empty());
        when(recognizedContributionRepository.save(any())).thenReturn(rc);
        when(loveRepository.save(any()))
                .thenReturn(LoveEntity.builder().id(9L).organizationId("default").recognizedContributionId(5L)
                        .loverUserId("bob").createdAt(Instant.parse("2026-06-02T00:00:00Z")).build());

        // when
        var actual = sut.love(request());

        // then
        assertThat(actual).usingRecursiveAssertion()
                .isEqualTo(Love.builder().id(9L).contributionType(ContributionType.SLOT_PRESENTED).contributionId(100L)
                        .communityId(1L).loverUserId("bob").createdAt(Instant.parse("2026-06-02T00:00:00Z")).build());
        verify(recognizedContributionRepository).save(any());
        verify(events).publishEvent(new ContributionLoved(ContributionType.SLOT_PRESENTED, 100L, 1L));
    }

    @Test
    void love_reusesExistingRecognizedContribution() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(slotSource()));
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.of(rc));
        when(loveRepository.save(any())).thenReturn(LoveEntityMother.any().id(9L).recognizedContributionId(5L)
                .createdAt(Instant.parse("2026-06-02T00:00:00Z")).build());

        // when
        var actual = sut.love(request());

        // then
        assertThat(actual.id()).isEqualTo(9L);
        verify(recognizedContributionRepository, never()).save(any());
    }

    @Test
    void love_recognizedContributionCreationRace_refetches() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(slotSource()));
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.empty()).thenReturn(Optional.of(rc));
        when(recognizedContributionRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));
        when(loveRepository.save(any())).thenReturn(LoveEntityMother.any().id(9L).recognizedContributionId(5L)
                .createdAt(Instant.parse("2026-06-02T00:00:00Z")).build());

        // when
        var actual = sut.love(request());

        // then
        assertThat(actual.id()).isEqualTo(9L);
    }

    @Test
    void love_refusesTheSoleContributorLovingTheirOwnWork() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(slotSource())); // contributors:
                                                                                                        // [alice]

        // when / then
        assertThatThrownBy(() -> sut.love(new LoveRequest(ContributionType.SLOT_PRESENTED, 100L)))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("NO_SELF_LOVE"));
    }

    /**
     * Loving your own work is refused because the count is a signal about how others received it. Being one of two
     * authors does not change that, and list equality only caught the case where you were the only one.
     */
    @Test
    void aCoAuthorCannotLoveTheirOwnContribution() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("u-1");
        var source = ContributionSource.builder().communityId(1L).contributorUserIds(List.of("u-1", "u-2"))
                .title("Talk").occurredAt(Instant.parse("2026-06-01T10:00:00Z")).build();
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(source));

        // when / then
        assertThatThrownBy(() -> sut.love(request())).isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("NO_SELF_LOVE"));
    }

    /**
     * A co-author is refused (see {@link #aCoAuthorCannotLoveTheirOwnContribution}); somebody outside the contributor
     * list is unaffected by that rule and gets credited as the lover as normal.
     */
    @Test
    void love_allowsANonContributorToLoveAMultiAuthorContribution() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        var source = ContributionSource.builder().communityId(1L).contributorUserIds(List.of("alice", "mia"))
                .title("Talk").occurredAt(Instant.parse("2026-06-01T10:00:00Z")).build();
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(source));
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.of(rc));
        when(loveRepository.save(any())).thenReturn(LoveEntityMother.any().id(9L).recognizedContributionId(5L)
                .loverUserId("bob").createdAt(Instant.parse("2026-06-02T00:00:00Z")).build());

        // when
        var actual = sut.love(new LoveRequest(ContributionType.SLOT_PRESENTED, 100L));

        // then
        assertThat(actual.loverUserId()).isEqualTo("bob");
        verify(events).publishEvent(new ContributionLoved(ContributionType.SLOT_PRESENTED, 100L, 1L));
    }

    @Test
    void love_refusesAContributionNobodyMade() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        var source = ContributionSource.builder().communityId(1L).contributorUserIds(List.of()).title("Open slot")
                .occurredAt(Instant.parse("2026-06-01T10:00:00Z")).build();
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(source));

        // when / then
        assertThatThrownBy(() -> sut.love(new LoveRequest(ContributionType.SLOT_PRESENTED, 100L)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void love_rejectsNonMember() {

        // given - membership of the resolved community is denied
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isMember(1L, "bob")).thenReturn(false);
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(slotSource()));

        // when / then
        assertThatThrownBy(() -> sut.love(request())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void love_duplicateThrowsConflict() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(slotSource()));
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.of(rc));
        when(loveRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        // when / then
        assertThatThrownBy(() -> sut.love(request())).isInstanceOf(ConflictException.class)
                .hasMessageContaining("already loved");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void love_rejectsUnknownContribution() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.love(request())).isInstanceOf(NotFoundException.class);
        verify(recognizedContributionRepository, never()).save(any());
    }

    @Test
    void love_derivesAttributionFromTheResolvedSource() {

        // given - a member loves a slot; every persisted attribution field must come from the source, not the wire
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        when(slotSourceAccess.slotContribution("default", 100L)).thenReturn(Optional.of(slotSource()));
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.empty());
        when(recognizedContributionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0, RecognizedContributionEntity.class).toBuilder().id(1L).build());
        when(loveRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0, LoveEntity.class).toBuilder().id(9L).build());

        // when
        sut.love(request());

        // then
        var captor = ArgumentCaptor.forClass(RecognizedContributionEntity.class);
        verify(recognizedContributionRepository).save(captor.capture());
        assertThat(captor.getValue().getCommunityId()).isEqualTo(1L);
        assertThat(captor.getValue().getTitle()).isEqualTo("Talk");
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(Instant.parse("2026-06-01T10:00:00Z"));
    }

    @Test
    void love_artifactResolvesThroughKnowledge() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isMember(1L, "bob")).thenReturn(true);
        when(artifactSourceAccess.artifactSource("default", 7L)).thenReturn(Optional.of(ContributionSource.builder()
                .communityId(1L).contributorUserIds(List.of("alice")).title("RFC").occurredAt(Instant.EPOCH).build()));
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.ARTIFACT_CAPTURED, 7L)).thenReturn(Optional.empty());
        when(recognizedContributionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0, RecognizedContributionEntity.class).toBuilder().id(2L).build());
        when(loveRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0, LoveEntity.class).toBuilder().id(10L).build());

        // when
        var actual = sut.love(new LoveRequest(ContributionType.ARTIFACT_CAPTURED, 7L));

        // then
        assertThat(actual.loverUserId()).isEqualTo("bob");
    }

    @Test
    void unlove_deletesOwnLove() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.of(rc));
        var love = LoveEntityMother.any().id(9L).recognizedContributionId(5L).build();
        when(loveRepository.findByOrganizationIdAndRecognizedContributionIdAndLoverUserId("default", 5L, "bob"))
                .thenReturn(Optional.of(love));

        // when
        sut.unlove(ContributionType.SLOT_PRESENTED, 100L);

        // then
        verify(loveRepository).delete(love);
        verify(events).publishEvent(new ContributionUnloved(ContributionType.SLOT_PRESENTED, 100L, 1L));
    }

    @Test
    void unlove_missingThrowsNotFound() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.unlove(ContributionType.SLOT_PRESENTED, 100L))
                .isInstanceOf(NotFoundException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void summary_emptyWhenNoRecognizedContribution() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.empty());

        // when
        var actual = sut.summary(ContributionType.SLOT_PRESENTED, 100L);

        // then
        assertThat(actual).usingRecursiveAssertion()
                .isEqualTo(LoveSummary.builder().count(0).viewerHasLoved(false).build());
    }

    @Test
    void summary_countsAndViewerFlag() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("bob");
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.of(rc));
        when(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", 5L))
                .thenReturn(List.of(LoveEntityMother.any().id(1L).loverUserId("bob").build(),
                        LoveEntityMother.any().id(2L).loverUserId("carol").build()));

        // when
        var actual = sut.summary(ContributionType.SLOT_PRESENTED, 100L);

        // then
        assertThat(actual).usingRecursiveAssertion()
                .isEqualTo(LoveSummary.builder().count(2).viewerHasLoved(true).build());
    }

    @Test
    void myContributions_enrichesSlotWithSessionAndSectionType() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(slotSourceAccess.slotsPresentedBy("default", "alice")).thenReturn(List.of(100L));
        when(artifactSourceAccess.artifactsCapturedBy("default", "alice")).thenReturn(List.of());
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn("default",
                ContributionType.SLOT_PRESENTED, List.of(100L))).thenReturn(List.of(rc));
        when(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", 5L))
                .thenReturn(List.of(LoveEntityMother.any().id(1L).recognizedContributionId(5L).build()));
        when(communityAccess.summaryById(any())).thenReturn(Map.of(1L, new CommunitySummary("eng", "Engineering")));
        when(slotSourceAccess.slotSources(List.of(100L)))
                .thenReturn(Map.of(100L, new SlotSource(42L, 7L, "Workshop", "wrench", "#123456")));
        when(artifactTypeAccess.artifactTypes(List.of())).thenReturn(Map.of());

        // when
        var actual = sut.myContributions();

        // then
        assertThat(actual).usingRecursiveFieldByFieldElementComparator()
                .containsExactly(RecognizedContribution.builder().contributionType(rc.getContributionType())
                        .contributionId(rc.getContributionId()).communityId(rc.getCommunityId()).communitySlug("eng")
                        .communityName("Engineering").title(rc.getTitle()).occurredAt(rc.getOccurredAt()).loveCount(1)
                        .sessionId(42L).typeId(7L).typeLabel("Workshop").typeIcon("wrench").typeAccentColor("#123456")
                        .build());
    }

    @Test
    void myContributions_slotWithNoResolvableSource_leavesTypeFieldsNull() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(slotSourceAccess.slotsPresentedBy("default", "alice")).thenReturn(List.of(100L));
        when(artifactSourceAccess.artifactsCapturedBy("default", "alice")).thenReturn(List.of());
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn("default",
                ContributionType.SLOT_PRESENTED, List.of(100L))).thenReturn(List.of(rc));
        when(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", 5L)).thenReturn(List.of());
        when(communityAccess.summaryById(any())).thenReturn(Map.of(1L, new CommunitySummary("eng", "Engineering")));
        when(slotSourceAccess.slotSources(List.of(100L))).thenReturn(Map.of());
        when(artifactTypeAccess.artifactTypes(List.of())).thenReturn(Map.of());

        // when
        var actual = sut.myContributions();

        // then
        assertThat(actual).singleElement()
                .satisfies(c -> assertThat(c).usingRecursiveComparison()
                        .isEqualTo(RecognizedContribution.builder().contributionType(rc.getContributionType())
                                .contributionId(100L).communityId(1L).communitySlug("eng").communityName("Engineering")
                                .title(rc.getTitle()).occurredAt(rc.getOccurredAt()).loveCount(0).build()));
    }

    @Test
    void myContributions_communityNoLongerResolves_leavesCommunityFieldsNull() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
        var rc = RecognizedContributionEntityMother.any().id(5L).build();
        when(slotSourceAccess.slotsPresentedBy("default", "alice")).thenReturn(List.of(100L));
        when(artifactSourceAccess.artifactsCapturedBy("default", "alice")).thenReturn(List.of());
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn("default",
                ContributionType.SLOT_PRESENTED, List.of(100L))).thenReturn(List.of(rc));
        when(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", 5L))
                .thenReturn(List.of(LoveEntityMother.any().id(1L).recognizedContributionId(5L).build()));
        when(communityAccess.summaryById(any())).thenReturn(Map.of());
        when(slotSourceAccess.slotSources(List.of(100L)))
                .thenReturn(Map.of(100L, new SlotSource(42L, 7L, "Workshop", "wrench", "#123456")));
        when(artifactTypeAccess.artifactTypes(List.of())).thenReturn(Map.of());

        // when
        var actual = sut.myContributions();

        // then
        assertThat(actual).usingRecursiveFieldByFieldElementComparator()
                .containsExactly(RecognizedContribution.builder().contributionType(rc.getContributionType())
                        .contributionId(rc.getContributionId()).communityId(rc.getCommunityId()).communitySlug(null)
                        .communityName(null).title(rc.getTitle()).occurredAt(rc.getOccurredAt()).loveCount(1)
                        .sessionId(42L).typeId(7L).typeLabel("Workshop").typeIcon("wrench").typeAccentColor("#123456")
                        .build());
    }

    @Test
    void myContributions_enrichesArtifactWithTypeBadge() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
        var rc = RecognizedContributionEntityMother.any().id(5L).contributionType(ContributionType.ARTIFACT_CAPTURED)
                .contributionId(200L).build();
        when(slotSourceAccess.slotsPresentedBy("default", "alice")).thenReturn(List.of());
        when(artifactSourceAccess.artifactsCapturedBy("default", "alice")).thenReturn(List.of(200L));
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn("default",
                ContributionType.ARTIFACT_CAPTURED, List.of(200L))).thenReturn(List.of(rc));
        when(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", 5L)).thenReturn(List.of());
        when(communityAccess.summaryById(any())).thenReturn(Map.of(1L, new CommunitySummary("eng", "Engineering")));
        when(slotSourceAccess.slotSources(List.of())).thenReturn(Map.of());
        when(artifactTypeAccess.artifactTypes(List.of(200L)))
                .thenReturn(Map.of(200L, new ArtifactBadge(3L, "RFC", "file-text", "#abcdef")));

        // when
        var actual = sut.myContributions();

        // then
        assertThat(actual).singleElement()
                .satisfies(c -> assertThat(c).usingRecursiveComparison()
                        .isEqualTo(RecognizedContribution.builder().contributionType(ContributionType.ARTIFACT_CAPTURED)
                                .contributionId(200L).communityId(1L).communitySlug("eng").communityName("Engineering")
                                .title(rc.getTitle()).occurredAt(rc.getOccurredAt()).loveCount(0).typeId(3L)
                                .typeLabel("RFC").typeIcon("file-text").typeAccentColor("#abcdef").build()));
    }

    @Test
    void myContributions_creditsEveryCurrentContributor_notTheSnapshot() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("mia");
        when(slotSourceAccess.slotsPresentedBy("default", "mia")).thenReturn(List.of(100L));
        when(artifactSourceAccess.artifactsCapturedBy("default", "mia")).thenReturn(List.of());
        var rc = RecognizedContributionEntityMother.any().id(5L).contributionType(ContributionType.SLOT_PRESENTED)
                .contributionId(100L).communityId(1L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn("default",
                ContributionType.SLOT_PRESENTED, List.of(100L))).thenReturn(List.of(rc));
        when(communityAccess.summaryById(List.of(1L)))
                .thenReturn(Map.of(1L, new CommunitySummary("eng", "Engineering")));
        when(slotSourceAccess.slotSources(List.of(100L))).thenReturn(Map.of());
        when(artifactTypeAccess.artifactTypes(List.of())).thenReturn(Map.of());
        when(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", 5L)).thenReturn(List.of(
                LoveEntityMother.any().loverUserId("bob").build(), LoveEntityMother.any().loverUserId("mia").build()));

        // when
        var actual = sut.myContributions();

        // then: mia sees her co-presented slot, and her own love of it does not count as received
        assertThat(actual).hasSize(1);
        assertThat(actual.getFirst().loveCount()).isEqualTo(1);
    }

    @Test
    void myContributions_artifactWithNoResolvableType_leavesTypeFieldsNull() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
        var rc = RecognizedContributionEntityMother.any().id(5L).contributionType(ContributionType.ARTIFACT_CAPTURED)
                .contributionId(200L).build();
        when(slotSourceAccess.slotsPresentedBy("default", "alice")).thenReturn(List.of());
        when(artifactSourceAccess.artifactsCapturedBy("default", "alice")).thenReturn(List.of(200L));
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn("default",
                ContributionType.ARTIFACT_CAPTURED, List.of(200L))).thenReturn(List.of(rc));
        when(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", 5L)).thenReturn(List.of());
        when(communityAccess.summaryById(any())).thenReturn(Map.of(1L, new CommunitySummary("eng", "Engineering")));
        when(slotSourceAccess.slotSources(List.of())).thenReturn(Map.of());
        when(artifactTypeAccess.artifactTypes(List.of(200L))).thenReturn(Map.of());

        // when
        var actual = sut.myContributions();

        // then
        assertThat(actual).singleElement().satisfies(c -> {
            assertThat(c.typeId()).isNull();
            assertThat(c.sessionId()).isNull();
        });
    }
}

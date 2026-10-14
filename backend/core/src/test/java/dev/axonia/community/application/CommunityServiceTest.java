package dev.axonia.community.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.community.api.CommunityDetailsChanged;
import dev.axonia.community.api.CommunityIdentity;
import dev.axonia.community.api.CommunityListing;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.MembershipFilter;
import dev.axonia.community.application.port.out.CommunitySearch;
import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityKindRecordMother;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.CommunityRecordMother;
import dev.axonia.community.domain.MembershipRecordMother;
import dev.axonia.media.api.MediaLinks;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.metrics.UsageCounters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityServiceTest {

    @Mock
    private CommunityStore communityStore;

    @Mock
    private CommunityKindStore communityKindStore;

    @Mock
    private MembershipLedger membershipLedger;

    @Mock
    private CommunityLookup communities;

    @Mock
    private MembershipAuthorizer membershipAuthorizer;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private MediaLinks mediaLinks;

    @Mock
    private AuthorizationGate gate;

    private Boolean countFixedHere;

    /** A real rule over a stub policy, so a test says what the policy answers rather than how the rule is asked. */
    @Spy
    private CommunityCountRule communityCountRule = new CommunityCountRule(
            List.of(organizationId -> Optional.ofNullable(countFixedHere)));

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    /**
     * The real counters rather than a mock, so the assertions below read the series a dashboard panel reads. A
     * {@code @Spy} is how {@code @InjectMocks} accepts a real collaborator at all.
     */
    @Spy
    private UsageCounters usageCounters = new UsageCounters(meters);

    @InjectMocks
    private CommunityService sut;

    @BeforeEach
    void seedSeams() {

        lenient().when(organizationContext.organizationId()).thenReturn("default");
        var guild = CommunityKindRecordMother.any().id(1L).label("Guild").build();
        lenient().when(communityKindStore.find("default", 1L)).thenReturn(Optional.of(guild));
        lenient().when(communityKindStore.listByLabel("default")).thenReturn(List.of(guild));
    }

    @Test
    void create_requiresCommunityCreatePermission() {
        // given
        org.mockito.Mockito
                .doThrow(new dev.axonia.shared.api.error.ForbiddenException("Missing permission: community:create"))
                .when(gate).requirePermission(Permission.COMMUNITY_CREATE);

        // then
        assertThatThrownBy(() -> sut.create(new CreateCommunityRequest("Rust", 1L, null)))
                .isInstanceOf(dev.axonia.shared.api.error.ForbiddenException.class);
    }

    @Test
    void create_refusedWhereTheCommunitiesAreFixedAtFounding() {

        // given
        countFixedHere = true;

        // when / then
        assertThatThrownBy(() -> sut.create(new CreateCommunityRequest("Rust", 1L, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("COMMUNITY_COUNT_FIXED");
        verifyNoInteractions(communityStore);
    }

    @Test
    void create_persistsActiveCommunityAndAutoJoinsOrganizer() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityStore.slugTaken("default", "frontend-guild")).thenReturn(false);
        when(communityStore.save(any())).thenAnswer(inv -> {
            CommunityRecord e = inv.getArgument(0);
            return e.toBuilder().id(1L).build();
        });

        // when
        var actual = sut.create(new CreateCommunityRequest("Frontend Guild", 1L, "We frontend"));

        // then
        assertThat(actual.slug()).isEqualTo("frontend-guild");
        assertThat(actual.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(actual.memberCount()).isEqualTo(1L);
        assertThat(actual.kind()).isEqualTo("Guild");
        verify(membershipLedger).save(any());
        assertThat(communitiesCreated()).isEqualTo(1.0);
    }

    /**
     * The product counter, asserted on the series a dashboard panel reads rather than on a mock having been called: a
     * call site wired to the wrong constant writes a different series and fails here. A refused create must leave the
     * panel flat - it is the count of communities that exist, not of attempts to make one.
     */
    @Test
    void create_refusedWhereTheCommunitiesAreFixedCountsNothing() {

        countFixedHere = true;

        assertThatThrownBy(() -> sut.create(new CreateCommunityRequest("Rust", 1L, null)))
                .isInstanceOf(ConflictException.class);

        assertThat(communitiesCreated()).isZero();
    }

    private double communitiesCreated() {

        return meters.get("axonia.usage.events").tag("event", "community-created").counter().count();
    }

    @Test
    void create_nullDescriptionStoredAsEmptyString() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityStore.slugTaken("default", "frontend-guild")).thenReturn(false);
        when(communityStore.save(any()))
                .thenAnswer(inv -> ((CommunityRecord) inv.getArgument(0)).toBuilder().id(1L).build());

        // when
        var actual = sut.create(new CreateCommunityRequest("Frontend Guild", 1L, null));

        // then
        assertThat(actual.description()).isEmpty();
    }

    @Test
    void create_resolvesSlugCollision() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityStore.slugTaken("default", "frontend-guild")).thenReturn(true);
        when(communityStore.slugTaken("default", "frontend-guild-2")).thenReturn(false);
        when(communityStore.save(any()))
                .thenAnswer(inv -> ((CommunityRecord) inv.getArgument(0)).toBuilder().id(1L).build());

        // when
        var actual = sut.create(new CreateCommunityRequest("Frontend Guild", 1L, "x"));

        // then
        assertThat(actual.slug()).isEqualTo("frontend-guild-2");
    }

    @Test
    void create_unknownKindRejected() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityKindStore.find("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.create(new CreateCommunityRequest("Frontend Guild", 99L, "x")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void directory_scopesByTenant() {

        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(communityKindStore.listByLabel("default")).thenReturn(java.util.List.of());
        when(communityStore.searchByName("default", CommunityStatus.ACTIVE, "", pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));

        sut.directory(null, pageable);

        verify(communityStore).searchByName("default", CommunityStatus.ACTIVE, "", pageable);
    }

    @Test
    void getBySlug() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(membershipLedger.count("default", 1L)).thenReturn(3L);

        // when
        var actual = sut.getBySlug("backend-guild");

        // then
        assertThat(actual.id()).isEqualTo(1L);
        assertThat(actual.memberCount()).isEqualTo(3L);
        assertThat(actual.kind()).isEqualTo("Guild");
    }

    @Test
    void getBySlug_mintsTheBadgeAndHeroAddressesFromTheirKeys() {

        // given
        var entity = CommunityRecordMother.any().id(1L).badgeKey("communities/1/badge/b.png")
                .heroKey("communities/1/hero/h.webp").build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(mediaLinks.urlFor("communities/1/badge/b.png")).thenReturn("/media/communities/1/badge/b.png");
        when(mediaLinks.urlFor("communities/1/hero/h.webp")).thenReturn("/media/communities/1/hero/h.webp");

        // when
        var actual = sut.getBySlug("backend-guild");

        // then
        assertThat(actual.badgeUrl()).isEqualTo("/media/communities/1/badge/b.png");
        assertThat(actual.heroUrl()).isEqualTo("/media/communities/1/hero/h.webp");
    }

    @Test
    void getBySlug_mintsNoAddressForACommunityWithoutImages() {

        // given
        var entity = CommunityRecordMother.any().id(1L).badgeKey(null).heroKey(null).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when
        var actual = sut.getBySlug("backend-guild");

        // then
        assertThat(actual.badgeUrl()).isNull();
        assertThat(actual.heroUrl()).isNull();
        verifyNoInteractions(mediaLinks);
    }

    @Test
    void getBySlug_notFound() {

        // given
        when(communities.findBySlugOrThrow("default", "missing"))
                .thenThrow(new NotFoundException("Community not found: missing"));

        // when / then
        assertThatThrownBy(() -> sut.getBySlug("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void hubCommunityName_returnsTheSingleCommunitysNameForTheHub() {

        // given - a hub's one community is provisioned with the hub id as its slug
        when(communityStore.findBySlug("acme-hub", "acme-hub"))
                .thenReturn(Optional.of(CommunityRecordMother.any().name("Rust Practitioners").build()));

        // when / then
        assertThat(sut.hubCommunityName("acme-hub")).contains("Rust Practitioners");
    }

    @Test
    void hubCommunityName_emptyWhenNoCommunityUnderThatSlug() {

        // given
        when(communityStore.findBySlug("acme-hub", "acme-hub")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.hubCommunityName("acme-hub")).isEmpty();
    }

    @Test
    void getById() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipLedger.count("default", 1L)).thenReturn(3L);

        // when
        var actual = sut.getById(1L);

        // then
        assertThat(actual.id()).isEqualTo(1L);
        assertThat(actual.memberCount()).isEqualTo(3L);
        assertThat(actual.kind()).isEqualTo("Guild");
    }

    @Test
    void getById_notFound() {

        // given
        when(communities.findByIdOrThrow("default", 99L)).thenThrow(new NotFoundException("Community not found: 99"));

        // when / then
        assertThatThrownBy(() -> sut.getById(99L)).isInstanceOf(NotFoundException.class);
    }

    /**
     * Org-explicit variant of {@link #getById}, for a caller with no HTTP request to read an organization from - the
     * communication sweep's producers, which run inside {@code OrganizationScope.runAs} and carry no JWT. Returns the
     * three columns already on the row it loads, and still must not touch {@code membershipLedger} or
     * {@code communityKindStore} the way {@code getById} does: the member count and the kind label each cost a query of
     * their own, and no producer needs either.
     */
    @Test
    void identityById_orgExplicit_returnsTheRowsOwnColumnsAndQueriesNothingElse() {

        // given
        var entity = CommunityRecordMother.any().id(7L).slug("backend-guild").name("Backend Guild")
                .accentColor("#1aa6a0").build();
        when(communities.findByIdOrThrow("acme", 7L)).thenReturn(entity);

        // when / then
        assertThat(sut.identityById("acme", 7L))
                .isEqualTo(new CommunityIdentity("backend-guild", "Backend Guild", "#1aa6a0"));
        verifyNoInteractions(membershipLedger, communityKindStore);
    }

    @Test
    void identityById_carriesANullAccentThroughRatherThanDefaultingIt() {

        // given: choosing an accent is optional, and the fallback hue belongs to the renderer, not to this read.
        var entity = CommunityRecordMother.any().id(7L).slug("backend-guild").name("Backend Guild").accentColor(null)
                .build();
        when(communities.findByIdOrThrow("acme", 7L)).thenReturn(entity);

        // when / then
        assertThat(sut.identityById("acme", 7L).accentColor()).isNull();
    }

    /**
     * The read the link-target callers use: an evidence item, a saved item or a recognized contribution holds a
     * community reference that may have outlived the community. A miss is therefore absent from the map, never a
     * {@code NotFoundException} - one thrown out of a joined transaction marks it rollback-only whatever the caller
     * catches, and that is what used to turn a removed community into a 500 on an unrelated read.
     */
    @Test
    void identitiesByIds_keysTheRowsItFoundAndOmitsTheIdsItDidNot() {

        // given
        var found = CommunityRecordMother.any().id(7L).slug("backend-guild").name("Backend Guild")
                .accentColor("#1aa6a0").build();
        when(communityStore.findByIds("default", List.of(7L, 9L))).thenReturn(List.of(found));

        // when / then
        assertThat(sut.identitiesByIds(List.of(7L, 9L)))
                .containsExactly(entry(7L, new CommunityIdentity("backend-guild", "Backend Guild", "#1aa6a0")));
    }

    @Test
    void identitiesByIds_emptyInput_queriesNothing() {

        // when / then
        assertThat(sut.identitiesByIds(List.of())).isEmpty();
        verifyNoInteractions(communityStore);
    }

    @Test
    void directory() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        var pageable = PageRequest.of(0, 10);
        when(communityStore.searchByName("default", CommunityStatus.ACTIVE, "guild", pageable))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
        when(membershipLedger.count("default", 1L)).thenReturn(5L);

        // when
        var actual = sut.directory("guild", pageable);

        // then
        assertThat(actual.getContent()).hasSize(1);
        assertThat(actual.getContent().getFirst().memberCount()).isEqualTo(5L);
        assertThat(actual.getContent().getFirst().kind()).isEqualTo("Guild");
    }

    @Test
    void listing_carries_the_callers_role() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(membershipLedger.findAllOf("default", "dev-user")).thenReturn(
                List.of(MembershipRecordMother.any().communityId(1L).role(CommunityRole.ORGANIZER).build()));
        var joined = CommunityRecordMother.any().id(1L).build();
        var other = CommunityRecordMother.any().id(2L).slug("other").build();
        var pageable = PageRequest.of(0, 10);
        when(communityStore.search(any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(joined, other), pageable, 2));

        // when
        var actual = sut.listing("guild", null, pageable);

        // then
        assertThat(actual.getContent()).extracting(CommunityListing::role).containsExactly(CommunityRole.ORGANIZER,
                null);
        assertThat(actual.getContent().getFirst().community().kind()).isEqualTo("Guild");
    }

    @Test
    void listing_searches_with_the_filter_and_the_callers_communities() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(membershipLedger.findAllOf("default", "dev-user"))
                .thenReturn(List.of(MembershipRecordMother.any().communityId(7L).role(CommunityRole.MEMBER).build()));
        var pageable = PageRequest.of(0, 10);
        when(communityStore.search(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        sut.listing(null, MembershipFilter.EXPLORE, pageable);

        // then
        verify(communityStore).search(CommunitySearch.builder().organizationId("default").status(CommunityStatus.ACTIVE)
                .name("").membership(MembershipFilter.EXPLORE).memberOf(Set.of(7L)).build(), pageable);
    }

    @Test
    void directory_nullQueryTreatedAsEmpty() {

        // given
        var pageable = PageRequest.of(0, 10);
        when(communityStore.searchByName("default", CommunityStatus.ACTIVE, "", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        var actual = sut.directory(null, pageable);

        // then
        assertThat(actual.getContent()).isEmpty();
    }

    @Test
    void update() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipLedger.count("default", 1L)).thenReturn(2L);

        // when
        var actual = sut.update("backend-guild", new UpdateCommunityRequest("Renamed", 1L, "new"));

        // then
        assertThat(actual.name()).isEqualTo("Renamed");
        assertThat(actual.slug()).isEqualTo("backend-guild");
        assertThat(actual.kind()).isEqualTo("Guild");
        verify(membershipAuthorizer).requireOrganizerOrOpenMember("default", 1L, "dev-user", false);
    }

    @Test
    void update_nameOrDescriptionChange_publishesCommunityDetailsChanged() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipLedger.count("default", 1L)).thenReturn(2L);

        // when
        sut.update("backend-guild", new UpdateCommunityRequest("Renamed", 1L, "new"));

        // then
        verify(eventPublisher).publishEvent(new CommunityDetailsChanged("default", 1L, "Renamed", "new"));
    }

    @Test
    void update_descriptionOnlyChange_stillPublishesCommunityDetailsChanged() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipLedger.count("default", 1L)).thenReturn(2L);

        // when - the name stays as persisted, only the description moves
        sut.update("backend-guild", new UpdateCommunityRequest("Backend Guild", 1L, "sharper pitch"));

        // then
        verify(eventPublisher)
                .publishEvent(new CommunityDetailsChanged("default", 1L, "Backend Guild", "sharper pitch"));
    }

    @Test
    void update_unchangedNameAndDescription_publishesNoEvent() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipLedger.count("default", 1L)).thenReturn(2L);

        // when - same name and description as the persisted entity, only the kind is touched
        sut.update("backend-guild", new UpdateCommunityRequest("Backend Guild", 1L, "A guild for backend engineers"));

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void update_notFound() {

        // given
        when(communities.findBySlugOrThrow("default", "missing"))
                .thenThrow(new NotFoundException("Community not found: missing"));

        // when / then
        assertThatThrownBy(() -> sut.update("missing", new UpdateCommunityRequest("x", 1L, "z")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_archivedIsRejected() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).status(CommunityStatus.ARCHIVED).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when / then
        assertThatThrownBy(() -> sut.update("backend-guild", new UpdateCommunityRequest("x", 1L, "z")))
                .isInstanceOf(ConflictException.class);
        verify(communityStore, never()).save(any());
    }

    @Test
    void update_unknownKindRejected() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityKindStore.find("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.update("backend-guild", new UpdateCommunityRequest("Renamed", 99L, "x")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void archive() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipLedger.count("default", 1L)).thenReturn(2L);

        // when
        var actual = sut.archive("backend-guild");

        // then
        assertThat(actual.status()).isEqualTo(CommunityStatus.ARCHIVED);
        assertThat(actual.kind()).isEqualTo("Guild");
    }

    @Test
    void archive_refusedWhereTheCommunitiesAreFixedAtFounding() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        countFixedHere = true;

        // when / then
        assertThatThrownBy(() -> sut.archive("backend-guild")).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("COMMUNITY_COUNT_FIXED");
        verify(communityStore, never()).save(any());
    }

    @Test
    void archive_alreadyArchived() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).status(CommunityStatus.ARCHIVED).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when / then
        assertThatThrownBy(() -> sut.archive("backend-guild")).isInstanceOf(ConflictException.class);
    }

    @Test
    void archive_notFound() {

        // given
        when(communities.findBySlugOrThrow("default", "missing"))
                .thenThrow(new NotFoundException("Community not found: missing"));

        // when / then
        assertThatThrownBy(() -> sut.archive("missing")).isInstanceOf(NotFoundException.class);
    }
}

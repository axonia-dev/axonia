package dev.axonia.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.IdpProvider;
import dev.axonia.identity.OrganizationIdp;
import dev.axonia.identity.OrganizationIdpResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class DirectoryGroupsAdapterTest {

    private static final String ORGANIZATION = "default";
    private static final String USER_ID = "user-sub-1";
    private static final String SCIM_ID = "scim-user-1";
    private static final Instant CONFIRMED = Instant.parse("2026-01-05T08:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private DirectoryGroupRepository directoryGroupRepository;

    @Mock
    private OrganizationIdpResolver organizationIdpResolver;

    @Mock
    private DirectoryGroupMemberRepository directoryGroupMemberRepository;

    @InjectMocks
    private DirectoryGroupsAdapter sut;

    // -------------------------------------------------------------------------
    // reconciliationKeysForUser
    // -------------------------------------------------------------------------

    @Test
    void reconciliationKeysForUser_userMappedToTwoGroups_returnsBothReconciliationKeys() {

        // given
        givenUserBelongsTo(confirmedGroup(1L, "engineers"), confirmedGroup(2L, "leads"));

        // when
        var actual = sut.reconciliationKeysForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).containsExactlyInAnyOrder("engineers", "leads");
    }

    @Test
    void reconciliationKeysForUser_personWhoArrivedAtFirstSignIn_stillResolvesTheGroupsTheyWereLinkedInto() {

        // given - arrival is a historical fact, not a current-state predicate. Every user carries a scim_id now, so a
        // first-sign-in person is discoverable over SCIM and Entra links them into synced groups afterwards.
        givenUserBelongsTo(UserArrival.FIRST_SIGN_IN, confirmedGroup(1L, "engineers"));

        // when
        var actual = sut.reconciliationKeysForUser(ORGANIZATION, USER_ID);

        // then - gating on arrival here would deny them every role bound to that group
        assertThat(actual).containsExactly("engineers");
    }

    @Test
    void reconciliationKeysForUser_userInNoDirectoryGroup_returnsEmpty() {

        // given - no membership row, which is the only thing that makes the answer empty
        givenUserBelongsTo();

        // when
        var actual = sut.reconciliationKeysForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void reconciliationKeysForUser_userNotFound_returnsEmpty() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORGANIZATION, USER_ID)).thenReturn(Optional.empty());

        // when
        var actual = sut.reconciliationKeysForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void reconciliationKeysForUser_nullUserId_returnsEmptyWithoutQuerying() {

        // given - a SCIM-provisioned user that never logged in has a null sub; querying by it would match
        // every sub-less row (user_id IS NULL), so the adapter must short-circuit, never query.

        // when
        var actual = sut.reconciliationKeysForUser(ORGANIZATION, null);

        // then
        assertThat(actual).isEmpty();
        verify(userRepository, never()).findByOrganizationIdAndUserId(any(), any());
    }

    @Test
    void reconciliationKeysForUser_groupWithNullKey_isExcluded() {

        // given
        givenUserBelongsTo(confirmedGroup(1L, null));

        // when
        var actual = sut.reconciliationKeysForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).isEmpty();
    }

    /** Unconfirmed means unconfirmed: the caller's own permission check does not see it. */
    @Test
    void anUnconfirmedGroupConfersNothing() {

        // given - a group SCIM pushed under a key a role binding was already waiting on, held until somebody with
        // role:assign says it is the group they meant
        givenUserBelongsTo(DirectoryGroupEntity.builder().id(1L).organizationId(ORGANIZATION)
                .reconciliationKey("platform-admins").confirmedAt(null).build());

        // when
        var actual = sut.reconciliationKeysForUser(ORGANIZATION, USER_ID);

        // then - returning the key here would grant the waiting binding's role before an administrator confirms it
        assertThat(actual).isEmpty();
    }

    @Test
    void reconciliationKeysForUser_organizationOnAnyProvider_stillAnswersForDisplayPurposes() {

        // given - the display-oriented port stays provider-agnostic; only the claim substitute is Entra-gated
        givenUserBelongsTo(confirmedGroup(1L, "engineers"));

        // when
        var actual = sut.reconciliationKeysForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).containsExactly("engineers");
        verifyNoInteractions(organizationIdpResolver);
    }

    // -------------------------------------------------------------------------
    // groupDisplayNamesForUser
    // -------------------------------------------------------------------------

    @Test
    void groupDisplayNamesForUser_userInTwoGroups_returnsBothDisplayNames() {

        // given
        givenUserBelongsTo(
                DirectoryGroupEntity.builder().id(1L).organizationId(ORGANIZATION).displayName("Engineering").build(),
                DirectoryGroupEntity.builder().id(2L).organizationId(ORGANIZATION).displayName("Platform").build());

        // when
        var actual = sut.groupDisplayNamesForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).containsExactlyInAnyOrder("Engineering", "Platform");
    }

    @Test
    void groupDisplayNamesForUser_personWhoArrivedAtFirstSignIn_stillResolvesTheGroupsTheyWereLinkedInto() {

        // given
        givenUserBelongsTo(UserArrival.FIRST_SIGN_IN,
                DirectoryGroupEntity.builder().id(1L).organizationId(ORGANIZATION).displayName("Engineering").build());

        // when
        var actual = sut.groupDisplayNamesForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).containsExactly("Engineering");
    }

    @Test
    void groupDisplayNamesForUser_unconfirmedGroup_isStillNamed() {

        // given - a held group grants nothing, but a display name grants nothing either
        givenUserBelongsTo(DirectoryGroupEntity.builder().id(1L).organizationId(ORGANIZATION)
                .displayName("Platform Admins").confirmedAt(null).build());

        // when
        var actual = sut.groupDisplayNamesForUser(ORGANIZATION, USER_ID);

        // then - hiding it would make the group invisible to the administrator who has to decide about it
        assertThat(actual).containsExactly("Platform Admins");
    }

    @Test
    void groupDisplayNamesForUser_userNotFound_returnsEmpty() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORGANIZATION, USER_ID)).thenReturn(Optional.empty());

        // when
        var actual = sut.groupDisplayNamesForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void groupDisplayNamesForUser_nullUserId_returnsEmptyWithoutQuerying() {

        // when
        var actual = sut.groupDisplayNamesForUser(ORGANIZATION, null);

        // then
        assertThat(actual).isEmpty();
        verify(userRepository, never()).findByOrganizationIdAndUserId(any(), any());
    }

    @Test
    void groupDisplayNamesForUser_groupWithNullName_isExcluded() {

        // given
        givenUserBelongsTo(
                DirectoryGroupEntity.builder().id(1L).organizationId(ORGANIZATION).displayName(null).build());

        // when
        var actual = sut.groupDisplayNamesForUser(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).isEmpty();
    }

    // -------------------------------------------------------------------------
    // reconciliationKeysSubstitutingForAbsentClaim
    // -------------------------------------------------------------------------

    @Test
    void reconciliationKeysSubstitutingForAbsentClaim_entraConnectedOrganization_returnsTheDirectoryKeys() {

        // given
        when(organizationIdpResolver.resolve(ORGANIZATION))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-1", true)));
        givenUserBelongsTo(confirmedGroup(1L, "group-guid-1"));

        // when
        var actual = sut.reconciliationKeysSubstitutingForAbsentClaim(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).containsExactly("group-guid-1");
    }

    @Test
    void reconciliationKeysSubstitutingForAbsentClaim_organizationWithNoDirectoryToRead_returnsEmptyWithoutReadingIt() {

        // given - a provider with no directory to read has no claim overage, so an empty claim means no groups
        when(organizationIdpResolver.resolve(ORGANIZATION))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.GENERIC_OIDC, null, false)));

        // when
        var actual = sut.reconciliationKeysSubstitutingForAbsentClaim(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).isEmpty();
        verifyNoInteractions(userRepository, directoryGroupRepository);
    }

    @Test
    void reconciliationKeysSubstitutingForAbsentClaim_organizationWithoutSsoConnection_returnsEmptyWithoutReadingTheDirectory() {

        // given - a hub Organization has no SSO connection at all
        when(organizationIdpResolver.resolve(ORGANIZATION)).thenReturn(Optional.empty());

        // when
        var actual = sut.reconciliationKeysSubstitutingForAbsentClaim(ORGANIZATION, USER_ID);

        // then
        assertThat(actual).isEmpty();
        verifyNoInteractions(userRepository, directoryGroupRepository);
    }

    // -------------------------------------------------------------------------
    // userIdsInGroups
    // -------------------------------------------------------------------------

    @Test
    void userIdsInGroups_confirmedGroup_returnsItsSignedInMembers() {

        // given
        when(directoryGroupRepository.findByOrganizationIdAndReconciliationKeyIn(ORGANIZATION,
                List.of("platform-admins"))).thenReturn(List.of(confirmedGroup(1L, "platform-admins")));
        when(directoryGroupMemberRepository.findUserIdsByOrganizationIdAndGroupIdIn(ORGANIZATION, List.of(1L)))
                .thenReturn(List.of(USER_ID));

        // when
        var actual = sut.userIdsInGroups(ORGANIZATION, List.of("platform-admins"));

        // then
        assertThat(actual).containsExactly(USER_ID);
    }

    /**
     * Unconfirmed means unconfirmed here too: a key a role binding was already waiting on when the directory pushed it
     * must not hand that role's permission to anybody, exactly the rule {@link #anUnconfirmedGroupConfersNothing}
     * already proves for {@link DirectoryGroupsAdapter#reconciliationKeysForUser}.
     */
    @Test
    void userIdsInGroups_unconfirmedGroup_contributesNobody() {

        // given
        when(directoryGroupRepository.findByOrganizationIdAndReconciliationKeyIn(ORGANIZATION,
                List.of("platform-admins")))
                .thenReturn(List.of(DirectoryGroupEntity.builder().id(1L).organizationId(ORGANIZATION)
                        .reconciliationKey("platform-admins").confirmedAt(null).build()));

        // when
        var actual = sut.userIdsInGroups(ORGANIZATION, List.of("platform-admins"));

        // then - the member lookup is never even reached
        assertThat(actual).isEmpty();
        verifyNoInteractions(directoryGroupMemberRepository);
    }

    @Test
    void userIdsInGroups_emptyReconciliationKeys_returnsEmptyWithoutQuerying() {

        // when
        var actual = sut.userIdsInGroups(ORGANIZATION, List.of());

        // then - IN () is invalid SQL, so this must short-circuit before either repository is asked
        assertThat(actual).isEmpty();
        verifyNoInteractions(directoryGroupRepository, directoryGroupMemberRepository);
    }

    /** A directory group an administrator has vouched for, which every group is unless it was held. */
    private static DirectoryGroupEntity confirmedGroup(Long id, String reconciliationKey) {

        return DirectoryGroupEntity.builder().id(id).organizationId(ORGANIZATION).reconciliationKey(reconciliationKey)
                .confirmedAt(CONFIRMED).build();
    }

    private void givenUserBelongsTo(DirectoryGroupEntity... groups) {

        givenUserBelongsTo(UserArrival.SYNCED, groups);
    }

    private void givenUserBelongsTo(UserArrival arrival, DirectoryGroupEntity... groups) {

        var user = UserEntity.builder().userId(USER_ID).scimId(SCIM_ID).arrival(arrival).organizationId(ORGANIZATION)
                .build();
        when(userRepository.findByOrganizationIdAndUserId(ORGANIZATION, USER_ID)).thenReturn(Optional.of(user));
        when(directoryGroupRepository.findByOrganizationIdAndMemberScimId(ORGANIZATION, SCIM_ID))
                .thenReturn(List.of(groups));
    }
}

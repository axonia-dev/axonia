package dev.axonia.identity;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.internal.DirectoryGroupEntity;
import dev.axonia.identity.internal.DirectoryGroupMemberEntity;
import dev.axonia.identity.internal.DirectoryGroupMemberRepository;
import dev.axonia.identity.internal.DirectoryGroupRepository;
import dev.axonia.identity.internal.UserArrival;
import dev.axonia.identity.internal.UserRepository;
import dev.axonia.identity.mother.UserEntityMother;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryGroupRepositoryTest extends CoreJdbcTestSupport {

    private static final Instant CONFIRMED = Instant.parse("2026-03-04T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-28T08:00:00Z");

    @Autowired
    private DirectoryGroupRepository groupRepository;

    @Autowired
    private DirectoryGroupMemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindByScimId_rlsRoundTrip() {

        // given
        var group = DirectoryGroupEntity.builder().organizationId("default").scimId("sg-1").displayName("Engineers")
                .reconciliationKey("Engineers").build();

        groupRepository.save(group);

        // when
        var found = groupRepository.findByOrganizationIdAndScimId("default", "sg-1");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Engineers");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getVersion()).isNotNull();
    }

    @Test
    void findByReconciliationKey() {

        // given
        var group = DirectoryGroupEntity.builder().organizationId("default").scimId("sg-2").displayName("Ops")
                .reconciliationKey("rec-key-ops").build();

        groupRepository.save(group);

        // when
        var found = groupRepository.findByOrganizationIdAndReconciliationKey("default", "rec-key-ops");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getScimId()).isEqualTo("sg-2");
    }

    @Test
    void rekeyToExternalId_rewritesKeyedRowsAndSkipsRowsWithoutAnExternalId() {

        // given - the mixed state an Organization lands in when it SCIM-provisions before connecting Entra
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-1")
                .externalId("obj-id-1").displayName("Engineers").reconciliationKey("Engineers").build());
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-2").externalId(null)
                .displayName("Ops").reconciliationKey("Ops").build());

        // when
        var actual = groupRepository.rekeyToExternalId("default");

        // then - the row carrying an object id is realigned; the one without keeps a usable key rather than a null one
        assertThat(actual).isEqualTo(1);
        assertThat(groupRepository.findByOrganizationIdAndScimId("default", "sg-rk-1").orElseThrow()
                .getReconciliationKey()).isEqualTo("obj-id-1");
        assertThat(groupRepository.findByOrganizationIdAndScimId("default", "sg-rk-2").orElseThrow()
                .getReconciliationKey()).isEqualTo("Ops");
    }

    @Test
    void rekeyToDisplayName_rewritesKeysBackToTheNonEntraConvention() {

        // given
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-3")
                .externalId("obj-id-3").displayName("Platform").reconciliationKey("obj-id-3").build());

        // when
        var actual = groupRepository.rekeyToDisplayName("default");

        // then
        assertThat(actual).isEqualTo(1);
        assertThat(groupRepository.findByOrganizationIdAndScimId("default", "sg-rk-3").orElseThrow()
                .getReconciliationKey()).isEqualTo("Platform");
    }

    @Test
    void rekeyToExternalId_leavesConfirmationsAloneAndSurfacesTheRowsItWillMove() {

        // given - a confirmed group the switch will move, and one already carrying the right key
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-c1")
                .externalId("platform-admins").displayName("Foo").reconciliationKey("Foo").confirmedAt(CONFIRMED)
                .build());
        groupRepository.save(
                DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-c2").externalId("obj-id-c2")
                        .displayName("Settled").reconciliationKey("obj-id-c2").confirmedAt(CONFIRMED).build());

        // when - the finder the rekeyer asks about is the rewrite's own WHERE clause plus confirmed_at IS NOT NULL
        var movers = groupRepository.findConfirmedGroupsRekeyingToExternalId("default");
        groupRepository.rekeyToExternalId("default");

        // then - only the row that actually moves is offered for the claim check
        assertThat(movers).extracting(DirectoryGroupEntity::getScimId).containsExactly("sg-rk-c1");
        // and the rewrite itself never touches a confirmation: holding is the rekeyer's decision, per claimed key
        assertThat(groupRepository.findByOrganizationIdAndScimId("default", "sg-rk-c1").orElseThrow().getConfirmedAt())
                .isEqualTo(CONFIRMED);
        assertThat(groupRepository.findByOrganizationIdAndScimId("default", "sg-rk-c2").orElseThrow().getConfirmedAt())
                .isEqualTo(CONFIRMED);
    }

    @Test
    void findConfirmedGroupsRekeyingToDisplayName_offersOnlyConfirmedMovers() {

        // given - a confirmed mover, a held mover (nothing to take back), and a row already on its key
        groupRepository.save(
                DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-c3").externalId("obj-id-c3")
                        .displayName("platform-admins").reconciliationKey("obj-id-c3").confirmedAt(CONFIRMED).build());
        groupRepository.save(
                DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-c4").externalId("obj-id-c4")
                        .displayName("Already Held").reconciliationKey("obj-id-c4").confirmedAt(null).build());
        groupRepository.save(
                DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-c5").externalId("obj-id-c5")
                        .displayName("Settled").reconciliationKey("Settled").confirmedAt(CONFIRMED).build());

        // when
        var movers = groupRepository.findConfirmedGroupsRekeyingToDisplayName("default");

        // then
        assertThat(movers).extracting(DirectoryGroupEntity::getScimId).containsExactly("sg-rk-c3");
    }

    @Test
    void holdByIds_takesTheConfirmationBackOffOnlyTheNamedGroups() {

        // given
        var held = groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-hold-1")
                .displayName("Platform Admins").reconciliationKey("platform-admins").confirmedAt(CONFIRMED).build());
        var spared = groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-hold-2")
                .displayName("Marketing").reconciliationKey("marketing").confirmedAt(CONFIRMED).build());

        // when
        var actual = groupRepository.holdByIds("default", List.of(held.getId()), UPDATED_AT);

        // then
        assertThat(actual).isEqualTo(1);
        assertThat(groupRepository.findByOrganizationIdAndScimId("default", "sg-hold-1").orElseThrow().getConfirmedAt())
                .isNull();
        assertThat(groupRepository.findByOrganizationIdAndScimId("default", "sg-hold-2").orElseThrow().getConfirmedAt())
                .isEqualTo(CONFIRMED);
        // updated_at comes from the caller's Clock, never from SQL now()
        assertThat(groupRepository.findByOrganizationIdAndScimId("default", "sg-hold-1").orElseThrow().getUpdatedAt())
                .isEqualTo(UPDATED_AT);
        assertThat(spared.getId()).isNotNull();
    }

    @Test
    void findByConfirmedAtIsNull_returnsOnlyTheHeldGroups() {

        // given
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-held")
                .displayName("Held").reconciliationKey("held-key").confirmedAt(null).build());
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-live")
                .displayName("Live").reconciliationKey("live-key").confirmedAt(CONFIRMED).build());

        // when
        var actual = groupRepository.findByOrganizationIdAndConfirmedAtIsNull("default");

        // then
        assertThat(actual).extracting(DirectoryGroupEntity::getReconciliationKey).containsExactly("held-key");
    }

    @Test
    void countGroupsWithoutExternalId_countsOnlyUnkeyableRows() {

        // given
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-4")
                .externalId("obj-id-4").displayName("With Id").reconciliationKey("With Id").build());
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rk-5").externalId(null)
                .displayName("Without Id").reconciliationKey("Without Id").build());

        // when
        var actual = groupRepository.countGroupsWithoutExternalId("default");

        // then
        assertThat(actual).isEqualTo(1);
    }

    @Test
    void saveMemberAndFindByGroupId() {

        // given
        var group = DirectoryGroupEntity.builder().organizationId("default").scimId("sg-3").displayName("Platform")
                .reconciliationKey("Platform").build();
        var savedGroup = groupRepository.save(group);

        var member = DirectoryGroupMemberEntity.builder().organizationId("default").groupId(savedGroup.getId())
                .userScimId("user-scim-1").build();
        memberRepository.save(member);

        // when
        var members = memberRepository.findByOrganizationIdAndGroupId("default", savedGroup.getId());

        // then
        assertThat(members).hasSize(1);
        assertThat(members.getFirst().getUserScimId()).isEqualTo("user-scim-1");
    }

    @Test
    void deleteMemberByCompositeKey() {

        // given
        var group = DirectoryGroupEntity.builder().organizationId("default").scimId("sg-4").displayName("Frontend")
                .reconciliationKey("Frontend").build();
        var savedGroup = groupRepository.save(group);

        var member = DirectoryGroupMemberEntity.builder().organizationId("default").groupId(savedGroup.getId())
                .userScimId("user-scim-2").build();
        memberRepository.save(member);

        // when
        memberRepository.deleteByOrganizationIdAndGroupIdAndUserScimId("default", savedGroup.getId(), "user-scim-2");

        // then
        var members = memberRepository.findByOrganizationIdAndGroupId("default", savedGroup.getId());
        assertThat(members).isEmpty();
    }

    @Test
    void findByOrganizationId_pagination() {

        // given
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-p1")
                .displayName("Group A").reconciliationKey("Group A").build());
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-p2")
                .displayName("Group B").reconciliationKey("Group B").build());

        // when
        var all = groupRepository.findByOrganizationId("default", PageRequest.of(0, 100));

        // then
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void findByOrganizationIdAndScimId_unknownScimId_returnsEmpty() {

        // when
        var found = groupRepository.findByOrganizationIdAndScimId("default", "no-such-group");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void findByOrganizationIdAndMemberScimId_returnsEveryGroupTheUserBelongsTo_inOneQuery() {

        // given
        var engineers = groupRepository.save(DirectoryGroupEntity.builder().organizationId("default")
                .scimId("sg-join-1").displayName("Engineers").reconciliationKey("obj-eng").build());
        var leads = groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-join-2")
                .displayName("Leads").reconciliationKey("obj-leads").build());
        var unrelated = groupRepository.save(DirectoryGroupEntity.builder().organizationId("default")
                .scimId("sg-join-3").displayName("Unrelated").reconciliationKey("obj-unrelated").build());
        memberRepository.save(DirectoryGroupMemberEntity.builder().organizationId("default").groupId(engineers.getId())
                .userScimId("user-join-1").build());
        memberRepository.save(DirectoryGroupMemberEntity.builder().organizationId("default").groupId(leads.getId())
                .userScimId("user-join-1").build());
        memberRepository.save(DirectoryGroupMemberEntity.builder().organizationId("default").groupId(unrelated.getId())
                .userScimId("user-join-2").build());

        // when
        var actual = groupRepository.findByOrganizationIdAndMemberScimId("default", "user-join-1");

        // then
        assertThat(actual).extracting(DirectoryGroupEntity::getReconciliationKey).containsExactlyInAnyOrder("obj-eng",
                "obj-leads");
        assertThat(actual).extracting(DirectoryGroupEntity::getDisplayName).containsExactlyInAnyOrder("Engineers",
                "Leads");
    }

    @Test
    void findByOrganizationIdAndMemberScimId_userWithNoMemberships_returnsEmpty() {

        // when
        var actual = groupRepository.findByOrganizationIdAndMemberScimId("default", "user-with-no-groups");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void insertIgnoreDuplicate_duplicateMemberLeavesExactlyOneRow() {

        // given
        var group = DirectoryGroupEntity.builder().organizationId("default").scimId("sg-idem").displayName("Idempotent")
                .reconciliationKey("Idempotent").build();
        var savedGroup = groupRepository.save(group);

        // when
        memberRepository.insertIgnoreDuplicate("default", savedGroup.getId(), "user-idem-1");
        // second call with same (organization, group, user) must not throw and must not add a second row
        memberRepository.insertIgnoreDuplicate("default", savedGroup.getId(), "user-idem-1");

        // then
        var members = memberRepository.findByOrganizationIdAndGroupId("default", savedGroup.getId());
        assertThat(members).hasSize(1);
        assertThat(members.getFirst().getUserScimId()).isEqualTo("user-idem-1");
    }

    @Test
    void findByOrganizationIdAndReconciliationKeyIn_returnsGroupsMatchingAnyGivenKey() {

        // given
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rkin-1")
                .displayName("Admins").reconciliationKey("admins-rkin").build());
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rkin-2")
                .displayName("Ops").reconciliationKey("ops-rkin").build());
        groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-rkin-3")
                .displayName("Unrelated").reconciliationKey("unrelated-rkin").build());

        // when
        var actual = groupRepository.findByOrganizationIdAndReconciliationKeyIn("default",
                List.of("admins-rkin", "ops-rkin"));

        // then
        assertThat(actual).extracting(DirectoryGroupEntity::getReconciliationKey)
                .containsExactlyInAnyOrder("admins-rkin", "ops-rkin");
    }

    /**
     * {@link DirectoryGroupMemberRepository#findUserIdsByOrganizationIdAndGroupIdIn} joins on
     * {@code identity_user.user_id}, so a SCIM-provisioned member who has never signed in - {@code user_id IS NULL} -
     * must be excluded rather than returned as a null recipient. This is the same rule
     * {@link UserRepository#findPresentUserIds} carries its own dedicated proof for.
     */
    @Test
    void findUserIdsByOrganizationIdAndGroupIdIn_excludesAMemberWhoHasNeverSignedIn() {

        // given
        var group = groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-uids-1")
                .displayName("Admins").reconciliationKey("admins-uids").confirmedAt(CONFIRMED).build());
        userRepository.save(UserEntityMother.any().organizationId("default").userId("user-signed-in")
                .scimId("scim-signed-in").arrival(UserArrival.SYNCED).active(true).build());
        userRepository.save(UserEntityMother.any().organizationId("default").userId(null).scimId("scim-never-signed-in")
                .arrival(UserArrival.SYNCED).active(true).build());
        memberRepository.save(DirectoryGroupMemberEntity.builder().organizationId("default").groupId(group.getId())
                .userScimId("scim-signed-in").build());
        memberRepository.save(DirectoryGroupMemberEntity.builder().organizationId("default").groupId(group.getId())
                .userScimId("scim-never-signed-in").build());

        // when
        var actual = memberRepository.findUserIdsByOrganizationIdAndGroupIdIn("default", List.of(group.getId()));

        // then - the signed-in member is named, the sub-less one is not
        assertThat(actual).containsExactly("user-signed-in");
    }

    /**
     * The Testcontainers connection is the table owner, and ownership bypasses row-level security, so this cannot prove
     * isolation end to end. It holds the explicit {@code organization_id} predicate the query's {@code WHERE} clause
     * applies to the member row, which is the half that is testable here.
     */
    @Test
    void findUserIdsByOrganizationIdAndGroupIdIn_doesNotReachIntoAnotherOrganization() {

        // given
        var group = groupRepository.save(DirectoryGroupEntity.builder().organizationId("default").scimId("sg-uids-2")
                .displayName("Ops").reconciliationKey("ops-uids").confirmedAt(CONFIRMED).build());
        userRepository.save(UserEntityMother.any().organizationId("default").userId("user-cross-org")
                .scimId("scim-cross-org").arrival(UserArrival.SYNCED).active(true).build());
        memberRepository.save(DirectoryGroupMemberEntity.builder().organizationId("default").groupId(group.getId())
                .userScimId("scim-cross-org").build());

        // when - the same group id, asked about under a different organization
        var actual = memberRepository.findUserIdsByOrganizationIdAndGroupIdIn("other-org", List.of(group.getId()));

        // then
        assertThat(actual).isEmpty();
    }
}

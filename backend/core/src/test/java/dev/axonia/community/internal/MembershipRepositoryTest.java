package dev.axonia.community.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import dev.axonia.AssertjAssertions;
import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.access.RoleBindingService;
import dev.axonia.community.CommunityRole;
import dev.axonia.community.CommunityStatus;
import dev.axonia.community.CurrentUser;
import dev.axonia.community.MemberCommunity;
import dev.axonia.community.MemberDirectory;
import dev.axonia.community.Membership;
import dev.axonia.community.MembershipService;
import dev.axonia.community.OrganizationContext;
import dev.axonia.community.mother.CommunityEntityMother;
import dev.axonia.community.mother.CommunityKindEntityMother;
import dev.axonia.community.mother.MembershipEntityMother;
import dev.axonia.identity.UserDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MembershipRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityKindRepository communityKindRepository;

    private Long kindId;

    @BeforeEach
    void seedKind() {

        kindId = communityKindRepository.save(CommunityKindEntityMother.any().build()).getId();
    }

    private Long communityId() {

        return communityRepository.save(CommunityEntityMother.any().kindId(kindId).build()).getId();
    }

    @Test
    void save() {

        // given
        var membership = MembershipEntityMother.any().communityId(communityId()).build();

        // when
        var actual = membershipRepository.save(membership);

        // then
        AssertjAssertions.assertEntityAuditingOnSave(actual);
        assertThat(actual.getRole()).isEqualTo(CommunityRole.MEMBER);
    }

    @Test
    void findByOrganizationIdAndCommunityIdAndUserId() {

        // given
        var id = communityId();
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("alice").build());

        // when
        var actual = membershipRepository.findByOrganizationIdAndCommunityIdAndUserId("default", id, "alice");

        // then
        assertThat(actual).isPresent();
    }

    @Test
    void findByOrganizationIdAndUserId() {

        // given
        var id = communityId();
        var otherCommunity = communityRepository
                .save(CommunityEntityMother.any().slug("frontend-guild").name("Frontend Guild").kindId(kindId).build());
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("alice").build());
        membershipRepository
                .save(MembershipEntityMother.any().communityId(otherCommunity.getId()).userId("bob").build());

        // when
        var actual = membershipRepository.findByOrganizationIdAndUserId("default", "alice");

        // then
        assertThat(actual).extracting(MembershipEntity::getCommunityId).containsExactly(id);
    }

    @Test
    void findActiveCommunitiesOf_leavesOutArchivedCommunitiesAndOtherOrganizations() {

        // given
        var platform = community("default", "platform", "Platform", CommunityStatus.ACTIVE);
        var apis = community("default", "apis", "apis", CommunityStatus.ACTIVE);
        var legacy = community("default", "legacy", "Legacy", CommunityStatus.ARCHIVED);
        var otherKind = communityKindRepository.save(CommunityKindEntityMother.any().organizationId("other").build());
        var theirs = communityRepository.save(CommunityEntityMother.any().organizationId("other").slug("theirs")
                .name("Theirs").kindId(otherKind.getId()).build());
        for (var id : List.of(platform, apis, legacy)) {
            membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("alice").build());
        }
        membershipRepository.save(MembershipEntityMother.any().organizationId("other").communityId(theirs.getId())
                .userId("alice").build());
        membershipRepository.save(MembershipEntityMother.any().communityId(theirs.getId()).userId("alice").build());
        membershipRepository.save(MembershipEntityMother.any().communityId(apis).userId("bob").build());

        // when
        var actual = membershipRepository.findActiveCommunitiesOf("default", "alice");

        // then
        assertThat(actual).containsExactly(new MemberCommunity(apis, "apis", "apis"),
                new MemberCommunity(platform, "platform", "Platform"));
    }

    private Long community(String organizationId, String slug, String name, CommunityStatus status) {

        return communityRepository.save(CommunityEntityMother.any().organizationId(organizationId).slug(slug).name(name)
                .status(status).kindId(kindId).build()).getId();
    }

    @Test
    void countByOrganizationIdAndCommunityId() {

        // given
        var id = communityId();
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("alice").build());
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("bob").build());

        // when
        var actual = membershipRepository.countByOrganizationIdAndCommunityId("default", id);

        // then
        assertThat(actual).isEqualTo(2);
    }

    @Test
    void countByOrganizationIdAndCommunityIdAndRole() {

        // given
        var id = communityId();
        membershipRepository.save(
                MembershipEntityMother.any().communityId(id).userId("alice").role(CommunityRole.ORGANIZER).build());
        membershipRepository
                .save(MembershipEntityMother.any().communityId(id).userId("bob").role(CommunityRole.MEMBER).build());

        // when
        var actual = membershipRepository.countByOrganizationIdAndCommunityIdAndRole("default", id,
                CommunityRole.ORGANIZER);

        // then
        assertThat(actual).isEqualTo(1);
    }

    @Test
    void save_duplicateMembership_violatesUniqueConstraint() {

        // given
        var id = communityId();
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("alice").build());

        // when / then
        assertThatThrownBy(
                () -> membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("alice").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByOrganizationIdAndCommunityId_paged() {

        // given
        var id = communityId();
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("alice").build());
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("bob").build());

        // when
        var actual = membershipRepository.findByOrganizationIdAndCommunityId("default", id, PageRequest.of(0, 10));

        // then
        assertThat(actual.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findUserIdsByOrganizationIdAndCommunityId() {

        // given
        var id = communityId();
        var otherCommunity = communityRepository
                .save(CommunityEntityMother.any().slug("frontend-guild").name("Frontend Guild").kindId(kindId).build());
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("alice").build());
        membershipRepository.save(MembershipEntityMother.any().communityId(id).userId("bob").build());
        membershipRepository
                .save(MembershipEntityMother.any().communityId(otherCommunity.getId()).userId("carol").build());

        // when
        var actual = membershipRepository.findUserIdsByOrganizationIdAndCommunityId("default", id);

        // then
        assertThat(actual).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void findUserIdsByOrganizationIdAndCommunityIdAndRole() {

        // given
        var id = communityId();
        membershipRepository.save(
                MembershipEntityMother.any().communityId(id).userId("alice").role(CommunityRole.ORGANIZER).build());
        membershipRepository
                .save(MembershipEntityMother.any().communityId(id).userId("bob").role(CommunityRole.MEMBER).build());

        // when
        var actual = membershipRepository.findUserIdsByOrganizationIdAndCommunityIdAndRole("default", id,
                CommunityRole.ORGANIZER);

        // then
        assertThat(actual).containsExactly("alice");
    }

    /**
     * The real trap {@code display_name} paging exists to avoid: {@link org.springframework.data.domain.PageImpl}
     * rewrites a total handed to it when the content does not fill the page, so a page smaller than the roster is the
     * only way to prove the real total survives. No {@code @SpringBootTest} in this module already exercises
     * {@link MembershipService} against a real database, so it is wired here by hand, with its collaborators outside
     * {@code community} mocked out since this test only exercises the paging path.
     */
    @Test
    void displayNameOrderingContinuesAcrossAPageBoundaryWithTheRealTotal() {

        // given
        var id = communityId();
        for (var userId : List.of("u-ana", "u-bea", "u-carl", "u-dina", "u-eve")) {
            membershipRepository.save(MembershipEntityMother.any().communityId(id).userId(userId).build());
        }
        var organizationContext = mock(OrganizationContext.class);
        when(organizationContext.organizationId()).thenReturn("default");
        var memberDirectory = new MemberDirectory(membershipRepository, mock(UserDirectory.class), organizationContext);
        var service = new MembershipService(communityRepository, membershipRepository, mock(MembershipAuthorizer.class),
                mock(CurrentUser.class), organizationContext, mock(ApplicationEventPublisher.class),
                mock(RoleBindingService.class), memberDirectory);

        // when
        var second = service.listMembers("backend-guild", PageRequest.of(1, 2, Sort.by("display_name")));

        // then
        assertThat(second.getContent()).extracting(Membership::userId).containsExactly("u-carl", "u-dina");
        assertThat(second.getTotalElements()).isEqualTo(5);
    }
}

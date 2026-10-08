package dev.axonia.community;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.access.RoleBindingService;
import dev.axonia.community.internal.CommunityEntity;
import dev.axonia.community.internal.CommunityRepository;
import dev.axonia.community.internal.MembershipAuthorizer;
import dev.axonia.community.internal.MembershipEntity;
import dev.axonia.community.internal.MembershipRepository;
import dev.axonia.community.internal.NameOrderedRoster;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final CommunityRepository communityRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipAuthorizer membershipAuthorizer;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final RoleBindingService roleBindings;
    private final MemberDirectory memberDirectory;

    @Transactional
    public Membership join(String slug) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var community = community(organizationId, slug);

        if (community.getStatus() == CommunityStatus.ARCHIVED) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "Cannot join an archived community");
        }

        membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, community.getId(), userId)
                .ifPresent(existing -> {
                    throw new ConflictException("ALREADY_MEMBER", "Already a member of this community");
                });

        var saved = membershipRepository.save(MembershipEntity.builder().organizationId(organizationId)
                .communityId(community.getId()).userId(userId).role(CommunityRole.MEMBER).build());

        return toMembership(saved);
    }

    @Transactional
    public void leave(String slug) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var community = community(organizationId, slug);
        removeMembership(organizationId, community.getId(), userId, true);
    }

    /** The member's communities that are not archived, ordered by name ignoring case, in one query. */
    @Transactional(readOnly = true)
    public List<MemberCommunity> activeCommunitiesOf(String organizationId, String userId) {

        return membershipRepository.findActiveCommunitiesOf(organizationId, userId);
    }

    /**
     * Community ids the given member belongs to within an Organization, for the account-erasure orchestrator, which
     * acts on an arbitrary member rather than the current caller and so has no slug to start from.
     */
    @Transactional(readOnly = true)
    public List<Long> communityIdsOf(String organizationId, String userId) {

        return membershipRepository.findByOrganizationIdAndUserId(organizationId, userId).stream()
                .map(MembershipEntity::getCommunityId).toList();
    }

    /**
     * Removes a specific member from a specific community on the account-erasure orchestrator's behalf. Unlike
     * {@link #leave(String)}, the acting subject is not the caller - erasure never runs as the person being erased - so
     * organizationId, communityId and userId are all explicit parameters rather than read off
     * {@link OrganizationContext}/{@link CurrentUser}, mirroring how {@code OrganizationDataPurger} takes its
     * organization id explicitly for the same reason. It otherwise mirrors {@link #leave(String)}'s cascade - the same
     * {@link CommunityMemberLeft} event, so the role-binding drop, member-count decrement and identity-provider removal
     * it triggers still happen - with one deliberate difference: it does NOT enforce
     * {@code LAST_ORGANIZER_CANNOT_LEAVE}. Erasure is a data-subject right, not a voluntary departure, and must not be
     * refusable by a housekeeping rule that exists to stop someone accidentally orphaning their own community; an
     * erasure that hit that guard would abort mid-flight after earlier Organizations were already irreversibly erased.
     * A community this leaves organizer-less is recoverable - {@link #changeRole}'s Owner repair hatch lets the hub's
     * Owner promote a replacement even though nobody there currently holds ORGANIZER to authorize it, and applies only
     * while nobody does - which a half-completed erasure is not.
     */
    @Transactional
    public void leaveForErasure(String organizationId, Long communityId, String userId) {

        removeMembership(organizationId, communityId, userId, false);
    }

    /**
     * The shared core of {@link #leave(String)} and {@link #leaveForErasure}: look up the membership, delete it,
     * publish {@link CommunityMemberLeft}. {@code enforceLastOrganizerGuard} is the one behavioural difference between
     * the two callers - see {@link #leaveForErasure}'s javadoc for why erasure skips it.
     */
    private void removeMembership(String organizationId, Long communityId, String userId,
            boolean enforceLastOrganizerGuard) {

        var membership = membership(organizationId, communityId, userId);

        if (enforceLastOrganizerGuard && membership.getRole() == CommunityRole.ORGANIZER
                && isLastOrganizer(organizationId, communityId)) {
            throw new ConflictException("LAST_ORGANIZER_CANNOT_LEAVE", "The last organizer cannot leave");
        }

        membershipRepository.delete(membership);
        eventPublisher.publishEvent(new CommunityMemberLeft(organizationId, communityId, userId));
    }

    /** The caller's own membership of this community, or 404 when they do not belong. */
    @Transactional(readOnly = true)
    public Membership myMembership(String slug) {

        var organizationId = organizationContext.organizationId();
        var community = community(organizationId, slug);
        return toMembership(membership(organizationId, community.getId(), currentUser.userId()));
    }

    @Transactional
    public Membership changeRole(String slug, String userId, CommunityRole role) {

        var organizationId = organizationContext.organizationId();
        var community = community(organizationId, slug);
        var caller = currentUser.userId();

        // The repair hatch, and nothing wider: an Organization's Owner may grant ORGANIZER in a community that
        // currently has no organizer at all. That is the only state leaveForErasure can strand - erasure is a
        // data-subject right and must not be refusable by LAST_ORGANIZER_CANNOT_LEAVE - and every other path to
        // ORGANIZER is itself organizer-gated, so without this the community would be unrecoverable. The
        // organizerCount predicate is what keeps it a repair: a community that still has an organizer is untouched
        // by this, so an Owner cannot join a healthy community, promote themselves, and demote the incumbents. It
        // was reachable before, and while an Owner already holds ORGANIZATION_DELETE over the same scope, a standing
        // widening of an enterprise tenant's authorization model is not something a repair needs.
        var ownerRepairingAnOrganizerlessCommunity = role == CommunityRole.ORGANIZER
                && roleBindings.callerHoldsOwnerRole(organizationId)
                && organizerCount(organizationId, community.getId()) == 0;

        if (!ownerRepairingAnOrganizerlessCommunity) {
            membershipAuthorizer.requireOrganizerOrOpenMember(organizationId, community.getId(), caller,
                    community.isCanManageMembers());
        }

        var membership = membership(organizationId, community.getId(), userId);

        // Crossing the organizer boundary - granting ORGANIZER or changing an organizer's role - stays reserved to
        // organizers even when the community opens member management to members, except for the Owner repair hatch
        // above, which by construction only applies where there is no organizer left to ask.
        if ((role == CommunityRole.ORGANIZER || membership.getRole() == CommunityRole.ORGANIZER)
                && !ownerRepairingAnOrganizerlessCommunity) {
            membershipAuthorizer.requireOrganizer(organizationId, community.getId(), caller);
        }

        if (membership.getRole() == CommunityRole.ORGANIZER && role == CommunityRole.MEMBER
                && isLastOrganizer(organizationId, community.getId())) {
            throw new ConflictException("LAST_ORGANIZER_CANNOT_BE_DEMOTED", "The last organizer cannot be demoted");
        }

        var saved = membershipRepository.save(membership.toBuilder().role(role).build());
        return toMembership(saved);
    }

    @Transactional(readOnly = true)
    public Page<Membership> listMembers(String slug, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var community = community(organizationId, slug);
        if (NameOrderedRoster.isRequested(pageable)) {
            return byDisplayName(organizationId, community.getId(), pageable);
        }
        return membershipRepository.findByOrganizationIdAndCommunityId(organizationId, community.getId(), pageable)
                .map(this::toMembership);
    }

    /**
     * Display names live in {@code identity}, so there is no column to page on: the roster is ordered whole and then
     * sliced. The slice's rows come back from the IN clause in the database's own order, so they are put back into the
     * ordering's order before the page is built.
     */
    private Page<Membership> byDisplayName(String organizationId, Long communityId, Pageable pageable) {

        var ordered = NameOrderedRoster.inRequestedDirection(memberDirectory.rosterOrderedByName(communityId),
                pageable);
        var from = (int) Math.min(pageable.getOffset(), ordered.size());
        var to = Math.min(from + pageable.getPageSize(), ordered.size());
        var pageIds = ordered.subList(from, to);
        if (pageIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ordered.size());
        }
        var rows = membershipRepository
                .findByOrganizationIdAndCommunityIdAndUserIdIn(organizationId, communityId, pageIds).stream()
                .collect(Collectors.toMap(MembershipEntity::getUserId, this::toMembership));
        var content = pageIds.stream().map(rows::get).filter(Objects::nonNull).toList();
        return new PageImpl<>(content, pageable, ordered.size());
    }

    /** The members of this community whose name matches {@code query}, asked by somebody who belongs to it. */
    @Transactional(readOnly = true)
    public List<CommunityMember> searchMembers(String slug, String query) {

        var organizationId = organizationContext.organizationId();
        var community = community(organizationId, slug);
        if (!isMember(organizationId, community.getId(), currentUser.userId())) {
            throw new ForbiddenException("Community membership required");
        }
        return memberDirectory.search(community.getId(), query);
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long communityId, String userId) {

        return isMember(organizationContext.organizationId(), communityId, userId);
    }

    /**
     * Org-explicit variant of {@link #isMember(Long, String)}, for a caller with no HTTP request to read an
     * {@link OrganizationContext} from - the communication drain's delivery gate, which runs each organization's due
     * work inside {@code OrganizationScope.runAs} and carries no JWT, so reading {@link #organizationContext} here
     * would throw rather than resolve.
     */
    @Transactional(readOnly = true)
    public boolean isMember(String organizationId, Long communityId, String userId) {

        return membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isOrganizer(Long communityId, String userId) {

        var organizationId = organizationContext.organizationId();
        return membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId)
                .map(m -> m.getRole() == CommunityRole.ORGANIZER).orElse(false);
    }

    /**
     * Every member of the community, as bare user ids - org-explicit, for the communication sweep's producers, which
     * run inside {@code OrganizationScope.runAs} and carry no JWT, so reading {@link #organizationContext} here would
     * throw rather than resolve. The audience for a community-wide notice like {@code SESSION_UPCOMING} or
     * {@code SESSION_WRAP_UP}.
     */
    @Transactional(readOnly = true)
    public List<String> memberUserIds(String organizationId, Long communityId) {

        return membershipRepository.findUserIdsByOrganizationIdAndCommunityId(organizationId, communityId);
    }

    /**
     * The community's organizers, as bare user ids - org-explicit, same caller as {@link #memberUserIds}. Part of the
     * audience for the awaiting-close nudge alongside the session's facilitator.
     */
    @Transactional(readOnly = true)
    public List<String> organizerUserIds(String organizationId, Long communityId) {

        return membershipRepository.findUserIdsByOrganizationIdAndCommunityIdAndRole(organizationId, communityId,
                CommunityRole.ORGANIZER);
    }

    private CommunityEntity community(String organizationId, String slug) {

        return communityRepository.findByOrganizationIdAndSlug(organizationId, slug)
                .orElseThrow(() -> new NotFoundException("Community not found: " + slug));
    }

    private MembershipEntity membership(String organizationId, Long communityId, String userId) {

        return membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId)
                .orElseThrow(() -> new NotFoundException("Membership not found"));
    }

    private boolean isLastOrganizer(String organizationId, Long communityId) {

        return organizerCount(organizationId, communityId) <= 1;
    }

    private long organizerCount(String organizationId, Long communityId) {

        return membershipRepository.countByOrganizationIdAndCommunityIdAndRole(organizationId, communityId,
                CommunityRole.ORGANIZER);
    }

    private Membership toMembership(MembershipEntity e) {

        return Membership.builder().id(e.getId()).communityId(e.getCommunityId()).userId(e.getUserId())
                .role(e.getRole()).joinedAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }
}

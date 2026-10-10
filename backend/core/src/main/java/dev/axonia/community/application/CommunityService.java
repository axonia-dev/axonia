package dev.axonia.community.application;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.community.api.CommunityDetailsChanged;
import dev.axonia.community.api.CommunityIdentity;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.FocalPoint;
import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityKindRecord;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.MembershipRecord;
import dev.axonia.community.domain.Slugs;
import dev.axonia.media.api.MediaLinks;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.metrics.UsageCounters;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommunityService implements Communities {

    private static final String COUNT_FIXED = "COMMUNITY_COUNT_FIXED";

    private final CommunityStore communityStore;
    private final CommunityKindStore communityKindStore;
    private final MembershipLedger membershipLedger;
    private final CommunityLookup communities;
    private final MembershipAuthorizer membershipAuthorizer;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final MediaLinks mediaLinks;
    private final AuthorizationGate gate;
    private final CommunityCountRule communityCountRule;
    private final ApplicationEventPublisher eventPublisher;
    private final UsageCounters usageCounters;

    @Transactional
    public Community create(CreateCommunityRequest request) {

        gate.requirePermission(Permission.COMMUNITY_CREATE);

        var organizationId = organizationContext.organizationId();
        if (communityCountRule.countFixedAtFounding(organizationId)) {
            throw new ConflictException(COUNT_FIXED,
                    "This organization's communities were all created with it, so another cannot be started.");
        }

        var userId = currentUser.userId();
        var kind = requireKind(organizationId, request.kindId());
        var slug = uniqueSlug(organizationId, Slugs.slugify(request.name()));

        var saved = communityStore.save(CommunityRecord.builder().organizationId(organizationId).slug(slug)
                .name(request.name()).kindId(kind.getId()).description(description(request.description()))
                .status(CommunityStatus.ACTIVE).build());

        membershipLedger.save(MembershipRecord.builder().organizationId(organizationId).communityId(saved.getId())
                .userId(userId).role(CommunityRole.ORGANIZER).build());

        // Deferred to the commit of this method's own transaction: the community and its organizer membership are
        // written but not yet durable here, and a counter incremented inline would keep counting through a release
        // in which every one of these rolled back. The hub's single auto-provisioned community deliberately does not
        // reach this - see CommunityProvisioningService - because it is a hub being born rather than anybody
        // choosing to start a community, and it is already 1:1 with its hub.
        usageCounters.recordAfterCommit(UsageCounters.Event.COMMUNITY_CREATED);

        return toCommunity(saved, 1L, kind.getLabel());
    }

    @Transactional
    public Community update(String slug, UpdateCommunityRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findBySlugOrThrow(organizationId, slug);
        membershipAuthorizer.requireOrganizerOrOpenMember(organizationId, entity.getId(), currentUser.userId(),
                entity.isCanEditCommunity());

        if (entity.getStatus() == CommunityStatus.ARCHIVED) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "Cannot edit an archived community");
        }

        var kind = requireKind(organizationId, request.kindId());
        var saved = communityStore.save(entity.toBuilder().name(request.name()).kindId(kind.getId())
                .description(description(request.description())).build());

        if (!saved.getName().equals(entity.getName()) || !saved.getDescription().equals(entity.getDescription())) {
            eventPublisher.publishEvent(new CommunityDetailsChanged(organizationId, saved.getId(), saved.getName(),
                    saved.getDescription()));
        }

        return toCommunity(saved, memberCount(organizationId, saved.getId()), kind.getLabel());
    }

    @Transactional
    public Community archive(String slug) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findBySlugOrThrow(organizationId, slug);
        membershipAuthorizer.requireOrganizerOrOpenMember(organizationId, entity.getId(), currentUser.userId(),
                entity.isCanArchiveCommunity());

        if (communityCountRule.countFixedAtFounding(organizationId)) {
            throw new ConflictException(COUNT_FIXED,
                    "This organization's communities were all created with it, so this one cannot be archived.");
        }

        if (entity.getStatus() == CommunityStatus.ARCHIVED) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "Community is already archived");
        }

        var saved = communityStore.save(entity.toBuilder().status(CommunityStatus.ARCHIVED).build());
        return toCommunity(saved, memberCount(organizationId, saved.getId()),
                requireKind(organizationId, saved.getKindId()).getLabel());
    }

    @Override
    @Transactional(readOnly = true)
    public Community getBySlug(String slug) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findBySlugOrThrow(organizationId, slug);
        return toCommunity(entity, memberCount(organizationId, entity.getId()),
                requireKind(organizationId, entity.getKindId()).getLabel());
    }

    /**
     * The name of the single community that IS a hub Organization (strict 1:1: a hub's one community is provisioned
     * with the hub id as its slug). For the account-erasure orchestrator's sole-owner guard, which must name the
     * community a person would need to delete first, and which - unlike {@link #getBySlug} - has no session to read an
     * {@link OrganizationContext} from and acts on an arbitrary Organization by id. Empty when the Organization is not
     * a hub, or is a hub whose community lookup otherwise fails.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> hubCommunityName(String organizationId) {

        return communityStore.findBySlug(organizationId, organizationId).map(CommunityRecord::getName);
    }

    @Override
    @Transactional(readOnly = true)
    public Community getById(Long id) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findByIdOrThrow(organizationId, id);
        return toCommunity(entity, memberCount(organizationId, entity.getId()),
                requireKind(organizationId, entity.getKindId()).getLabel());
    }

    /**
     * Org-explicit variant of {@link #getById(Long)}, for a caller with no HTTP request to read an
     * {@link OrganizationContext} from - the communication sweep's producers, which run inside
     * {@code OrganizationScope.runAs} and carry no JWT. Returns only what is already on the row this loads: the member
     * count and the kind label {@link #getById(Long)} also resolves each cost a query of their own, and no producer
     * needs either.
     */
    @Override
    @Transactional(readOnly = true)
    public CommunityIdentity identityById(String organizationId, Long id) {

        var entity = communities.findByIdOrThrow(organizationId, id);
        return new CommunityIdentity(entity.getSlug(), entity.getName(), entity.getAccentColor());
    }

    /**
     * The same three columns as {@link #identityById} for several communities at once, in one query, keyed by id. An id
     * that no longer resolves is absent from the map rather than an exception: the callers are read models holding a
     * reference that outlived the community it points at (an evidence item, a saved item, a recognized contribution),
     * and for them a removed community is an ordinary missing link target, not a failure. Handing it back as a
     * {@link NotFoundException} would also poison their transaction - a {@code @Transactional} method that throws marks
     * the transaction it joined rollback-only, whatever the caller then catches.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, CommunityIdentity> identitiesByIds(Collection<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }
        return communityStore.findByIds(organizationContext.organizationId(), ids).stream().collect(Collectors.toMap(
                CommunityRecord::getId, e -> new CommunityIdentity(e.getSlug(), e.getName(), e.getAccentColor())));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Community> directory(String q, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var filter = q == null ? "" : q;
        var labels = kindLabels(organizationId);
        return communityStore.searchByName(organizationId, CommunityStatus.ACTIVE, filter, pageable)
                .map(entity -> toCommunity(entity, memberCount(organizationId, entity.getId()),
                        labels.get(entity.getKindId())));
    }

    private CommunityKindRecord requireKind(String organizationId, Long kindId) {

        return communityKindStore.find(organizationId, kindId)
                .orElseThrow(() -> new NotFoundException("Kind not found: " + kindId));
    }

    private Map<Long, String> kindLabels(String organizationId) {

        return communityKindStore.listByLabel(organizationId).stream()
                .collect(Collectors.toMap(CommunityKindRecord::getId, CommunityKindRecord::getLabel));
    }

    private String uniqueSlug(String organizationId, String base) {

        var candidate = base;
        var counter = 1;
        while (communityStore.slugTaken(organizationId, candidate)) {
            counter++;
            candidate = base + "-" + counter;
        }
        return candidate;
    }

    private long memberCount(String organizationId, Long communityId) {

        return membershipLedger.count(organizationId, communityId);
    }

    private String description(String value) {

        return Objects.requireNonNullElse(value, "");
    }

    private Community toCommunity(CommunityRecord e, long memberCount, String kindLabel) {

        return Community.builder().id(e.getId()).slug(e.getSlug()).name(e.getName()).kindId(e.getKindId())
                .kind(kindLabel).description(e.getDescription()).status(e.getStatus()).memberCount(memberCount)
                .badgeUrl(resolve(e.getBadgeKey())).heroUrl(resolve(e.getHeroKey())).accentColor(e.getAccentColor())
                .heroFocus(FocalPoint.fromColumns(e.getHeroFocusX(), e.getHeroFocusY()))
                .badgeFocus(FocalPoint.fromColumns(e.getBadgeFocusX(), e.getBadgeFocusY())).createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt()).build();
    }

    private String resolve(String key) {

        return Optional.ofNullable(key).map(mediaLinks::urlFor).orElse(null);
    }
}

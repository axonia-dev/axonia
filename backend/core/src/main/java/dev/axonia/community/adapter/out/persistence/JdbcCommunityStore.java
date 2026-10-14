package dev.axonia.community.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.MembershipFilter;
import dev.axonia.community.application.port.out.CommunitySearch;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.KindCommunityCount;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcCommunityStore implements CommunityStore {

    private final CommunityRepository communityRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CommunityRecord> findBySlug(String organizationId, String slug) {

        return communityRepository.findByOrganizationIdAndSlug(organizationId, slug).map(JdbcCommunityStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommunityRecord> findById(String organizationId, Long id) {

        return communityRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcCommunityStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityRecord> findByIds(String organizationId, Collection<Long> ids) {

        return communityRepository.findByOrganizationIdAndIdIn(organizationId, ids).stream()
                .map(JdbcCommunityStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean slugTaken(String organizationId, String slug) {

        return communityRepository.existsByOrganizationIdAndSlug(organizationId, slug);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommunityRecord> searchByName(String organizationId, CommunityStatus status, String name,
            Pageable pageable) {

        return communityRepository
                .findByOrganizationIdAndStatusAndNameContainingIgnoreCase(organizationId, status, name, pageable)
                .map(JdbcCommunityStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommunityRecord> search(CommunitySearch search, Pageable pageable) {

        var unfiltered = search.membership() == null
                || search.membership() == MembershipFilter.EXPLORE && search.memberOf().isEmpty();
        if (unfiltered) {
            return searchByName(search.organizationId(), search.status(), search.name(), pageable);
        }
        if (search.memberOf().isEmpty()) {
            return Page.empty(pageable);
        }
        var page = search.membership() == MembershipFilter.JOINED
                ? communityRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCaseAndIdIn(
                        search.organizationId(), search.status(), search.name(), search.memberOf(), pageable)
                : communityRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCaseAndIdNotIn(
                        search.organizationId(), search.status(), search.name(), search.memberOf(), pageable);
        return page.map(JdbcCommunityStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOfKind(String organizationId, Long kindId) {

        return communityRepository.countByOrganizationIdAndKindId(organizationId, kindId);
    }

    @Override
    public void reassignKind(String organizationId, Long source, Long target) {

        communityRepository.reassignKind(organizationId, source, target);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KindCommunityCount> countByKind(String organizationId) {

        return communityRepository.countCommunitiesByKind(organizationId).stream()
                .map(row -> new KindCommunityCount(row.kindId(), row.communityCount())).toList();
    }

    @Override
    @Transactional
    public CommunityRecord save(CommunityRecord community) {

        return toRecord(communityRepository.save(toEntity(community)));
    }

    static CommunityRecord toRecord(CommunityEntity e) {

        return CommunityRecord.builder().id(e.getId()).organizationId(e.getOrganizationId()).slug(e.getSlug())
                .name(e.getName()).kindId(e.getKindId()).description(e.getDescription()).status(e.getStatus())
                .badgeKey(e.getBadgeKey()).heroKey(e.getHeroKey()).accentColor(e.getAccentColor())
                .heroFocusX(e.getHeroFocusX()).heroFocusY(e.getHeroFocusY()).badgeFocusX(e.getBadgeFocusX())
                .badgeFocusY(e.getBadgeFocusY()).canStartSeries(e.isCanStartSeries())
                .canEditStructure(e.isCanEditStructure()).canQueueTopics(e.isCanQueueTopics())
                .canManageSchedule(e.isCanManageSchedule()).canEditCommunity(e.isCanEditCommunity())
                .canManageLookAndFeel(e.isCanManageLookAndFeel()).canManageMembers(e.isCanManageMembers())
                .canManageContributionPolicy(e.isCanManageContributionPolicy())
                .canArchiveCommunity(e.isCanArchiveCommunity()).canCurateTags(e.isCanCurateTags())
                .canViewHealth(e.isCanViewHealth()).canInviteMembers(e.isCanInviteMembers())
                .canManageCommunication(e.isCanManageCommunication())
                .canCreditContributions(e.isCanCreditContributions()).notifySessionUpcoming(e.isNotifySessionUpcoming())
                .notifySessionWrapUp(e.isNotifySessionWrapUp()).sessionUpcomingLeadDays(e.getSessionUpcomingLeadDays())
                .wrapUpGraceHours(e.getWrapUpGraceHours()).topicManagementPolicy(e.getTopicManagementPolicy())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).version(e.getVersion()).build();
    }

    static CommunityEntity toEntity(CommunityRecord c) {

        return CommunityEntity.builder().id(c.getId()).organizationId(c.getOrganizationId()).slug(c.getSlug())
                .name(c.getName()).kindId(c.getKindId()).description(c.getDescription()).status(c.getStatus())
                .badgeKey(c.getBadgeKey()).heroKey(c.getHeroKey()).accentColor(c.getAccentColor())
                .heroFocusX(c.getHeroFocusX()).heroFocusY(c.getHeroFocusY()).badgeFocusX(c.getBadgeFocusX())
                .badgeFocusY(c.getBadgeFocusY()).canStartSeries(c.isCanStartSeries())
                .canEditStructure(c.isCanEditStructure()).canQueueTopics(c.isCanQueueTopics())
                .canManageSchedule(c.isCanManageSchedule()).canEditCommunity(c.isCanEditCommunity())
                .canManageLookAndFeel(c.isCanManageLookAndFeel()).canManageMembers(c.isCanManageMembers())
                .canManageContributionPolicy(c.isCanManageContributionPolicy())
                .canArchiveCommunity(c.isCanArchiveCommunity()).canCurateTags(c.isCanCurateTags())
                .canViewHealth(c.isCanViewHealth()).canInviteMembers(c.isCanInviteMembers())
                .canManageCommunication(c.isCanManageCommunication())
                .canCreditContributions(c.isCanCreditContributions()).notifySessionUpcoming(c.isNotifySessionUpcoming())
                .notifySessionWrapUp(c.isNotifySessionWrapUp()).sessionUpcomingLeadDays(c.getSessionUpcomingLeadDays())
                .wrapUpGraceHours(c.getWrapUpGraceHours()).topicManagementPolicy(c.getTopicManagementPolicy())
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).version(c.getVersion()).build();
    }
}

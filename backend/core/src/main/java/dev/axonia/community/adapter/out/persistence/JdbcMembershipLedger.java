package dev.axonia.community.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.MemberCommunity;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.domain.MembershipRecord;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcMembershipLedger implements MembershipLedger {

    private final MembershipRepository membershipRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipRecord> find(String organizationId, Long communityId, String userId) {

        return membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId)
                .map(JdbcMembershipLedger::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipRecord> findAllOf(String organizationId, String userId) {

        return membershipRepository.findByOrganizationIdAndUserId(organizationId, userId).stream()
                .map(JdbcMembershipLedger::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCommunity> activeCommunitiesOf(String organizationId, String userId) {

        return membershipRepository.findActiveCommunitiesOf(organizationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(String organizationId, Long communityId) {

        return membershipRepository.countByOrganizationIdAndCommunityId(organizationId, communityId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countWithRole(String organizationId, Long communityId, CommunityRole role) {

        return membershipRepository.countByOrganizationIdAndCommunityIdAndRole(organizationId, communityId, role);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MembershipRecord> page(String organizationId, Long communityId, Pageable pageable) {

        return membershipRepository.findByOrganizationIdAndCommunityId(organizationId, communityId, pageable)
                .map(JdbcMembershipLedger::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> memberUserIds(String organizationId, Long communityId) {

        return membershipRepository.findUserIdsByOrganizationIdAndCommunityId(organizationId, communityId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> userIdsWithRole(String organizationId, Long communityId, CommunityRole role) {

        return membershipRepository.findUserIdsByOrganizationIdAndCommunityIdAndRole(organizationId, communityId, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipRecord> findMembers(String organizationId, Long communityId, Collection<String> userIds) {

        return membershipRepository.findByOrganizationIdAndCommunityIdAndUserIdIn(organizationId, communityId, userIds)
                .stream().map(JdbcMembershipLedger::toRecord).toList();
    }

    @Override
    @Transactional
    public MembershipRecord save(MembershipRecord membership) {

        return toRecord(membershipRepository.save(toEntity(membership)));
    }

    @Override
    public void delete(MembershipRecord membership) {

        membershipRepository.delete(toEntity(membership));
    }

    static MembershipRecord toRecord(MembershipEntity e) {

        return MembershipRecord.builder().id(e.getId()).organizationId(e.getOrganizationId())
                .communityId(e.getCommunityId()).userId(e.getUserId()).role(e.getRole()).createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt()).version(e.getVersion()).build();
    }

    static MembershipEntity toEntity(MembershipRecord m) {

        return MembershipEntity.builder().id(m.getId()).organizationId(m.getOrganizationId())
                .communityId(m.getCommunityId()).userId(m.getUserId()).role(m.getRole()).createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt()).version(m.getVersion()).build();
    }
}

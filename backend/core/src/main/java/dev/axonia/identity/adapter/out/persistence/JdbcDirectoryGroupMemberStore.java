package dev.axonia.identity.adapter.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.DirectoryGroupMemberStore;
import dev.axonia.identity.domain.DirectoryGroupMember;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcDirectoryGroupMemberStore implements DirectoryGroupMemberStore {

    private final DirectoryGroupMemberRepository directoryGroupMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryGroupMember> findByOrganizationIdAndGroupId(String organizationId, Long groupId) {
        return directoryGroupMemberRepository.findByOrganizationIdAndGroupId(organizationId, groupId).stream()
                .map(JdbcDirectoryGroupMemberStore::toDirectoryGroupMember).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findUserIdsByOrganizationIdAndGroupIdIn(String organizationId, Collection<Long> groupIds) {
        return directoryGroupMemberRepository.findUserIdsByOrganizationIdAndGroupIdIn(organizationId, groupIds);
    }

    @Override
    public void insertIgnoreDuplicate(String organizationId, Long groupId, String userScimId) {
        directoryGroupMemberRepository.insertIgnoreDuplicate(organizationId, groupId, userScimId);
    }

    @Override
    public void deleteByOrganizationIdAndGroupIdAndUserScimId(String organizationId, Long groupId, String userScimId) {
        directoryGroupMemberRepository.deleteByOrganizationIdAndGroupIdAndUserScimId(organizationId, groupId,
                userScimId);
    }

    @Override
    public void deleteAllByOrganizationIdAndGroupId(String organizationId, Long groupId) {
        directoryGroupMemberRepository.deleteAllByOrganizationIdAndGroupId(organizationId, groupId);
    }

    static DirectoryGroupMember toDirectoryGroupMember(DirectoryGroupMemberEntity entity) {
        return DirectoryGroupMember.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .groupId(entity.getGroupId()).userScimId(entity.getUserScimId()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static DirectoryGroupMemberEntity toEntity(DirectoryGroupMember directoryGroupMember) {
        return DirectoryGroupMemberEntity.builder().id(directoryGroupMember.id())
                .organizationId(directoryGroupMember.organizationId()).groupId(directoryGroupMember.groupId())
                .userScimId(directoryGroupMember.userScimId()).createdAt(directoryGroupMember.createdAt())
                .updatedAt(directoryGroupMember.updatedAt()).version(directoryGroupMember.version()).build();
    }
}

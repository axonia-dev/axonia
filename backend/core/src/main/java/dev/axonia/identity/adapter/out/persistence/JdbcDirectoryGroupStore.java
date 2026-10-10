package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.DirectoryGroupStore;
import dev.axonia.identity.domain.DirectoryGroup;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcDirectoryGroupStore implements DirectoryGroupStore {

    private final DirectoryGroupRepository directoryGroupRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<DirectoryGroup> findByOrganizationIdAndScimId(String organizationId, String scimId) {
        return directoryGroupRepository.findByOrganizationIdAndScimId(organizationId, scimId)
                .map(JdbcDirectoryGroupStore::toDirectoryGroup);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DirectoryGroup> findByOrganizationIdAndReconciliationKey(String organizationId,
            String reconciliationKey) {
        return directoryGroupRepository.findByOrganizationIdAndReconciliationKey(organizationId, reconciliationKey)
                .map(JdbcDirectoryGroupStore::toDirectoryGroup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryGroup> findByOrganizationIdAndDisplayName(String organizationId, String displayName) {
        return directoryGroupRepository.findByOrganizationIdAndDisplayName(organizationId, displayName).stream()
                .map(JdbcDirectoryGroupStore::toDirectoryGroup).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryGroup> findByOrganizationIdAndReconciliationKeyIn(String organizationId,
            Collection<String> reconciliationKeys) {
        return directoryGroupRepository.findByOrganizationIdAndReconciliationKeyIn(organizationId, reconciliationKeys)
                .stream().map(JdbcDirectoryGroupStore::toDirectoryGroup).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryGroup> findByOrganizationId(String organizationId, int limit) {
        return directoryGroupRepository.findByOrganizationId(organizationId, PageRequest.of(0, limit)).stream()
                .map(JdbcDirectoryGroupStore::toDirectoryGroup).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryGroup> findByOrganizationIdAndConfirmedAtIsNull(String organizationId) {
        return directoryGroupRepository.findByOrganizationIdAndConfirmedAtIsNull(organizationId).stream()
                .map(JdbcDirectoryGroupStore::toDirectoryGroup).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryGroup> findByOrganizationIdAndMemberScimId(String organizationId, String userScimId) {
        return directoryGroupRepository.findByOrganizationIdAndMemberScimId(organizationId, userScimId).stream()
                .map(JdbcDirectoryGroupStore::toDirectoryGroup).toList();
    }

    @Override
    @Transactional
    public DirectoryGroup save(DirectoryGroup group) {
        return toDirectoryGroup(directoryGroupRepository.save(toEntity(group)));
    }

    @Override
    @Transactional
    public void delete(DirectoryGroup group) {
        directoryGroupRepository.delete(toEntity(group));
    }

    @Override
    @Transactional
    public int rekeyToExternalId(String organizationId) {
        return directoryGroupRepository.rekeyToExternalId(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryGroup> findConfirmedGroupsRekeyingToExternalId(String organizationId) {
        return directoryGroupRepository.findConfirmedGroupsRekeyingToExternalId(organizationId).stream()
                .map(JdbcDirectoryGroupStore::toDirectoryGroup).toList();
    }

    @Override
    @Transactional
    public int rekeyToDisplayName(String organizationId) {
        return directoryGroupRepository.rekeyToDisplayName(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryGroup> findConfirmedGroupsRekeyingToDisplayName(String organizationId) {
        return directoryGroupRepository.findConfirmedGroupsRekeyingToDisplayName(organizationId).stream()
                .map(JdbcDirectoryGroupStore::toDirectoryGroup).toList();
    }

    @Override
    @Transactional
    public int holdByIds(String organizationId, Collection<Long> ids, Instant updatedAt) {
        return directoryGroupRepository.holdByIds(organizationId, ids, updatedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public int countGroupsWithoutExternalId(String organizationId) {
        return directoryGroupRepository.countGroupsWithoutExternalId(organizationId);
    }

    static DirectoryGroup toDirectoryGroup(DirectoryGroupEntity entity) {
        return DirectoryGroup.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .scimId(entity.getScimId()).externalId(entity.getExternalId()).displayName(entity.getDisplayName())
                .reconciliationKey(entity.getReconciliationKey()).confirmedAt(entity.getConfirmedAt())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static DirectoryGroupEntity toEntity(DirectoryGroup directoryGroup) {
        return DirectoryGroupEntity.builder().id(directoryGroup.id()).organizationId(directoryGroup.organizationId())
                .scimId(directoryGroup.scimId()).externalId(directoryGroup.externalId())
                .displayName(directoryGroup.displayName()).reconciliationKey(directoryGroup.reconciliationKey())
                .confirmedAt(directoryGroup.confirmedAt()).createdAt(directoryGroup.createdAt())
                .updatedAt(directoryGroup.updatedAt()).version(directoryGroup.version()).build();
    }
}

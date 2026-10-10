package dev.axonia.community.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.domain.CommunityKindRecord;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcCommunityKindStore implements CommunityKindStore {

    private final CommunityKindRepository communityKindRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CommunityKindRecord> listByLabel(String organizationId) {

        return communityKindRepository.findByOrganizationIdOrderByLabelAsc(organizationId).stream()
                .map(JdbcCommunityKindStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommunityKindRecord> find(String organizationId, Long id) {

        return communityKindRepository.findByOrganizationIdAndId(organizationId, id)
                .map(JdbcCommunityKindStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean labelTaken(String organizationId, String label) {

        return communityKindRepository.existsByOrganizationIdAndLabel(organizationId, label);
    }

    @Override
    @Transactional
    public CommunityKindRecord save(CommunityKindRecord kind) {

        return toRecord(communityKindRepository.save(toEntity(kind)));
    }

    @Override
    public void delete(CommunityKindRecord kind) {

        communityKindRepository.delete(toEntity(kind));
    }

    static CommunityKindRecord toRecord(CommunityKindEntity e) {

        return CommunityKindRecord.builder().id(e.getId()).organizationId(e.getOrganizationId()).label(e.getLabel())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).version(e.getVersion()).build();
    }

    static CommunityKindEntity toEntity(CommunityKindRecord k) {

        return CommunityKindEntity.builder().id(k.getId()).organizationId(k.getOrganizationId()).label(k.getLabel())
                .createdAt(k.getCreatedAt()).updatedAt(k.getUpdatedAt()).version(k.getVersion()).build();
    }
}

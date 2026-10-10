package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.domain.SectionTypeRecord;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcSectionTypeStore implements SectionTypeStore {

    private final SectionTypeRepository sectionTypeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SectionTypeRecord> findByOrganizationIdOrderByLabelAsc(String organizationId) {

        return sectionTypeRepository.findByOrganizationIdOrderByLabelAsc(organizationId).stream()
                .map(JdbcSectionTypeStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SectionTypeRecord> findByOrganizationIdAndId(String organizationId, Long id) {

        return sectionTypeRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcSectionTypeStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionTypeRecord> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids) {

        return sectionTypeRepository.findByOrganizationIdAndIdIn(organizationId, ids).stream()
                .map(JdbcSectionTypeStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOrganizationIdAndLabel(String organizationId, String label) {

        return sectionTypeRepository.existsByOrganizationIdAndLabel(organizationId, label);
    }

    @Override
    @Transactional(readOnly = true)
    public long countStructureRefs(Long typeId) {

        return sectionTypeRepository.countStructureRefs(typeId);
    }

    @Override
    public void reassignStructureRefs(Long source, Long target) {

        sectionTypeRepository.reassignStructureRefs(source, target);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSectionRefs(Long typeId) {

        return sectionTypeRepository.countSectionRefs(typeId);
    }

    @Override
    public void reassignSectionRefs(Long source, Long target) {

        sectionTypeRepository.reassignSectionRefs(source, target);
    }

    @Override
    @Transactional
    public SectionTypeRecord save(SectionTypeRecord sectionType) {

        return toRecord(sectionTypeRepository.save(toEntity(sectionType)));
    }

    @Override
    public void delete(SectionTypeRecord sectionType) {

        sectionTypeRepository.delete(toEntity(sectionType));
    }

    static SectionTypeRecord toRecord(SectionTypeEntity entity) {

        return SectionTypeRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .label(entity.getLabel()).expectsPresenter(entity.isExpectsPresenter()).icon(entity.getIcon())
                .accentColor(entity.getAccentColor()).createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion()).build();
    }

    static SectionTypeEntity toEntity(SectionTypeRecord sectionType) {

        return SectionTypeEntity.builder().id(sectionType.getId()).organizationId(sectionType.getOrganizationId())
                .label(sectionType.getLabel()).expectsPresenter(sectionType.isExpectsPresenter())
                .icon(sectionType.getIcon()).accentColor(sectionType.getAccentColor())
                .createdAt(sectionType.getCreatedAt()).updatedAt(sectionType.getUpdatedAt())
                .version(sectionType.getVersion()).build();
    }
}

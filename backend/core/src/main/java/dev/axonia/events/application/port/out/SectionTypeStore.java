package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.axonia.events.domain.SectionTypeRecord;

public interface SectionTypeStore {

    List<SectionTypeRecord> findByOrganizationIdOrderByLabelAsc(String organizationId);

    Optional<SectionTypeRecord> findByOrganizationIdAndId(String organizationId, Long id);

    List<SectionTypeRecord> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);

    boolean existsByOrganizationIdAndLabel(String organizationId, String label);

    long countStructureRefs(Long typeId);

    void reassignStructureRefs(Long source, Long target);

    long countSectionRefs(Long typeId);

    void reassignSectionRefs(Long source, Long target);

    SectionTypeRecord save(SectionTypeRecord sectionType);

    void delete(SectionTypeRecord sectionType);
}

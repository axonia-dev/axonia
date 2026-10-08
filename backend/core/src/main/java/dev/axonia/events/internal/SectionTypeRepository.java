package dev.axonia.events.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface SectionTypeRepository extends CrudRepository<SectionTypeEntity, Long> {

    List<SectionTypeEntity> findByOrganizationIdOrderByLabelAsc(String organizationId);

    Optional<SectionTypeEntity> findByOrganizationIdAndId(String organizationId, Long id);

    /** Every named section type in one read, so {@code SessionMapper} resolves a batch's labels once. */
    List<SectionTypeEntity> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);

    boolean existsByOrganizationIdAndLabel(String organizationId, String label);

    @Query("SELECT count(*) FROM event_series_section_type WHERE section_type_id = :typeId")
    long countStructureRefs(@Param("typeId") Long typeId);

    @Modifying
    @Query("UPDATE event_series_section_type SET section_type_id = :target WHERE section_type_id = :source")
    void reassignStructureRefs(@Param("source") Long source, @Param("target") Long target);

    @Query("SELECT count(*) FROM event_section WHERE section_type_id = :typeId")
    long countSectionRefs(@Param("typeId") Long typeId);

    @Modifying
    @Query("UPDATE event_section SET section_type_id = :target WHERE section_type_id = :source")
    void reassignSectionRefs(@Param("source") Long source, @Param("target") Long target);
}

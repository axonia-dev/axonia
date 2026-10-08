package dev.axonia.events;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.NotFoundException;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.Permission;
import dev.axonia.events.internal.SectionTypeEntity;
import dev.axonia.events.internal.SectionTypeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SectionTypeService {

    private final SectionTypeRepository sectionTypeRepository;
    private final OrganizationContext organizationContext;
    private final AuthorizationGate gate;

    @Transactional(readOnly = true)
    public List<SectionType> list() {

        // Reading the section-type list is reference data every event-edit flow needs; it is not gated by the admin
        // SECTION_TYPE_READ permission. Mutations below remain gated by SECTION_TYPE_WRITE.
        var organizationId = organizationContext.organizationId();
        return sectionTypeRepository.findByOrganizationIdOrderByLabelAsc(organizationId).stream().map(this::toType)
                .toList();
    }

    @Transactional
    public SectionType create(CreateSectionTypeRequest request) {

        gate.requirePermission(Permission.SECTION_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        if (sectionTypeRepository.existsByOrganizationIdAndLabel(organizationId, request.label())) {
            throw new ConflictException("SECTION_TYPE_EXISTS", "Section type already exists: " + request.label());
        }
        var expectsPresenter = request.expectsPresenter() == null || request.expectsPresenter();
        var saved = sectionTypeRepository.save(SectionTypeEntity.builder().organizationId(organizationId)
                .label(request.label()).expectsPresenter(expectsPresenter).icon(request.icon())
                .accentColor(request.accentColor()).build());
        return toType(saved);
    }

    @Transactional
    public SectionType update(Long typeId, UpdateSectionTypeRequest request) {

        gate.requirePermission(Permission.SECTION_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        var entity = sectionTypeRepository.findByOrganizationIdAndId(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Section type not found: " + typeId));
        if (!entity.getLabel().equals(request.label())
                && sectionTypeRepository.existsByOrganizationIdAndLabel(organizationId, request.label())) {
            throw new ConflictException("SECTION_TYPE_EXISTS", "Section type already exists: " + request.label());
        }
        var expectsPresenter = request.expectsPresenter() == null || request.expectsPresenter();
        var saved = sectionTypeRepository.save(entity.toBuilder().label(request.label())
                .expectsPresenter(expectsPresenter).icon(request.icon()).accentColor(request.accentColor()).build());
        return toType(saved);
    }

    @Transactional
    public void delete(Long typeId, Long reassignToId) {

        gate.requirePermission(Permission.SECTION_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        var type = sectionTypeRepository.findByOrganizationIdAndId(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Section type not found: " + typeId));

        var inUse = sectionTypeRepository.countStructureRefs(typeId) + sectionTypeRepository.countSectionRefs(typeId);
        if (inUse > 0) {
            if (reassignToId == null) {
                throw new ConflictException("SECTION_TYPE_IN_USE",
                        "Section type is in use; a reassignment target is required to delete it");
            }
            if (reassignToId.equals(typeId)) {
                throw new ConflictException("INVALID_REASSIGNMENT", "Cannot reassign to the deleted type");
            }
            sectionTypeRepository.findByOrganizationIdAndId(organizationId, reassignToId)
                    .orElseThrow(() -> new NotFoundException("Section type not found: " + reassignToId));
            sectionTypeRepository.reassignStructureRefs(typeId, reassignToId);
            sectionTypeRepository.reassignSectionRefs(typeId, reassignToId);
        }

        sectionTypeRepository.delete(type);
    }

    private SectionType toType(SectionTypeEntity e) {

        return SectionType.builder().id(e.getId()).label(e.getLabel()).expectsPresenter(e.isExpectsPresenter())
                .icon(e.getIcon()).accentColor(e.getAccentColor()).build();
    }
}

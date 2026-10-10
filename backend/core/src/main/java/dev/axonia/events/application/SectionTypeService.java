package dev.axonia.events.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.events.api.SectionType;
import dev.axonia.events.api.SectionTypes;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.domain.SectionTypeRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SectionTypeService implements SectionTypes {

    private final SectionTypeStore sectionTypeStore;
    private final OrganizationContext organizationContext;
    private final AuthorizationGate gate;

    @Override
    @Transactional(readOnly = true)
    public List<SectionType> list() {

        // Reading the section-type list is reference data every event-edit flow needs; it is not gated by the admin
        // SECTION_TYPE_READ permission. Mutations below remain gated by SECTION_TYPE_WRITE.
        var organizationId = organizationContext.organizationId();
        return sectionTypeStore.findByOrganizationIdOrderByLabelAsc(organizationId).stream().map(this::toType).toList();
    }

    @Transactional
    public SectionType create(CreateSectionTypeRequest request) {

        gate.requirePermission(Permission.SECTION_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        if (sectionTypeStore.existsByOrganizationIdAndLabel(organizationId, request.label())) {
            throw new ConflictException("SECTION_TYPE_EXISTS", "Section type already exists: " + request.label());
        }
        var expectsPresenter = request.expectsPresenter() == null || request.expectsPresenter();
        var saved = sectionTypeStore.save(SectionTypeRecord.builder().organizationId(organizationId)
                .label(request.label()).expectsPresenter(expectsPresenter).icon(request.icon())
                .accentColor(request.accentColor()).build());
        return toType(saved);
    }

    @Transactional
    public SectionType update(Long typeId, UpdateSectionTypeRequest request) {

        gate.requirePermission(Permission.SECTION_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        var entity = sectionTypeStore.findByOrganizationIdAndId(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Section type not found: " + typeId));
        if (!entity.getLabel().equals(request.label())
                && sectionTypeStore.existsByOrganizationIdAndLabel(organizationId, request.label())) {
            throw new ConflictException("SECTION_TYPE_EXISTS", "Section type already exists: " + request.label());
        }
        var expectsPresenter = request.expectsPresenter() == null || request.expectsPresenter();
        var saved = sectionTypeStore.save(entity.toBuilder().label(request.label()).expectsPresenter(expectsPresenter)
                .icon(request.icon()).accentColor(request.accentColor()).build());
        return toType(saved);
    }

    @Transactional
    public void delete(Long typeId, Long reassignToId) {

        gate.requirePermission(Permission.SECTION_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        var type = sectionTypeStore.findByOrganizationIdAndId(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Section type not found: " + typeId));

        var inUse = sectionTypeStore.countStructureRefs(typeId) + sectionTypeStore.countSectionRefs(typeId);
        if (inUse > 0) {
            if (reassignToId == null) {
                throw new ConflictException("SECTION_TYPE_IN_USE",
                        "Section type is in use; a reassignment target is required to delete it");
            }
            if (reassignToId.equals(typeId)) {
                throw new ConflictException("INVALID_REASSIGNMENT", "Cannot reassign to the deleted type");
            }
            sectionTypeStore.findByOrganizationIdAndId(organizationId, reassignToId)
                    .orElseThrow(() -> new NotFoundException("Section type not found: " + reassignToId));
            sectionTypeStore.reassignStructureRefs(typeId, reassignToId);
            sectionTypeStore.reassignSectionRefs(typeId, reassignToId);
        }

        sectionTypeStore.delete(type);
    }

    private SectionType toType(SectionTypeRecord e) {

        return SectionType.builder().id(e.getId()).label(e.getLabel()).expectsPresenter(e.isExpectsPresenter())
                .icon(e.getIcon()).accentColor(e.getAccentColor()).build();
    }
}

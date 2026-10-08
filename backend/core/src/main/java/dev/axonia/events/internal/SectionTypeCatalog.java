package dev.axonia.events.internal;

import org.springframework.stereotype.Component;

import dev.axonia.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * A section type is a row in this Organization's own catalogue, not an arbitrary number. Shared by every write that
 * carries a {@code sectionTypeId} - {@code SessionService.createOneOff}, {@code SectionService.addSection},
 * {@code SectionService.updateSection}, and {@code SeriesService.toRefs} (a series' default structure) - so the check
 * lives in one place rather than four near-identical copies.
 */
@Component
@RequiredArgsConstructor
public class SectionTypeCatalog {

    private final SectionTypeRepository sectionTypeRepository;

    public void require(String organizationId, Long sectionTypeId) {

        sectionTypeRepository.findByOrganizationIdAndId(organizationId, sectionTypeId)
                .orElseThrow(() -> new NotFoundException("Section type not found: " + sectionTypeId));
    }
}

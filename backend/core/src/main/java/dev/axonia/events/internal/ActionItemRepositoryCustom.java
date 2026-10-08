package dev.axonia.events.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

interface ActionItemRepositoryCustom {

    Page<ActionItemEntity> findFiltered(String organizationId, Long communityId, ActionItemFilter filter,
            Pageable pageable);
}

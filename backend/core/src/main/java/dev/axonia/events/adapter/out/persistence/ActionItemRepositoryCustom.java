package dev.axonia.events.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.events.application.ActionItemFilter;

interface ActionItemRepositoryCustom {

    Page<ActionItemEntity> findFiltered(String organizationId, Long communityId, ActionItemFilter filter,
            Pageable pageable);
}

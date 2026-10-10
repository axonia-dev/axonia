package dev.axonia.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.organization.api.OrganizationRegistration;
import dev.axonia.organization.application.port.out.OrganizationStore;
import dev.axonia.organization.domain.RegistryEntry;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcOrganizationStore implements OrganizationStore {

    private final OrganizationRepository repository;

    @Override
    @Transactional
    public RegistryEntry register(OrganizationRegistration registration) {
        return toEntry(repository.save(OrganizationEntity.builder().id(registration.id()).name(registration.name())
                .kind(registration.kind()).isNew(true).build()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String id) {
        return repository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegistryEntry> find(String id) {
        return repository.findById(id).map(JdbcOrganizationStore::toEntry);
    }

    @Override
    @Transactional
    public RegistryEntry update(RegistryEntry entry) {
        return toEntry(repository.save(toEntity(entry)));
    }

    @Override
    @Transactional
    public int setCommunicationsEnabled(String id, boolean enabled, Instant updatedAt) {
        return repository.updateCommunicationsEnabled(id, enabled, updatedAt);
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    static RegistryEntry toEntry(OrganizationEntity entity) {
        return RegistryEntry.builder().id(entity.getId()).name(entity.getName()).kind(entity.getKind())
                .lifecycleState(entity.getLifecycleState()).closedAt(entity.getClosedAt())
                .closedBy(entity.getClosedBy()).communicationsEnabled(entity.isCommunicationsEnabled())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).build();
    }

    static OrganizationEntity toEntity(RegistryEntry entry) {
        return OrganizationEntity.builder().id(entry.id()).name(entry.name()).kind(entry.kind())
                .lifecycleState(entry.lifecycleState()).closedAt(entry.closedAt()).closedBy(entry.closedBy())
                .communicationsEnabled(entry.communicationsEnabled()).createdAt(entry.createdAt())
                .updatedAt(entry.updatedAt()).build();
    }
}

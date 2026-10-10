package dev.axonia.roundup.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.roundup.application.port.out.RoundupSettingsStore;
import dev.axonia.roundup.domain.RoundupSettings;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcRoundupSettingsStore implements RoundupSettingsStore {

    private final RoundupSettingsRepository roundupSettingsRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<RoundupSettings> find(String organizationId) {

        return roundupSettingsRepository.findById(organizationId)
                .map(entity -> new RoundupSettings(organizationId, entity.isEnabled()));
    }

    @Override
    @Transactional
    public RoundupSettings save(String organizationId, boolean enabled) {

        var entity = roundupSettingsRepository.findById(organizationId)
                .map(existing -> existing.toBuilder().enabled(enabled).build()).orElseGet(() -> RoundupSettingsEntity
                        .builder().organizationId(organizationId).enabled(enabled).isNew(true).build());
        var saved = roundupSettingsRepository.save(entity);
        return new RoundupSettings(saved.getOrganizationId(), saved.isEnabled());
    }
}

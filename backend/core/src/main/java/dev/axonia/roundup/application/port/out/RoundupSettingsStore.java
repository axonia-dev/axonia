package dev.axonia.roundup.application.port.out;

import java.util.Optional;

import dev.axonia.roundup.domain.RoundupSettings;

public interface RoundupSettingsStore {

    /** Empty when the organization never touched the setting. */
    Optional<RoundupSettings> find(String organizationId);

    /** Switches the organization's roundup on or off, creating its row the first time. */
    RoundupSettings save(String organizationId, boolean enabled);
}

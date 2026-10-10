package dev.axonia.installation.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.installation.domain.MailFieldChange;
import dev.axonia.installation.domain.SettingsChangeEntry;

/** The mail integration's recorded settings changes, newest first. */
public interface SettingsChangeLog {

    void append(String actor, Instant at, List<MailFieldChange> changes);

    Optional<SettingsChangeEntry> latest();

    Page<SettingsChangeEntry> page(Pageable pageable);
}

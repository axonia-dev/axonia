package dev.axonia.installation.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import dev.axonia.installation.domain.SettingsKeys;

/** Bound as text and parsed by {@link SettingsKeys}, so a malformed key never reaches a bind error that prints it. */
@ConfigurationProperties("axonia.installation")
record InstallationProperties(String settingsKey, String previousSettingsKeys) {

    @Override
    public String toString() {

        return "InstallationProperties[settingsKey=<hidden>, previousSettingsKeys=<hidden>]";
    }
}

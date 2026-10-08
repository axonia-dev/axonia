package dev.axonia.installation.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound as text and parsed by {@link SettingsKeys}, so a malformed key never reaches a bind error that prints it. */
@ConfigurationProperties("axonia.installation")
record InstallationProperties(String settingsKey, String previousSettingsKeys) {

    @Override
    public String toString() {

        return "InstallationProperties[settingsKey=<hidden>, previousSettingsKeys=<hidden>]";
    }
}

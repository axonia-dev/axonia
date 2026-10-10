package dev.axonia.installation.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.installation.domain.MailServerOverlay;
import dev.axonia.installation.domain.SettingsKeys;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({InstallationProperties.class, MailServerOverlayProperties.class})
class InstallationConfiguration {

    @Bean
    SettingsKeys settingsKeys(InstallationProperties properties) {

        return SettingsKeys.parse(properties.settingsKey(), properties.previousSettingsKeys());
    }

    @Bean
    MailServerOverlay mailServerOverlay(MailServerOverlayProperties properties) {

        return MailServerOverlay.from(properties);
    }
}

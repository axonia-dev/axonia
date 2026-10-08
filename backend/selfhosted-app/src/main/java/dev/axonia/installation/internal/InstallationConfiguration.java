package dev.axonia.installation.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

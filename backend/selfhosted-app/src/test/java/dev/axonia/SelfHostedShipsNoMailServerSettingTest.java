package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No configuration file the Self-hosted application reads sets a mail server: an installation's mail server is the card
 * or the deployment's AXONIA_MAIL_* variables, never a value shipped with it.
 */
class SelfHostedShipsNoMailServerSettingTest {

    /** Every file application.properties imports, and the profile files beside them. */
    private static final List<String> FILES = List.of("application.properties", "axonia-core.properties",
            "axonia-selfhosted.properties", "application-DEV.properties", "application-PROD.properties");

    @Test
    void noConfigurationFileOfTheApplicationSetsAMailServer() {

        // when
        var shipped = FILES.stream().flatMap(name -> resources(name).stream())
                .filter(url -> !url.toString().contains("test-classes") && !url.toString().contains("-tests.jar"))
                .toList();

        // then
        assertThat(shipped).extracting(URL::toString).anyMatch(url -> url.endsWith("/axonia-core.properties"))
                .anyMatch(url -> url.endsWith("/axonia-selfhosted.properties"));
        assertThat(shipped).allSatisfy(url -> assertThat(load(url).stringPropertyNames()).as(url.toString())
                .noneMatch(key -> key.startsWith("spring.mail.") || key.startsWith("axonia.mail.")));
    }

    private static List<URL> resources(String name) {

        try {
            return Collections.list(SelfHostedShipsNoMailServerSettingTest.class.getClassLoader().getResources(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties load(URL url) {

        var properties = new Properties();
        try (var in = url.openStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}

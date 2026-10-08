package dev.axonia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * An installation started with none of its variables set refuses to start before anything reaches the database, and
 * names every variable it is missing at once rather than the first one an auto-configuration happens to read. The
 * application is started without the machine's environment variables, so a shell that exports one of them changes
 * nothing here.
 */
class SelfHostedStartRefusalTest {

    @Test
    void anInstallationWithNoVariablesSetRefusesToStartAndNamesThem() {

        // when
        var actual = catchThrowable(() -> new SpringApplicationBuilder(AxoniaSelfHostedApplication.class)
                .environment(new WithoutEnvironmentVariables()).run("--server.port=0"));

        // then
        assertThat(actual).isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Settings that name a variable which is not set: ")
                .hasMessageContaining("DATABASE_URL").hasMessageContaining("DATABASE_PASSWORD")
                .hasMessageContaining("DATABASE_OWNER_USERNAME").hasMessageContaining("DATABASE_OWNER_PASSWORD")
                .hasMessageContaining("DATABASE_COMMUNICATION_READ_PASSWORD")
                .hasMessageContaining("AXONIA_EDGE_SHARED_SECRETS").hasMessageContaining("AXONIA_PUBLIC_BASE_URL")
                .hasMessageContaining("AXONIA_UNSUBSCRIBE_SECRET").hasMessageContaining("AXONIA_SETTINGS_KEY")
                .hasMessageNotContaining("AXONIA_SETTINGS_PREVIOUS_KEYS");
    }

    /** The JVM's system properties, as an application always has them, and no environment variable at all. */
    private static final class WithoutEnvironmentVariables extends StandardEnvironment {

        @Override
        protected void customizePropertySources(MutablePropertySources propertySources) {

            propertySources
                    .addLast(new MapPropertySource(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, getSystemProperties()));
        }
    }
}

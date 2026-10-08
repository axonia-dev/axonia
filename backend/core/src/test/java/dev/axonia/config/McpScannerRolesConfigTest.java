package dev.axonia.config;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds {@link McpScannerRolesConfig} against the real context, because the whole point of that class is what the
 * container ends up believing about beans nobody in this repository declares.
 *
 * <p>
 * The Flyway converter is the reason this test exists. Three of the four definitions are selected by class literal, so
 * an upstream rename fails compilation; that one is matched by name, and a rename there would leave the post-processor
 * quietly doing nothing while the warning it exists to remove came back. Asserting the definition is still there
 * separates "upstream moved" from "upstream fixed it", which is what decides whether the FIXME can go.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class McpScannerRolesConfigTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void marksTheBeansTheMcpAnnotationScannerCreatesTooEarlyAsInfrastructure() {

        ConfigurableListableBeanFactory factory = context.getBeanFactory();

        assertThat(rolesOf(factory, McpServerAnnotationScannerAutoConfiguration.class)).isNotEmpty()
                .containsOnly(BeanDefinition.ROLE_INFRASTRUCTURE);
        assertThat(rolesOf(factory, McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans.class))
                .isNotEmpty().containsOnly(BeanDefinition.ROLE_INFRASTRUCTURE);
        assertThat(rolesOf(factory, McpServerAnnotationScannerProperties.class)).isNotEmpty()
                .containsOnly(BeanDefinition.ROLE_INFRASTRUCTURE);
    }

    @Test
    void marksTheFlywayConverterThatEarlyPropertyBindingDragsInAsInfrastructure() {

        ConfigurableListableBeanFactory factory = context.getBeanFactory();

        assertThat(factory.containsBeanDefinition(McpScannerRolesConfig.FLYWAY_VERSION_CONVERTER)).isTrue();
        assertThat(factory.getBeanDefinition(McpScannerRolesConfig.FLYWAY_VERSION_CONVERTER).getRole())
                .isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
    }

    private static int[] rolesOf(ConfigurableListableBeanFactory factory, Class<?> type) {

        return Arrays.stream(factory.getBeanNamesForType(type, true, false))
                .mapToInt(name -> factory.getBeanDefinition(name).getRole()).toArray();
    }
}

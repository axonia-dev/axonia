package dev.axonia.shared.configuration;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerProperties;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Silences four {@code BeanPostProcessorChecker} warnings that Spring AI's MCP annotation scanner emits on every boot.
 *
 * <p>
 * Spring AI declares {@code serverAnnotatedMethodBeanPostProcessor} as a static {@code @Bean}, which is correct, but
 * gives it two parameters that are not: {@code ServerMcpAnnotatedBeans} comes from a non-static factory method, so
 * building the post-processor instantiates the auto-configuration class itself, and
 * {@code McpServerAnnotationScannerProperties} has to be bound at the same moment, which in turn pulls in Flyway's
 * {@code stringOrNumberMigrationVersionConverter} because binding needs every {@code @ConfigurationPropertiesBinding}
 * converter. All four are created before the remaining post-processors are registered, and Spring warns about each one.
 *
 * <p>
 * Nothing is actually broken: none of the four needs post-processing, and the properties bean is still bound because
 * the binder's own post-processor is {@code PriorityOrdered} and therefore already in place. So this fixes the report,
 * not the behaviour. Marking the definitions {@code ROLE_INFRASTRUCTURE} is the remedy the warning text itself names,
 * and it forks no upstream logic: only the role of a bean definition changes, never how a bean is built. Boot logs are
 * worth keeping actionable, and four unavoidable warnings train readers to skim past the fifth.
 *
 * <p>
 * FIXME: remove this class once spring-projects/spring-ai#4694 is released. The three types are referenced by class
 * literal, so an upstream rename breaks the build rather than passing silently; the Flyway converter is matched by
 * name, which {@code McpScannerRolesConfigTest} holds.
 */
@Configuration(proxyBeanMethods = false)
class McpScannerRolesConfig {

    private static final List<Class<?>> EAGERLY_CREATED = List.of(McpServerAnnotationScannerAutoConfiguration.class,
            McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans.class,
            McpServerAnnotationScannerProperties.class);

    static final String FLYWAY_VERSION_CONVERTER = "stringOrNumberMigrationVersionConverter";

    @Bean
    static BeanFactoryPostProcessor mcpScannerBeanRoles() {

        return factory -> {
            EAGERLY_CREATED.stream().flatMap(type -> Stream.of(factory.getBeanNamesForType(type, true, false)))
                    .forEach(name -> markInfrastructure(factory, name));
            markInfrastructure(factory, FLYWAY_VERSION_CONVERTER);
        };
    }

    private static void markInfrastructure(ConfigurableListableBeanFactory factory, String beanName) {

        if (factory.containsBeanDefinition(beanName)) {
            factory.getBeanDefinition(beanName).setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        }
    }
}

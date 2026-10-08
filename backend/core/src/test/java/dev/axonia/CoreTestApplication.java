package dev.axonia;

import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The application every open Spring test names, standing in for an edition's application class, with the open modules
 * only.
 *
 * <p>
 * It is {@code @Configuration} plus {@code @TestComponent} rather than {@code @TestConfiguration}: the test context
 * bootstrapper keeps a declared class only when it is not a {@code @TestConfiguration}, and the component scan skips
 * any {@code @TestComponent}, so this class is used where a test names it and discovered nowhere.
 */
@Configuration(proxyBeanMethods = false)
@TestComponent
@EnableAutoConfiguration
@EnableScheduling
@ComponentScan(basePackages = "dev.axonia", excludeFilters = {
        @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)})
public class CoreTestApplication {
}

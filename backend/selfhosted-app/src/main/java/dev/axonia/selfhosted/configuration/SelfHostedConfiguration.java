package dev.axonia.selfhosted.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SelfHostedProperties.class)
class SelfHostedConfiguration {
}

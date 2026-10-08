package dev.axonia.selfhosted.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import dev.axonia.selfhosted.SelfHostedProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SelfHostedProperties.class)
class SelfHostedConfiguration {
}

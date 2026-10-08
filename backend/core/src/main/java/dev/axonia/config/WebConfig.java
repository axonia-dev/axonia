package dev.axonia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Serializes {@code Page} responses through Spring Data's stable {@code PagedModel} DTO instead of the as-is
 * {@code PageImpl}, whose JSON structure carries no stability guarantee across versions.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {
}

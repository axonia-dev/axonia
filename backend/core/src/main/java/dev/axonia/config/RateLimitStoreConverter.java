package dev.axonia.config;

import org.springframework.core.convert.converter.Converter;

import dev.axonia.config.RateLimitProperties.Store;

/**
 * Registered with {@code @ConfigurationPropertiesBinding} in {@link RateLimitConfig} so the binder calls this instead
 * of falling through to the JDK's own {@code Enum.valueOf}, whose failure names only the enum class and the rejected
 * value - not the choices that would have worked.
 */
class RateLimitStoreConverter implements Converter<String, Store> {

    @Override
    public Store convert(String source) {

        return RateLimitProperties.parse(source);
    }
}

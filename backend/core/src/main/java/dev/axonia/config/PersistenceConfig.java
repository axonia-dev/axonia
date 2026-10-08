package dev.axonia.config;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.zaxxer.hikari.HikariDataSource;

import dev.axonia.CurrentToken;
import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.OrganizationResolver;
import dev.axonia.SubjectAuthentication;
import dev.axonia.SubjectResolver;

@Configuration
@EnableJdbcAuditing
public class PersistenceConfig {

    @Bean
    OrganizationResolver organizationResolver() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            // SCIM and any other non-JWT auth that carries its own Organization (e.g. ScimAuthenticationToken).
            // Checked first so the config module never imports identity-specific types.
            if (auth instanceof OrganizationBoundAuthentication oba) {
                return Optional.of(oba.organizationId());
            }
            if (auth instanceof JwtAuthenticationToken) {
                return CurrentToken.resolvedOrganization();
            }
            return Optional.empty();
        };
    }

    @Bean
    SubjectResolver subjectResolver() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                return Optional.ofNullable(jwt.getToken().getSubject());
            }
            if (auth instanceof SubjectAuthentication subject) {
                return Optional.of(subject.subject());
            }
            return Optional.empty();
        };
    }

    // Spring Boot resolves a dialect only when no JdbcDialect bean exists, so this one serves every edition and every
    // test context that holds this configuration.
    @Bean
    JdbcDialect jdbcDialect() {
        return new KeyReturningPostgresDialect();
    }

    // Split into three beans so spring.datasource.hikari.* actually binds: a custom DataSource
    // @Bean replaces Boot's auto-configured HikariDataSource, and Boot only binds those
    // properties onto the auto-configured bean - bound directly here instead, otherwise
    // they're silently discarded and every pool defaults to Hikari's built-in size. The
    // DataSourceProperties bean is explicit (rather than @EnableConfigurationProperties) so it
    // can be qualified: a second datasource elsewhere means type-only autowiring
    // is ambiguous everywhere a DataSourceProperties or HikariDataSource is injected.
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    HikariDataSource hikariDataSource(@Qualifier("dataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    DataSource dataSource(OrganizationResolver resolver, SubjectResolver subjects,
            @Qualifier("hikariDataSource") HikariDataSource hikari) {
        return new OrganizationAwareDataSource(hikari, resolver, subjects);
    }
}

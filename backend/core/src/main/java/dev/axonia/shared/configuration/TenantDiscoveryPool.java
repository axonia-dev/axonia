package dev.axonia.shared.configuration;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.zaxxer.hikari.HikariDataSource;

/**
 * The privileged read datasource backing {@code JdbcOrganizationIds} (step 1 of every scheduled tick): a deliberately
 * tiny second pool, sized for background work, for the narrow cross-organization reads of the {@code organization}
 * table. Discovering which organizations exist is what it was built for and still its bulk;
 * {@code OrganizationIds#kindOf} also reads one organization's kind on the request path, which is why the pool is small
 * rather than single-connection. Anything heavier than that belongs on the primary datasource. This one is built by
 * pure property binding, because Boot auto-configures only one datasource and Hikari properties only bind onto a bean
 * built this way.
 *
 * <p>
 * NOT wrapped in {@code OrganizationAwareDataSource}: this pool crosses organizations by design, the opposite of what
 * that wrapper prevents. The startup {@code RlsRuntimeRoleCheck} watches only the primary datasource, so this pool
 * never trips it.
 *
 * <p>
 * The database role remains {@code axonia_communication_read}, created by V30 and named before this became shared
 * infrastructure. Renaming it would mean a migration and a production secret rotation to fix a name, so the property
 * keys generalized and the role did not.
 *
 * <p>
 * Public, because {@code CommunicationJdbcTestSupport} lives outside this package and {@code @Import}s this class
 * directly to get {@code JdbcOrganizationIds}' real privileged pool in its {@code @DataJdbcTest} slice, rather than a
 * mock.
 */
@Configuration
public class TenantDiscoveryPool {

    @Bean
    @ConfigurationProperties(prefix = "axonia.tenant-discovery.datasource")
    DataSourceProperties tenantDiscoveryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "axonia.tenant-discovery.datasource.hikari")
    HikariDataSource tenantDiscoveryDataSource(
            @Qualifier("tenantDiscoveryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    JdbcClient tenantDiscoveryJdbcClient(@Qualifier("tenantDiscoveryDataSource") DataSource tenantDiscoveryDataSource) {
        return JdbcClient.create(tenantDiscoveryDataSource);
    }
}

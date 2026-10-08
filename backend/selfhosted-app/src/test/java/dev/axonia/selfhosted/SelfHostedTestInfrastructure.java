package dev.axonia.selfhosted;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;

import dev.axonia.AxoniaSelfHostedApplication;

/**
 * Points the Self-hosted application at {@link SelfHostedDatabase} and {@link MockOidcIssuer}, and names the one
 * Organization every Self-hosted test runs as. An initializer ordered first rather than dynamic test properties,
 * because the application's {@code ResolvedConfigurationGuard} is an initializer ordered last that reads the settings
 * before the context refreshes, and dynamic properties arrive only after that. Its settings rank just below a test's
 * own {@code properties}, so a test can still change one.
 */
public class SelfHostedTestInfrastructure
        implements
            ApplicationContextInitializer<ConfigurableApplicationContext>,
            Ordered {

    public static final String ORGANIZATION_ID = "acme";

    public static final String ORGANIZATION_NAME = "Acme";

    public static final String OWNER_GROUP = "axonia-owners";

    public static final String EDGE_SECRET = "local-dev-edge-secret";

    private static final String SOURCE = "selfHostedTestInfrastructure";

    private static final String INLINED = TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME;

    @Override
    public void initialize(ConfigurableApplicationContext context) {

        var sources = context.getEnvironment().getPropertySources();
        var infrastructure = new MapPropertySource(SOURCE, new HashMap<>(settings()));
        if (sources.contains(INLINED)) {
            sources.addAfter(INLINED, infrastructure);
        } else {
            sources.addFirst(infrastructure);
        }
        context.getBeanFactory().registerSingleton("mockOidcIssuer", MockOidcIssuer.shared());
    }

    @Override
    public int getOrder() {

        return Ordered.HIGHEST_PRECEDENCE;
    }

    /** Every setting the initializer applies, for a test that starts an application of its own with one changed. */
    public static Map<String, String> settings() {

        var database = SelfHostedDatabase.shared();
        var issuer = MockOidcIssuer.shared();
        var settings = new HashMap<String, String>();
        settings.put("spring.flyway.url", database.getJdbcUrl());
        settings.put("spring.flyway.user", database.getUsername());
        settings.put("spring.flyway.password", database.getPassword());
        settings.put("spring.datasource.url", database.getJdbcUrl());
        settings.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        settings.put("spring.datasource.username", SelfHostedDatabase.APP_ROLE);
        settings.put("spring.datasource.password", SelfHostedDatabase.APP_PASSWORD);
        settings.put("axonia.tenant-discovery.datasource.url", database.getJdbcUrl());
        settings.put("axonia.tenant-discovery.datasource.driver-class-name", "org.postgresql.Driver");
        settings.put("axonia.tenant-discovery.datasource.username", SelfHostedDatabase.DISCOVERY_ROLE);
        settings.put("axonia.tenant-discovery.datasource.password", SelfHostedDatabase.DISCOVERY_PASSWORD);
        settings.put("axonia.selfhosted.organization.id", ORGANIZATION_ID);
        settings.put("axonia.selfhosted.organization.name", ORGANIZATION_NAME);
        settings.put("axonia.selfhosted.owner-group", OWNER_GROUP);
        settings.put("axonia.selfhosted.oidc.issuer-uri", issuer.issuerUri());
        settings.put("axonia.selfhosted.oidc.audience", MockOidcIssuer.AUDIENCE);
        settings.put("axonia.organization.allow-default-fallback", "false");
        return Map.copyOf(settings);
    }

    /**
     * Starts a second Self-hosted application on the same database and issuer, with {@code overrides} applied over
     * {@link #settings()}, and hands it to the caller to close. The settings travel as command-line arguments, which
     * outrank {@code application.properties}; default properties would not. It serves on a random loopback port,
     * because the security chains need a servlet context.
     */
    public static ConfigurableApplicationContext start(Map<String, String> overrides) {

        var merged = new HashMap<>(settings());
        merged.put("server.port", "0");
        merged.putAll(overrides);
        var arguments = merged.entrySet().stream().map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(AxoniaSelfHostedApplication.class).profiles("DEV")
                .web(WebApplicationType.SERVLET).run(arguments);
    }
}

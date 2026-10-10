package dev.axonia.selfhosted.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import dev.axonia.RepositoryRoot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The DEV profile, read as the application reads it, in the same order: the profile file over the Self-hosted settings
 * over the shared ones. The test classpath's own application-DEV.properties shadows this one in every Spring test, so
 * nothing else would notice a DEV line that stopped resolving until a developer's run refused to start.
 */
class SelfHostedDevProfileTest {

    private static final StandardEnvironment DEV = devEnvironment();

    @Test
    void namesEveryVariableTheApplicationNeeds() {

        assertThat(ResolvedConfigurationGuard.unresolvedSettings(DEV)).isEmpty();
    }

    @Test
    void foundsTheLocalOrganizationWithItsOwnersGroup() {

        new ApplicationContextRunner().withUserConfiguration(SelfHostedConfiguration.class)
                .withInitializer(context -> DEV.getPropertySources()
                        .forEach(source -> context.getEnvironment().getPropertySources().addLast(source)))
                .run(context -> {
                    var properties = context.getBean(SelfHostedProperties.class);
                    assertThat(properties.organization().id()).isEqualTo("axonia-local");
                    assertThat(properties.ownerGroup()).isEqualTo("axonia-owners");
                    assertThat(properties.oidc().issuerUri()).isEqualTo("http://localhost:8091/axonia");
                    assertThat(properties.oidc().audience()).isEqualTo("axonia-local");
                });
    }

    @Test
    void runsBesideTheCloudsDevRunOnPortsOfItsOwn() {

        assertThat(DEV.getProperty("server.port")).isEqualTo("8090");
        assertThat(DEV.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5433/axonia");
        assertThat(DEV.getProperty("spring.flyway.url")).isEqualTo(DEV.getProperty("spring.datasource.url"));
        assertThat(DEV.getProperty("axonia.tenant-discovery.datasource.url"))
                .isEqualTo(DEV.getProperty("spring.datasource.url"));
    }

    @Test
    void keepsTheEdgeGateOnWithTheSecretTheDevServerStamps() {

        assertThat(DEV.getProperty("axonia.edge.gate-enabled")).isEqualTo("true");
        assertThat(DEV.getProperty("axonia.edge.shared-secrets")).isEqualTo("local-dev-edge-secret");
    }

    /** Run from the {@code backend} directory, as the IDE configuration and {@code spring-boot:run} both are. */
    @Test
    void startsTheServicesOfTheDevComposeFile() {

        assertThat(DEV.getProperty("spring.docker.compose.enabled")).isEqualTo("true");
        assertThat(DEV.getProperty("spring.docker.compose.file")).isEqualTo("../deploy/dev/compose.yaml");
        assertThat(RepositoryRoot.file("backend").resolve(DEV.getProperty("spring.docker.compose.file")))
                .isRegularFile();
    }

    @Test
    void neverFallsBackToAnOrganization() {

        assertThat(DEV.getProperty("axonia.organization.allow-default-fallback", "false")).isEqualTo("false");
    }

    /**
     * The dev server of {@code pnpm dev:selfhosted} and this run must agree on both addresses and on the identity
     * provider.
     */
    @Test
    void agreesWithTheSelfHostedDevServer() {

        var devServer = load(new FileSystemResource(RepositoryRoot.file("frontend/.env.selfhosted")));

        assertThat(devServer.getProperty("DEV_BACKEND_ORIGIN"))
                .isEqualTo("http://localhost:" + DEV.getProperty("server.port"));
        assertThat(DEV.getProperty("axonia.public-base-url"))
                .isEqualTo("http://localhost:" + devServer.getProperty("DEV_PORT"));
        assertThat(devServer.getProperty("DEV_OIDC_CLIENT_ID"))
                .isEqualTo(DEV.getProperty("axonia.selfhosted.oidc.audience"));
        assertThat(devServer.getProperty("DEV_OIDC_ISSUER"))
                .isEqualTo(DEV.getProperty("axonia.selfhosted.oidc.issuer-uri"));
    }

    private static StandardEnvironment devEnvironment() {

        var environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        List.of(new FileSystemResource(Path.of("src/main/resources/application-DEV.properties")),
                new FileSystemResource(Path.of("src/main/resources/axonia-selfhosted.properties")),
                new ClassPathResource("axonia-core.properties"),
                new FileSystemResource(Path.of("src/main/resources/application.properties")))
                .forEach(file -> loaded(file).forEach(sources::addLast));
        return environment;
    }

    private static List<PropertySource<?>> loaded(Resource file) {

        try {
            return new PropertiesPropertySourceLoader().load(file.getDescription(), file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties load(Resource file) {

        var properties = new Properties();
        try (var in = file.getInputStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}

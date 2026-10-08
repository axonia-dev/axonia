package dev.axonia.selfhosted;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.support.TestPropertySourceUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SelfHostedTestInfrastructureTest {

    @Test
    void runsBeforeEveryOtherInitializer() {

        assertThat(new SelfHostedTestInfrastructure().getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void pointsTheApplicationAtItsOwnDatabaseAsItsOwnRolesAndAtTheIssuer() {

        // when
        var settings = SelfHostedTestInfrastructure.settings();

        // then
        assertThat(settings).containsEntry("spring.datasource.url", SelfHostedDatabase.shared().getJdbcUrl())
                .containsEntry("axonia.organization.allow-default-fallback", "false")
                .containsEntry("spring.datasource.username", "axonia_app")
                .containsEntry("axonia.tenant-discovery.datasource.username", "axonia_communication_read")
                .containsEntry("spring.flyway.user", SelfHostedDatabase.shared().getUsername())
                .containsEntry("axonia.selfhosted.oidc.issuer-uri", MockOidcIssuer.shared().issuerUri())
                .containsEntry("axonia.selfhosted.oidc.audience", MockOidcIssuer.AUDIENCE);
    }

    @Test
    void theRolesConnectWithThePasswordsTheMigrationGivesThem() throws IOException {

        // given
        var shared = new Properties();
        try (var in = new ClassPathResource("axonia-core-test.properties").getInputStream()) {
            shared.load(in);
        }

        // then
        assertThat(shared.getProperty("spring.flyway.placeholders.app-password"))
                .isEqualTo(SelfHostedDatabase.APP_PASSWORD);
        assertThat(shared.getProperty("spring.flyway.placeholders.communication-read-password"))
                .isEqualTo(SelfHostedDatabase.DISCOVERY_PASSWORD);
    }

    @Test
    void aTestsOwnPropertiesOutrankTheInfrastructure() {

        // given
        var context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource(TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME,
                        Map.of("axonia.selfhosted.owner-group", "another-group")));

        // when
        new SelfHostedTestInfrastructure().initialize(context);

        // then
        assertThat(context.getEnvironment().getProperty("axonia.selfhosted.owner-group")).isEqualTo("another-group");
        assertThat(context.getEnvironment().getProperty("axonia.selfhosted.organization.id"))
                .isEqualTo(SelfHostedTestInfrastructure.ORGANIZATION_ID);
    }

    @Test
    void outranksTheConfigurationFilesAndRegistersTheIssuer() {

        // given
        var context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources()
                .addLast(new MapPropertySource("files", Map.of("axonia.selfhosted.owner-group", "from-a-file")));

        // when
        new SelfHostedTestInfrastructure().initialize(context);

        // then
        assertThat(context.getEnvironment().getProperty("axonia.selfhosted.owner-group"))
                .isEqualTo(SelfHostedTestInfrastructure.OWNER_GROUP);
        assertThat(context.getBeanFactory().getBean(MockOidcIssuer.class)).isSameAs(MockOidcIssuer.shared());
    }
}

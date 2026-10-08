package dev.axonia;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactoryInformation;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.ClassUtils;

import dev.axonia.communication.MailServer;
import dev.axonia.health.HealthController;
import dev.axonia.health.HealthService;
import dev.axonia.identity.IdentityAccounts;
import dev.axonia.identity.OrganizationCardLookSource;
import dev.axonia.identity.OrganizationIdpResolver;
import dev.axonia.identity.OrganizationMembershipSource;
import dev.axonia.identity.SignInObserver;
import dev.axonia.identity.SignInProviderResolver;
import dev.axonia.identity.SuccessionAddresses;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what {@link CoreTestApplication} gives each kind of open test: a web slice stays a web slice, a JDBC slice stays
 * a JDBC slice, and a full context holds the open modules with the closed ports stood in, never the Cloud application.
 * In each, every bean of a {@code dev.axonia} type is one core compiles, repository interfaces included.
 */
class CoreTestConfigurationTest {

    private static final List<Path> OUTPUT_DIRECTORIES = List.of(Path.of("target", "classes"),
            Path.of("target", "test-classes"));

    private static final Set<Class<?>> STOOD_IN_PORTS = Set.of(SuccessionAddresses.class, SignInProviderResolver.class,
            SignInObserver.class, PaidCapability.class, OrganizationMembershipSource.class,
            OrganizationIdpResolver.class, OrganizationCardLookSource.class, IdentityAccounts.class, MailServer.class);

    @Nested
    @CoreWebMvcTest(controllers = HealthController.class)
    class AWebSlice extends ControllerTestSupport {

        @Autowired
        private ApplicationContext context;

        @MockitoBean
        private HealthService healthService;

        @Test
        void holdsTheControllerAndNeitherARepositoryNorAStandIn() {

            // when
            var foreign = foreignBeansOf(context);

            // then
            assertThat(context.getBeansOfType(HealthController.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(Repository.class)).isEmpty();
            assertThat(context.getBeanNamesForType(ClosedPortStandIns.class)).isEmpty();
            assertThat(foreign).as("beans of an open web slice that core does not compile").isEmpty();
        }
    }

    @Nested
    class AJdbcSlice extends CoreJdbcTestSupport {

        @Autowired
        private ApplicationContext context;

        @Test
        void holdsRepositoriesAndNoControllerAndNoClosedComponent() {

            // when
            var foreign = foreignBeansOf(context);

            // then
            assertThat(context.getBeanNamesForType(Repository.class)).isNotEmpty();
            assertThat(context.getBeanNamesForAnnotation(Controller.class)).isEmpty();
            assertThat(foreign).as("beans of an open JDBC slice that core does not compile").isEmpty();
        }
    }

    @Nested
    @CoreSpringBootTest
    @ActiveProfiles("DEV")
    class AFullContext {

        @Autowired
        private ApplicationContext context;

        @Test
        void holdsTheStandInsAndNothingOfTheCloudApplication() {

            // when
            var foreign = foreignBeansOf(context);

            // then
            assertThat(context.getBeanNamesForType(ClosedPortStandIns.class)).hasSize(1);
            STOOD_IN_PORTS.forEach(port -> assertThat(context.getBeanNamesForType(port))
                    .as("beans implementing %s", port.getSimpleName()).hasSize(1));
            assertThat(context.getBeanNamesForAnnotation(SpringBootConfiguration.class)).isEmpty();
            assertThat(foreign).as("beans of an open full context that core does not compile").isEmpty();
        }
    }

    /**
     * Every bean whose type lies under {@code dev.axonia} but is not compiled by this build unit, a Spring Data
     * repository read by its interface rather than its proxy, and a lambda by the class that defines it. Nothing here
     * names another build unit's packages: a type belongs to core when its class file is in core's own output.
     */
    private static Set<String> foreignBeansOf(ApplicationContext context) {

        var foreign = new TreeSet<String>();
        for (var name : context.getBeanDefinitionNames()) {
            var type = typeOf(context, name);
            if (type != null && type.startsWith("dev.axonia.") && !compiledByThisBuildUnit(type)) {
                foreign.add(name + " (" + type + ")");
            }
        }
        return foreign;
    }

    private static String typeOf(ApplicationContext context, String name) {

        if (context.containsBean("&" + name)
                && context.getBean("&" + name) instanceof RepositoryFactoryInformation<?, ?> factory) {
            return factory.getRepositoryInformation().getRepositoryInterface().getName();
        }
        var type = context.getType(name);
        if (type == null) {
            return null;
        }
        var userType = ClassUtils.getUserClass(type);
        return (userType.isHidden() ? userType.getNestHost() : userType).getName();
    }

    private static boolean compiledByThisBuildUnit(String typeName) {

        var classFile = typeName.replace('.', '/') + ".class";
        return OUTPUT_DIRECTORIES.stream().anyMatch(directory -> Files.isRegularFile(directory.resolve(classFile)));
    }
}

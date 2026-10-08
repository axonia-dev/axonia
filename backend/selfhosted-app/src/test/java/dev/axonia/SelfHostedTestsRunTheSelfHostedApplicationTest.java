package dev.axonia;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.BootstrapUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every Spring test selfhosted-app compiles runs on {@link AxoniaSelfHostedApplication}, never on core's
 * {@link CoreTestApplication} or its stand-ins for the closed ports: a Self-hosted test on the stand-ins would prove
 * the stand-ins, not the Self-hosted adapters. The Self-hosted twin of {@code CloudTestsRunTheCloudApplicationTest}.
 */
class SelfHostedTestsRunTheSelfHostedApplicationTest {

    @Test
    void everySelfHostedSpringTestResolvesTheSelfHostedApplication() {

        // given
        var tests = selfHostedTests();

        // when
        var elsewhere = tests.stream()
                .filter(type -> !List.of(BootstrapUtils.resolveTestContextBootstrapper(type)
                        .buildMergedContextConfiguration().getClasses()).contains(AxoniaSelfHostedApplication.class))
                .map(Class::getName).sorted().toList();

        // then
        assertThat(tests).as("the scan sees the Self-hosted Spring tests").isNotEmpty();
        assertThat(elsewhere)
                .as("Self-hosted Spring tests not on AxoniaSelfHostedApplication: use @SelfHostedSpringBootTest")
                .isEmpty();
    }

    @Test
    void noSelfHostedTestInheritsTheCoreSpringBootTestOrTheStandIns() {

        // when
        var inheriting = selfHostedTests().stream().filter(CompiledTestClasses::inheritsCoreConfiguration)
                .map(Class::getName).sorted().toList();

        // then
        assertThat(inheriting)
                .as("Self-hosted Spring tests inheriting @CoreSpringBootTest or ClosedPortStandIns, which stand in "
                        + "for the adapters this edition must provide: use @SelfHostedSpringBootTest")
                .isEmpty();
    }

    private List<Class<?>> selfHostedTests() {

        return CompiledTestClasses.springTestsBeside(getClass());
    }
}

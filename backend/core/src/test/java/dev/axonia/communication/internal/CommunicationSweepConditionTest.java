package dev.axonia.communication.internal;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.axonia.OrganizationIds;
import dev.axonia.SweepHeartbeat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The pin {@link CommunicationSweepEnabledTest} cannot provide: {@code matchIfMissing = true} can, by definition, only
 * be exercised with the property key entirely ABSENT, and every {@code @SpringBootTest} in this suite sets
 * {@code axonia.communication.sweep.enabled=false} explicitly (see {@code application-DEV.properties}), so no full
 * Spring Boot context in the suite ever leaves the key unset. {@link #theBeanIsPresentWhenNoPropertyIsSetAtAll} is the
 * one place that does: an isolated {@link ApplicationContextRunner} over {@link CommunicationSweep}'s own
 * {@code @ConditionalOnProperty}, with {@link OrganizationIds}, {@link SweepUnit}, {@link SweepHeartbeat},
 * {@link Clock} and {@link CommunicationProperties} stubbed out since only the condition, not real collaborator wiring,
 * is under test here - {@link CommunicationSweepEnabledTest} already proves the real ones resolve correctly.
 *
 * <p>
 * Verified to bite, one mutation at a time, by applying each to {@link CommunicationSweep} and re-running this class:
 * flipping {@code matchIfMissing = true} to {@code havingValue = "true"} fails
 * {@link #theBeanIsPresentWhenNoPropertyIsSetAtAll} (no property set, so the condition no longer matches by default);
 * renaming the property key fails {@link #theBeanIsAbsentWhenExplicitlyDisabled} only, because with
 * {@code matchIfMissing = true} still in force the condition matches by default, so the bean is present - which is what
 * {@link #theBeanIsPresentWhenExplicitlyEnabled} already asserts, leaving it green; inverting to
 * {@code havingValue = "false"} while also dropping {@code matchIfMissing} fails all three, since the default flips to
 * absent and each explicit setting then produces the opposite of what the two branch tests expect.
 */
class CommunicationSweepConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(OrganizationIds.class, () -> mock(OrganizationIds.class))
            .withBean(SweepUnit.class, () -> mock(SweepUnit.class))
            .withBean(SweepHeartbeat.class, () -> mock(SweepHeartbeat.class))
            .withBean(Clock.class, () -> mock(Clock.class))
            .withBean(CommunicationProperties.class, () -> mock(CommunicationProperties.class))
            .withUserConfiguration(CommunicationSweep.class);

    @Test
    void theBeanIsPresentWhenNoPropertyIsSetAtAll() {

        contextRunner.run(context -> assertThat(context).hasSingleBean(CommunicationSweep.class));
    }

    @Test
    void theBeanIsAbsentWhenExplicitlyDisabled() {

        contextRunner.withPropertyValues("axonia.communication.sweep.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CommunicationSweep.class));
    }

    @Test
    void theBeanIsPresentWhenExplicitlyEnabled() {

        contextRunner.withPropertyValues("axonia.communication.sweep.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(CommunicationSweep.class));
    }
}

package dev.axonia.communication.adapter.in.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the guard {@code CommunicationSweep}'s own javadoc describes: under the test profile's
 * {@code axonia.communication.sweep.enabled=false} (see {@code src/test/resources/axonia-core-test.properties}), the
 * bean must be entirely absent from the context, not merely present-but-quiet. Bean-level absence is what makes one
 * flag enough for {@link CommunicationSweep}'s {@code @Scheduled} drain tick: there is no per-method condition to
 * forget, because there is no per-method bean. A delayed first tick is not enough on its own -
 * {@code @EnableScheduling} sits on the application class, so every {@code @SpringBootTest} in the suite boots a live
 * scheduler, and Spring's TestContext cache keeps a booted context, and its scheduler, alive for the gate's whole
 * duration rather than one test class's. This test would go red the moment the {@code @ConditionalOnProperty} guard on
 * {@link CommunicationSweep} was removed or its property key changed, regardless of whether any other test happened to
 * notice the resulting background activity.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class CommunicationSweepDisabledInTestsTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSweepBeanIsAbsentUnderTheTestProfilesDisableFlag() {

        assertThat(context.getBeanNamesForType(CommunicationSweep.class)).isEmpty();
    }
}

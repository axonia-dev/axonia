package dev.axonia.communication.adapter.in.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.communication.application.SweepUnit;
import dev.axonia.organization.api.OrganizationIds;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An integration-level smoke test, not the mutation-catching guard an earlier version of this javadoc claimed it was:
 * it boots the REAL application context with real collaborators ({@link OrganizationIds}, {@link SweepUnit}) and proves
 * {@link CommunicationSweep} actually resolves and constructs when the flag is explicitly {@code true}, rather than
 * only satisfying an isolated condition check. That guard - the one that actually goes red when
 * {@code matchIfMissing = true} is flipped to {@code havingValue = "true"}, or the property key is renamed, or the
 * condition is inverted - is {@link CommunicationSweepConditionTest}, not this class. The mutation that matters most is
 * the first one: it kills all mail in production, because production never sets the key, and it leaves BOTH this class
 * and {@link CommunicationSweepDisabledInTestsTest} green, since both only ever run with the key set to a literal
 * {@code "true"} or {@code "false"} and neither can therefore exercise {@code matchIfMissing} at all. The rename and
 * the inversion do go red in {@link CommunicationSweepDisabledInTestsTest}. See
 * {@link CommunicationSweepConditionTest}'s own javadoc for the per-mutation trace.
 *
 * <p>
 * Enabling the flag necessarily boots a real {@code @Scheduled} drain tick (produce and retention are clustered tasks,
 * and the clustered scheduler never starts in a test), which {@code axonia-core-test.properties}' own comment on
 * {@code axonia.communication.sweep.enabled} documents as a genuine hazard: a context left in Spring's TestContext
 * cache keeps ticking against the shared {@code jdbc:tc} container - whose driver connects as the superuser regardless
 * of which credentials are configured, bypassing RLS entirely - for as long as the cache holds it, which in the full
 * gate is the rest of the run. Two things close that off here rather than merely hoping the tick's first run never
 * lands: the drain interval is pushed out to comfortably outlast this class's own execution, and
 * {@code @DirtiesContext} closes this context - cancelling its scheduler, and with it the tick - the moment this
 * class's tests finish, instead of caching it for whatever runs next.
 */
@CoreSpringBootTest(properties = {"axonia.communication.sweep.enabled=true", "axonia.communication.drain-interval=1h"})
@ActiveProfiles("DEV")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunicationSweepEnabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSweepBeanIsPresentWhenTheFlagIsExplicitlyEnabled() {

        assertThat(context.getBeanNamesForType(CommunicationSweep.class)).hasSize(1);
    }
}

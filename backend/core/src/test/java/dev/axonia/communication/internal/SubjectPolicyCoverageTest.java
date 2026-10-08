package dev.axonia.communication.internal;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.MessageSubjectPolicy;
import dev.axonia.communication.MessageType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An open {@link MessageType} no {@link MessageSubjectPolicy} claims must stop the application from starting. Left to
 * delivery, the same fault is silent: every row of the unrouted type is suppressed with a reason naming a setting that
 * was never the cause - on every recipient, permanently. A contributed type exists because a policy claims it.
 */
class SubjectPolicyCoverageTest {

    @Test
    void refusesToStartWhenAnOpenMessageTypeHasNoPolicy() {

        var thrown = catchThrowable(() -> new MessageSubjectPolicies(List.of()));

        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("no MessageSubjectPolicy")
                .hasMessageContaining("session_upcoming").hasMessageContaining("organization_roundup");
    }

    @Test
    void namesOnlyTheMessageTypesLeftUnrouted() {

        var partial = policyFor(MessageType.SESSION_UPCOMING, MessageType.SESSION_WRAP_UP);

        var thrown = catchThrowable(() -> new MessageSubjectPolicies(List.of(partial)));

        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("session_awaiting_close")
                .hasMessageNotContaining("session_upcoming");
    }

    @Test
    void aContributedTypeIsDeclaredAfterTheOpenOnes() {

        var contributed = MessageType.of("contributed_type");
        var policies = new MessageSubjectPolicies(
                List.of(policyFor(MessageType.open().toArray(MessageType[]::new)), policyFor(contributed)));

        assertThat(policies.types()).containsExactly(MessageType.SESSION_UPCOMING, MessageType.SESSION_WRAP_UP,
                MessageType.SESSION_AWAITING_CLOSE, MessageType.ORGANIZATION_ROUNDUP, contributed);
        assertThat(policies.declares(contributed)).isTrue();
        assertThat(policies.declares(MessageType.of("not_sent_here"))).isFalse();
        assertThat(policies.policyFor(MessageType.of("not_sent_here"))).isEmpty();
    }

    @Test
    void refusesTwoPoliciesClaimingOneType() {

        var thrown = catchThrowable(
                () -> new MessageSubjectPolicies(List.of(policyFor(MessageType.open().toArray(MessageType[]::new)),
                        policyFor(MessageType.SESSION_UPCOMING))));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    private static MessageSubjectPolicy policyFor(MessageType... types) {

        var policy = mock(MessageSubjectPolicy.class);
        when(policy.handles()).thenReturn(Set.of(types));
        return policy;
    }
}

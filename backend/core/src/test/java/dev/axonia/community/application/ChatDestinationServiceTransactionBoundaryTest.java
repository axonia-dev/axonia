package dev.axonia.community.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for a bug that shipped once: {@code postTestMessage} - now {@link ChatDestinationService#test} -
 * used to be {@code @Transactional} across both the outbound probe call and the health write that follows it, so a
 * failed test post's own {@code ChatSendFailedException} rolled back the very write meant to record the failure - the
 * leader-visible half of a failed test post vanished in production, and no Mockito-based unit test could see it (a mock
 * {@code CommunityStore} has no transaction to roll back).
 *
 * <p>
 * No unit test exercising mocks can prove the absence of a real, in-force transaction boundary, and this repository has
 * no {@code @DataJdbcTest} class in this module today whose existing {@code @Import} set this case could safely join -
 * adding a new one risks the shared Testcontainers Postgres exhaustion this repository has hit before. A structural
 * check is the cheap, precise alternative: it fails loudly on exactly the edit that would reintroduce the bug, at
 * compile-adjacent speed, with no database.
 *
 * <p>
 * This alone does not make the write safe - see {@code DrainWorkers.outcomeOf}'s own javadoc for why the drain keeps
 * this exact write on a stack of its own, and {@code ChatHealthRecorder} for where {@link ChatDestinationService#test}
 * now reaches it through a genuine proxy hop rather than a call on {@code this}. It guards the one thing neither of
 * those can: that nobody re-annotates the method wrapping both the probe call and the write in one ambient transaction
 * again.
 */
class ChatDestinationServiceTransactionBoundaryTest {

    @Test
    void test_carriesNoTransactionalAnnotation() throws NoSuchMethodException {

        var method = ChatDestinationService.class.getMethod("test", String.class, long.class);

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }
}

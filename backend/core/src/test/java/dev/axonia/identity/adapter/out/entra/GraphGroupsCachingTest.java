package dev.axonia.identity.adapter.out.entra;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.axonia.SteppableClock;
import dev.axonia.identity.api.IdentityProviderDirectory.IdpGroup;
import dev.axonia.identity.api.IdpProvider;
import dev.axonia.identity.api.OrganizationIdp;
import dev.axonia.identity.configuration.CacheConfig;
import dev.axonia.identity.configuration.EntraGraph;
import dev.axonia.shared.api.metrics.IntegrationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The Entra groups cache, asserted through a real Spring context: the cache is a proxy, so an adapter built by hand
 * would pass every one of these against a method no cache stands in front of.
 */
class GraphGroupsCachingTest {

    private static final Class<?>[] WIRING = {CacheConfig.class, GraphGroups.class, EntraDirectoryAdapter.class};

    private static final String GROUPS = "https://graph.microsoft.com/v1.0/groups?$select=id,displayName";
    private static final Instant T0 = Instant.parse("2020-01-01T00:00:00Z");
    private static final OrganizationIdp ACME = new OrganizationIdp(IdpProvider.ENTRA, "tenant-acme", true);
    private static final OrganizationIdp GLOBEX = new OrganizationIdp(IdpProvider.ENTRA, "tenant-globex", true);

    private final RestClient.Builder graphBuilder = RestClient.builder();
    private final MockRestServiceServer graph = MockRestServiceServer.bindTo(graphBuilder).build();
    private final SteppableClock clock = new SteppableClock(T0);
    private final IntegrationMetrics metrics = new IntegrationMetrics(new SimpleMeterRegistry());

    private final ApplicationContextRunner contexts = new ApplicationContextRunner().withUserConfiguration(WIRING)
            .withBean("entraGraphRestClient", RestClient.class, () -> EntraGraph.client(graphBuilder, metrics, clock))
            .withBean(Clock.class, () -> clock);

    @Test
    void theSameTenantIsReadOnceWithinFiveMinutes() {

        // given
        EntraGraph.expectToken(graph, "tenant-acme", "graph-token");
        expectGroups("acme-g1");

        contexts.run(context -> {
            var directory = context.getBean(EntraDirectoryAdapter.class);

            // when
            var first = directory.listGroups("acme", ACME);
            clock.advance(Duration.ofMinutes(5).minusNanos(1));
            var second = directory.listGroups("acme", ACME);

            // then
            graph.verify();
            assertThat(second).isEqualTo(first);
        });
    }

    @Test
    void theTenantIsReadAgainOnceFiveMinutesHavePassedOnTheApplicationClock() {

        // given
        EntraGraph.expectToken(graph, "tenant-acme", "graph-token");
        expectGroups("acme-g1");
        expectGroups("acme-g2");

        contexts.run(context -> {
            var directory = context.getBean(EntraDirectoryAdapter.class);

            // when
            directory.listGroups("acme", ACME);
            clock.advance(Duration.ofMinutes(5));
            var after = directory.listGroups("acme", ACME);

            // then
            graph.verify();
            assertThat(after).extracting(IdpGroup::groupId).containsExactly("acme-g2");
        });
    }

    @Test
    void anOrganizationThatChangesItsTenantSeesTheNewTenantsGroupsAtOnce() {

        // given
        EntraGraph.expectToken(graph, "tenant-acme", "graph-token");
        expectGroups("acme-g1");
        EntraGraph.expectToken(graph, "tenant-globex", "graph-token");
        expectGroups("globex-g1");

        contexts.run(context -> {
            var directory = context.getBean(EntraDirectoryAdapter.class);

            // when
            directory.listGroups("acme", ACME);
            var afterTheChange = directory.listGroups("acme", GLOBEX);

            // then
            graph.verify();
            assertThat(afterTheChange).extracting(IdpGroup::groupId).containsExactly("globex-g1");
        });
    }

    @Test
    void twoOrganizationsOnOneTenantShareOneRead() {

        // given
        EntraGraph.expectToken(graph, "tenant-acme", "graph-token");
        expectGroups("acme-g1");

        contexts.run(context -> {
            var directory = context.getBean(EntraDirectoryAdapter.class);

            // when
            var parent = directory.listGroups("acme", ACME);
            var subsidiary = directory.listGroups("acme-subsidiary", ACME);

            // then
            graph.verify();
            assertThat(subsidiary).isEqualTo(parent);
        });
    }

    @Test
    void twoTenantsNeverShareAnEntry() {

        // given
        EntraGraph.expectToken(graph, "tenant-acme", "graph-token");
        expectGroups("acme-g1");
        EntraGraph.expectToken(graph, "tenant-globex", "graph-token");
        expectGroups("globex-g1");

        contexts.run(context -> {
            var directory = context.getBean(EntraDirectoryAdapter.class);

            // when
            var acme = directory.listGroups("acme", ACME);
            var globex = directory.listGroups("globex", GLOBEX);
            var acmeAgain = directory.listGroups("acme", ACME);
            var globexAgain = directory.listGroups("globex", GLOBEX);

            // then
            graph.verify();
            assertThat(acme).extracting(IdpGroup::groupId).containsExactly("acme-g1");
            assertThat(globex).extracting(IdpGroup::groupId).containsExactly("globex-g1");
            assertThat(acmeAgain).isEqualTo(acme);
            assertThat(globexAgain).isEqualTo(globex);
        });
    }

    @Test
    void aRevokedProofTakesEffectAtOnceThoughTheTenantIsCached() {

        // given
        EntraGraph.expectToken(graph, "tenant-acme", "graph-token");
        expectGroups("acme-g1");
        var revoked = new OrganizationIdp(IdpProvider.ENTRA, "tenant-acme", false);

        contexts.run(context -> {
            var directory = context.getBean(EntraDirectoryAdapter.class);

            // when
            var proved = directory.listGroups("acme", ACME);
            var afterRevocation = directory.listGroups("acme", revoked);

            // then
            graph.verify();
            assertThat(proved).isNotEmpty();
            assertThat(afterRevocation).isEmpty();
        });
    }

    @Test
    void aFailedReadIsNotCached() {

        // given
        EntraGraph.expectToken(graph, "tenant-acme", "graph-token");
        graph.expect(requestTo(GROUPS)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        expectGroups("acme-g1");

        contexts.run(context -> {
            var directory = context.getBean(EntraDirectoryAdapter.class);

            // when
            var failed = catchThrowable(() -> directory.listGroups("acme", ACME));
            var retried = directory.listGroups("acme", ACME);

            // then
            graph.verify();
            assertThat(failed).isNotNull();
            assertThat(retried).extracting(IdpGroup::groupId).containsExactly("acme-g1");
        });
    }

    @Test
    void concurrentReadsOfOneTenantShareOneGraphCall() {

        // given - the first read is held inside Graph until the second caller is parked behind it
        var inside = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        EntraGraph.expectToken(graph, "tenant-acme", "graph-token");
        graph.expect(requestTo(GROUPS)).andRespond(request -> {
            inside.countDown();
            awaitQuietly(release);
            return withSuccess(page("acme-g1"), MediaType.APPLICATION_JSON).createResponse(request);
        });

        contexts.run(context -> {
            var directory = context.getBean(EntraDirectoryAdapter.class);
            var first = new FutureTask<>(() -> directory.listGroups("acme", ACME));
            var second = new FutureTask<>(() -> directory.listGroups("acme-subsidiary", ACME));

            // when
            var firstThread = Thread.ofPlatform().start(first);
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
            var secondThread = Thread.ofPlatform().start(second);
            await("the second reader parked on the first reader's load or done").atMost(Duration.ofSeconds(5))
                    .until(() -> isParkedOnTheSharedLoadOrDone(secondThread));
            release.countDown();
            firstThread.join();
            secondThread.join();

            // then
            graph.verify();
            assertThat(second.get()).isEqualTo(first.get());
        });
    }

    private void expectGroups(String... ids) {
        graph.expect(requestTo(GROUPS)).andRespond(withSuccess(page(ids), MediaType.APPLICATION_JSON));
    }

    private static String page(String... ids) {
        return Arrays.stream(ids).map(id -> "{\"id\":\"" + id + "\",\"displayName\":\"" + id + "\"}")
                .collect(Collectors.joining(",", "{\"value\":[", "]}"));
    }

    /**
     * Done, or blocked on the cache entry the first reader is loading: a thread merely starting up can be briefly
     * waiting or blocked before it reaches the cache, and releasing the load then would let it start a second one.
     */
    private static boolean isParkedOnTheSharedLoadOrDone(Thread thread) {

        return thread.getState() == Thread.State.TERMINATED
                || thread.getState() == Thread.State.BLOCKED && Arrays.stream(thread.getStackTrace())
                        .anyMatch(frame -> frame.getClassName().equals(ConcurrentHashMap.class.getName()));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

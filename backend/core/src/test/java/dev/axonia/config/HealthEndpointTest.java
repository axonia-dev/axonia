package dev.axonia.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import dev.axonia.CoreSpringBootTest;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Holds the two halves of the health contract apart, because they answer different questions and only one of them is a
 * deployment signal.
 *
 * <p>
 * This suite runs with Redis deliberately unreachable: {@code src/test/resources/application-DEV.properties} points
 * {@code spring.data.redis.url} at a dead port so tests can never share bucket state with a developer's compose Redis.
 * That makes this class the natural home for what an unavailable dependency does to each endpoint, and it means both
 * assertions below observe a real outage rather than a simulated one.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class HealthEndpointTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void theAggregateReportsDownWhenADependencyIsUnreachable() throws Exception {
        // The aggregate exists to tell the truth about dependencies, exactly as it would for a failing database or
        // disk. Asserting DOWN rather than suppressing the contributor is deliberate: a health endpoint that stays
        // green through a real dependency failure is worth less than no health endpoint. The rate limiter still fails
        // open, so the product keeps serving - what we tolerate and what we report are separate concerns.
        //
        // This is also still the authentication test it was built as. apply(springSecurity()) wires the real
        // SecurityConfig chain into MockMvc, so a 401 or 403 fails this assertion too and the endpoint cannot be put
        // behind authentication unnoticed.
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mockMvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void readinessStaysUpBecauseItAnswersWhetherThisInstanceCanServeTraffic() throws Exception {
        // The probe the platform actually calls, and the only one of the two that is a deployment signal. It must track
        // whether this instance can serve traffic, not whether every dependency is healthy, or an outage in something
        // the product is designed to survive would pull a serving backend out of rotation.
        //
        // The readiness group holds only readinessState, which is what makes that true, and this assertion is what
        // keeps it true: widening the group to include the aggregate's contributors fails here.
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

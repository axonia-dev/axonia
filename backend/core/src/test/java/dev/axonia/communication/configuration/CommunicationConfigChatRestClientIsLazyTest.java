package dev.axonia.communication.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CommunicationConfig} is {@code @Import}ed by {@code UnsubscribeControllerTest}, the only {@code @WebMvcTest}
 * slice in this repository that does so, purely to get a real {@code UnsubscribeTokens} bean rather than a mock -
 * {@code @Import}ing a {@code @Configuration} class brings every one of its {@code @Bean} methods along, {@code
 * chatRestClient} included, even though the slice never asks for a {@link org.springframework.web.client.RestClient}. A
 * {@code @WebMvcTest} slice has no auto-configured {@code RestClient.Builder}, which is exactly what this context
 * runner reproduces without the cost of standing up MockMvc: {@code chatRestClient}'s bean definition must still exist
 * (production still needs it), but the method backing it must not run until something actually injects it.
 */
class CommunicationConfigChatRestClientIsLazyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CommunicationConfig.class)
            .withPropertyValues("axonia.communication.unsubscribe-secret=test-unsubscribe-secret",
                    "axonia.tenant-discovery.datasource.password=test-read-password");

    @Test
    void loadsWithoutARestClientBuilderBecauseNothingHereInjectsChatRestClient() {

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBeanDefinition("chatRestClient")).isTrue();
            assertThat(context.getBeanFactory().containsSingleton("chatRestClient")).isFalse();
        });
    }
}

package dev.axonia.communication.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

import dev.axonia.IntegrationMetrics;
import dev.axonia.IntegrationMetrics.Integration;

/**
 * Binds {@link CommunicationProperties} and {@link CommunicationReadCredential}, and wires {@link ChatTransport}'s
 * {@link RestClient}. The module's other beans are plain {@code @Component}s.
 *
 * <p>
 * Public, unlike most of this module's internals, so {@code UnsubscribeControllerTest} - which lives outside
 * {@code .internal} - can {@code @Import} it directly to get a real, fully-wired {@code UnsubscribeTokens} bean rather
 * than a mock, and so prove the controller and the token signer actually agree on a token's shape.
 */
@Configuration
@EnableConfigurationProperties({CommunicationProperties.class, CommunicationReadCredential.class})
public class CommunicationConfig {

    /**
     * Built from the auto-configured builder so it inherits the application's connect and read ceilings, with one
     * deliberate override: redirects are refused rather than followed. {@link ChatTransport} re-checks a stored URL
     * against {@code ChatPlatforms}' host allowlist on every send, but an allowlisted host that answered with a
     * redirect - to, say, a cloud metadata endpoint - would walk straight past that check if the transport followed it,
     * and the client this application actually ships with follows one by default.
     *
     * <p>
     * {@code factoryBuilder} is the SAME {@link ClientHttpRequestFactoryBuilder} bean
     * {@code RestClientAutoConfiguration} itself resolves from {@code spring.http.clients.factory} - never
     * {@code ClientHttpRequestFactoryBuilder.detect()} called again here. That property is unset today, so both resolve
     * identically, but calling {@code detect()} a second time would silently stop tracking it: setting the property
     * later would switch every other client in this application while this one kept whatever {@code detect()} happened
     * to find. {@code builder} stays prototype-scoped per injection point (see {@code RestClientAutoConfiguration}), so
     * overriding its request factory here touches no other client either way.
     *
     * <p>
     * {@code @Lazy}: this class is also {@code @Import}ed by {@code UnsubscribeControllerTest} - a {@code @WebMvcTest}
     * slice - purely to get a real {@link UnsubscribeTokens} bean instead of a mock, but {@code @Import}ing a
     * {@code @Configuration} class brings every one of its {@code @Bean} methods along, this one included. A
     * {@code @WebMvcTest} slice never auto-configures {@code RestClient.Builder}, so without {@code @Lazy} this method
     * is invoked eagerly during context refresh and fails to resolve its first parameter in every such slice, even
     * though nothing in it ever asks for a {@link RestClient}. The same is true of {@code IntegrationMetrics}, a
     * root-package {@code @Component} no slice component-scans. {@code @Lazy} defers the method call until something
     * actually injects the bean: in production, {@link ChatTransport} is an eagerly-instantiated {@code @Component}
     * that does exactly that in its constructor, so the real client is still built at startup, still from the
     * auto-configured builder, still with the timeouts and the no-redirect override above - only the slice that never
     * asks for it is spared the failure.
     */
    @Bean
    @Lazy
    RestClient chatRestClient(RestClient.Builder builder, HttpClientSettings httpClientSettings,
            ClientHttpRequestFactoryBuilder<?> factoryBuilder, IntegrationMetrics metrics) {

        var noRedirects = factoryBuilder.build(httpClientSettings.withRedirects(HttpRedirects.DONT_FOLLOW));
        return builder.requestFactory(noRedirects).requestInterceptor(metrics.interceptor(Integration.CHAT)).build();
    }
}

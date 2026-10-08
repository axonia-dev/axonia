package dev.axonia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OAuth2 client is on the classpath for axonia's own outbound tokens only. Spring Security wires OAuth2 login
 * support as soon as it finds a registration repository or an authorized-client manager among the beans, so none may be
 * one, and no filter chain may carry an OAuth2 client filter.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class OAuth2ClientStaysOffTheSecurityChainsTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void noRegistrationRepositoryOrAuthorizedClientPieceIsABean() {

        assertThat(context.getBeanNamesForType(ClientRegistrationRepository.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OAuth2AuthorizedClientService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OAuth2AuthorizedClientRepository.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OAuth2AuthorizedClientManager.class)).isEmpty();
    }

    @Test
    void noSecurityFilterChainCarriesAnOAuth2ClientFilter() {

        var filters = context.getBeansOfType(SecurityFilterChain.class).values().stream()
                .flatMap(chain -> chain.getFilters().stream()).map(filter -> filter.getClass().getName()).toList();

        assertThat(filters).isNotEmpty()
                .noneMatch(name -> name.startsWith("org.springframework.security.oauth2.client"));
    }
}

package dev.axonia.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.axonia.CoreWebMvcTest;
import dev.axonia.community.CommunityController;
import dev.axonia.community.CommunityPolicyService;
import dev.axonia.community.CommunityService;
import dev.axonia.community.LookAndFeelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * An anonymous endpoint is permitted because the module serving it says so, and only for POST. The chain itself names
 * none: {@link SecurityConfigTest} runs it with no contribution, where every API request needs a bearer.
 */
@CoreWebMvcTest(controllers = CommunityController.class)
@ActiveProfiles("DEV")
@Import({SecurityConfig.class, AnonymousPostTest.ContributedAnonymousPost.class})
class AnonymousPostTest {

    private static final String CONTRIBUTED = "/axonia/communities/probe/anonymous";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CommunityService communityService;

    @MockitoBean
    private CommunityPolicyService communityPolicyService;

    @MockitoBean
    private LookAndFeelService lookAndFeelService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void aContributedPostIsReachableWithoutABearer() {

        // when
        var actual = mvc.post().uri(CONTRIBUTED).exchange();

        // then
        assertThat(actual.getResponse().getStatus()).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void theContributedPathStillNeedsABearerForAnotherMethod() {

        // when
        var actual = mvc.get().uri(CONTRIBUTED).exchange();

        // then
        assertThat(actual.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void aPostNobodyContributedStillNeedsABearer() {

        // when
        var actual = mvc.post().uri("/axonia/communities").exchange();

        // then
        assertThat(actual.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void aPathWithoutItsLeadingSlashIsRefused() {

        assertThatIllegalArgumentException().isThrownBy(() -> new AnonymousPost("communities/probe/anonymous"))
                .withMessage("An anonymous post's path starts with '/': communities/probe/anonymous");
    }

    @Test
    void aMissingPathIsRefused() {

        assertThatIllegalArgumentException().isThrownBy(() -> new AnonymousPost(null))
                .withMessage("An anonymous post's path starts with '/': null");
    }

    @Test
    void aSubtreeIsRefused() {

        assertThatIllegalArgumentException().isThrownBy(() -> new AnonymousPost("/**"))
                .withMessage("An anonymous post names a single path, not a subtree: /**");
    }

    @TestConfiguration
    static class ContributedAnonymousPost {

        @Bean
        AnonymousPost probe() {

            return new AnonymousPost("/communities/probe/anonymous");
        }
    }
}

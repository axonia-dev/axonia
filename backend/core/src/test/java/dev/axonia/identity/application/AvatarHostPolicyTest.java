package dev.axonia.identity.application;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.identity.configuration.AvatarProperties;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarHostPolicyTest {

    private final AvatarHostPolicy policy = new AvatarHostPolicy(new AvatarProperties(
            List.of("googleusercontent.com", "avatars.githubusercontent.com"), 2_097_152, Duration.ofSeconds(5)));

    @Test
    void permitsAnExactHostMatch() {

        assertThat(policy.permits(URI.create("https://avatars.githubusercontent.com/u/1?v=4"))).isTrue();
    }

    @Test
    void permitsASubdomainOfAnAllowedHost() {

        assertThat(policy.permits(URI.create("https://lh3.googleusercontent.com/a/abc=s96-c"))).isTrue();
    }

    @Test
    void refusesAHostThatMerelyEndsWithAnAllowedHostWithoutADotBoundary() {

        assertThat(policy.permits(URI.create("https://evilgoogleusercontent.com/a"))).isFalse();
    }

    @Test
    void refusesAnUnlistedHost() {

        assertThat(policy.permits(URI.create("https://cdn.example.com/a.png"))).isFalse();
    }

    @Test
    void refusesPlainHttpOnAnOtherwiseAllowedHost() {

        assertThat(policy.permits(URI.create("http://lh3.googleusercontent.com/a"))).isFalse();
    }

    @Test
    void refusesAUrlCarryingUserInfo() {

        assertThat(policy.permits(URI.create("https://attacker@lh3.googleusercontent.com/a"))).isFalse();
    }

    @Test
    void refusesAUrlWithNoHost() {

        assertThat(policy.permits(URI.create("https:///a"))).isFalse();
    }

    @Test
    void matchesTheHostCaseInsensitively() {

        assertThat(policy.permits(URI.create("https://LH3.GoogleUserContent.COM/a"))).isTrue();
    }
}

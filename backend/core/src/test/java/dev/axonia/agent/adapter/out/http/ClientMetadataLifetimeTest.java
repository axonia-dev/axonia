package dev.axonia.agent.adapter.out.http;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMetadataLifetimeTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            max-age=600                         | 600
            public, max-age=600                 | 600
            MAX-AGE=600                         | 600
            max-age="600"                       | 600
            no-cache="Set-Cookie", max-age=600  | 600
            private, max-age=600, s-maxage=30   | 600
            max-age=600, community="UCI"        | 600
            max-age=60                          | 60
            max-age=86400                       | 86400
            max-age=59                          | 60
            max-age=0                           | 60
            max-age=86401                       | 86400
            max-age=99999999999999999999        | 86400
            no-store                            | 60
            No-Cache                            | 60
            no-cache, max-age=600               | 60
            public, max-age=600, no-store       | 60
            max-age=600, max-age=600            | 60
            max-age=600, max-age=3600           | 60
            max-age                             | 60
            max-age=abc                         | 60
            max-age=-1                          | 60
            max-age=600, "                      | 60
            max-age = 600                       | 60
            public                              | 3600
            no-cache="Set-Cookie"               | 3600
            s-maxage=600                        | 3600
            ''                                  | 3600
            """)
    void of(String cacheControl, long seconds) {

        assertThat(ClientMetadataLifetime.of(cacheControl)).isEqualTo(Duration.ofSeconds(seconds));
    }

    @Test
    void of_withoutCacheControl() {

        assertThat(ClientMetadataLifetime.of(null)).isEqualTo(Duration.ofHours(1));
    }
}

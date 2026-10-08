package dev.axonia.privacy.internal;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurrogateIdsTest {

    @Test
    void mintsAPrefixedIdThatFitsASubjectColumnAndCannotCollideWithAnIdentityProviderSubject() {

        var surrogate = SurrogateIds.mint();

        assertThat(surrogate).startsWith("erased:").hasSizeLessThan(256);
    }

    @Test
    void mintsADistinctIdEveryTime() {

        assertThat(Stream.generate(SurrogateIds::mint).limit(1_000).distinct()).hasSize(1_000);
    }
}

package dev.axonia.knowledge.application;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.application.port.out.ArtifactLoveCounts;
import dev.axonia.recognition.api.ContributionLoved;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.ContributionUnloved;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@MockitoSettings
class ArtifactLoveCounterTest {

    @Mock
    private ArtifactLoveCounts loveCounts;

    @Test
    void loved_countsACapturedArtifact() {

        // when
        new ArtifactLoveCounter(loveCounts).loved(new ContributionLoved(ContributionType.ARTIFACT_CAPTURED, 7L, 1L));

        // then
        verify(loveCounts).increment(7L, 1L);
    }

    @Test
    void unloved_uncountsACapturedArtifact() {

        // when
        new ArtifactLoveCounter(loveCounts)
                .unloved(new ContributionUnloved(ContributionType.ARTIFACT_CAPTURED, 7L, 1L));

        // then
        verify(loveCounts).decrement(7L, 1L);
    }

    @Test
    void otherContributionKindsAreIgnored() {

        // given
        var other = ContributionType.SLOT_PRESENTED;
        var sut = new ArtifactLoveCounter(loveCounts);

        // when
        sut.loved(new ContributionLoved(other, 7L, 1L));
        sut.unloved(new ContributionUnloved(other, 7L, 1L));

        // then
        verifyNoInteractions(loveCounts);
    }
}

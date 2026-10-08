package dev.axonia.bookmarks.internal;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.Artifact;
import dev.axonia.knowledge.ArtifactService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class ArtifactAccessAdapterTest {

    @Mock
    private ArtifactService artifactService;

    @InjectMocks
    private ArtifactAccessAdapter sut;

    @Test
    void get_delegatesToArtifactService() {

        // given
        var artifact = Artifact.builder().id(100L).communityId(7L).title("Hexagonal Architecture 101").build();
        when(artifactService.get(100L)).thenReturn(artifact);

        // then
        assertThat(sut.get(100L)).isSameAs(artifact);
    }
}

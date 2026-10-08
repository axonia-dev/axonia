package dev.axonia.onboarding.internal;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;

import dev.axonia.knowledge.Artifact;
import dev.axonia.knowledge.ArtifactService;
import dev.axonia.onboarding.Pick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@MockitoSettings
class LovedArtifactsAdapterTest {

    @Mock
    private ArtifactService artifacts;

    @InjectMocks
    private LovedArtifactsAdapter adapter;

    @Test
    void picksTheArtifactWithLoveOverZero() {

        when(artifacts.search(eq("guild"), any(), any())).thenReturn(
                new PageImpl<>(List.of(Artifact.builder().id(1L).title("build cache guide").loveCount(3).build())));

        assertThat(adapter.mostLoved("guild")).contains(new Pick(1L, "build cache guide"));
    }

    @Test
    void anUntouchedTopResultYieldsNoStep() {

        when(artifacts.search(eq("guild"), any(), any()))
                .thenReturn(new PageImpl<>(List.of(Artifact.builder().id(1L).title("unloved").loveCount(0).build())));

        assertThat(adapter.mostLoved("guild")).isEmpty();
    }

    @Test
    void anEmptyLibraryYieldsNoStep() {

        when(artifacts.search(eq("guild"), any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(adapter.mostLoved("guild")).isEmpty();
    }
}

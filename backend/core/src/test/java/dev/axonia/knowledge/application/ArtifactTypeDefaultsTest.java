package dev.axonia.knowledge.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.knowledge.application.port.out.ArtifactTypeStore;
import dev.axonia.knowledge.domain.StoredArtifactType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactTypeDefaultsTest {

    @Mock
    private ArtifactTypeStore repository;

    @InjectMocks
    private ArtifactTypeDefaults sut;

    @Test
    void seedFor_insertsTheDefaultVocabularyForTheOrganization() {
        // given
        when(repository.save(any(StoredArtifactType.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.seedFor("rust-practitioners");

        // then
        var actual = ArgumentCaptor.forClass(StoredArtifactType.class);
        verify(repository, times(6)).save(actual.capture());
        assertThat(actual.getAllValues()).extracting(StoredArtifactType::label).containsExactly("ADR", "Article",
                "Notes", "Recording", "Repo", "Slides");
        assertThat(actual.getAllValues()).extracting(StoredArtifactType::organizationId)
                .containsOnly("rust-practitioners");
        assertThat(actual.getAllValues().get(0)).extracting(StoredArtifactType::icon, StoredArtifactType::accentColor)
                .containsExactly("file-check", "#6e3ceb");
    }
}

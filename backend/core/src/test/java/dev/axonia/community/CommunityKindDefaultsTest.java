package dev.axonia.community;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.community.internal.CommunityKindEntity;
import dev.axonia.community.internal.CommunityKindRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityKindDefaultsTest {

    @Mock
    private CommunityKindRepository repository;

    @InjectMocks
    private CommunityKindDefaults sut;

    @Test
    void seedFor_insertsTheDefaultVocabularyForTheOrganization() {
        // given
        when(repository.save(any(CommunityKindEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.seedFor("rust-practitioners");

        // then
        var actual = ArgumentCaptor.forClass(CommunityKindEntity.class);
        verify(repository, times(1)).save(actual.capture());
        assertThat(actual.getAllValues()).extracting(CommunityKindEntity::getLabel)
                .containsExactly("Community of Practice");
        assertThat(actual.getAllValues()).extracting(CommunityKindEntity::getOrganizationId)
                .containsOnly("rust-practitioners");
    }
}

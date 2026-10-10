package dev.axonia.community.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.domain.CommunityKindRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityKindDefaultsServiceTest {

    @Mock
    private CommunityKindStore repository;

    @InjectMocks
    private CommunityKindDefaultsService sut;

    @Test
    void seedFor_insertsTheDefaultVocabularyForTheOrganization() {
        // given
        when(repository.save(any(CommunityKindRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.seedFor("rust-practitioners");

        // then
        var actual = ArgumentCaptor.forClass(CommunityKindRecord.class);
        verify(repository, times(1)).save(actual.capture());
        assertThat(actual.getAllValues()).extracting(CommunityKindRecord::getLabel)
                .containsExactly("Community of Practice");
        assertThat(actual.getAllValues()).extracting(CommunityKindRecord::getOrganizationId)
                .containsOnly("rust-practitioners");
    }
}

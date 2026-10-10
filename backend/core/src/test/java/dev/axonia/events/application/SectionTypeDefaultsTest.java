package dev.axonia.events.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.domain.SectionTypeRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionTypeDefaultsTest {

    @Mock
    private SectionTypeStore repository;

    @InjectMocks
    private SectionTypeDefaults sut;

    @Test
    void seedFor_insertsTheDefaultVocabularyForTheOrganization() {
        // given
        when(repository.save(any(SectionTypeRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.seedFor("rust-practitioners");

        // then
        var actual = ArgumentCaptor.forClass(SectionTypeRecord.class);
        verify(repository, times(5)).save(actual.capture());
        assertThat(actual.getAllValues()).extracting(SectionTypeRecord::getLabel).containsExactly("Ice Breaker",
                "Lightning Talk", "Deep Dive", "Round Table", "Q&A");
        assertThat(actual.getAllValues()).extracting(SectionTypeRecord::getOrganizationId)
                .containsOnly("rust-practitioners");
        assertThat(actual.getAllValues()).extracting(SectionTypeRecord::isExpectsPresenter).containsExactly(false, true,
                true, false, false);
    }
}

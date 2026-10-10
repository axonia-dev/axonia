package dev.axonia.community.application;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.domain.CommunityRecordMother;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityLookupTest {

    @Mock
    private CommunityStore communityStore;

    @InjectMocks
    private CommunityLookup sut;

    @Test
    void findBySlugOrThrow_returnsTheEntity() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));

        // when
        var actual = sut.findBySlugOrThrow("default", "backend-guild");

        // then
        assertThat(actual).isEqualTo(entity);
    }

    @Test
    void findBySlugOrThrow_notFound() {

        // given
        when(communityStore.findBySlug("default", "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.findBySlugOrThrow("default", "missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByIdOrThrow_returnsTheEntity() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communityStore.findById("default", 1L)).thenReturn(Optional.of(entity));

        // when
        var actual = sut.findByIdOrThrow("default", 1L);

        // then
        assertThat(actual).isEqualTo(entity);
    }

    @Test
    void findByIdOrThrow_notFound() {

        // given
        when(communityStore.findById("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.findByIdOrThrow("default", 99L)).isInstanceOf(NotFoundException.class);
    }
}

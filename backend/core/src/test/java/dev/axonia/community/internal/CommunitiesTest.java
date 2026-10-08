package dev.axonia.community.internal;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.NotFoundException;
import dev.axonia.community.mother.CommunityEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunitiesTest {

    @Mock
    private CommunityRepository communityRepository;

    @InjectMocks
    private Communities sut;

    @Test
    void findBySlugOrThrow_returnsTheEntity() {

        // given
        var entity = CommunityEntityMother.any().id(1L).build();
        when(communityRepository.findByOrganizationIdAndSlug("default", "backend-guild"))
                .thenReturn(Optional.of(entity));

        // when
        var actual = sut.findBySlugOrThrow("default", "backend-guild");

        // then
        assertThat(actual).isEqualTo(entity);
    }

    @Test
    void findBySlugOrThrow_notFound() {

        // given
        when(communityRepository.findByOrganizationIdAndSlug("default", "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.findBySlugOrThrow("default", "missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByIdOrThrow_returnsTheEntity() {

        // given
        var entity = CommunityEntityMother.any().id(1L).build();
        when(communityRepository.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));

        // when
        var actual = sut.findByIdOrThrow("default", 1L);

        // then
        assertThat(actual).isEqualTo(entity);
    }

    @Test
    void findByIdOrThrow_notFound() {

        // given
        when(communityRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.findByIdOrThrow("default", 99L)).isInstanceOf(NotFoundException.class);
    }
}

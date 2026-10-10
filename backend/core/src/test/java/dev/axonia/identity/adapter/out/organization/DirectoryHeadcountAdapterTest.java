package dev.axonia.identity.adapter.out.organization;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.adapter.out.persistence.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class DirectoryHeadcountAdapterTest {

    private static final String ORGANIZATION = "default";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DirectoryHeadcountAdapter sut;

    @Test
    void presentMembers_countsTheOrganizationsDirectory() {

        // given
        when(userRepository.countPresentByOrganizationId(ORGANIZATION)).thenReturn(4L);

        // when
        var result = sut.presentMembers(ORGANIZATION);

        // then
        assertThat(result).isEqualTo(4L);
    }
}

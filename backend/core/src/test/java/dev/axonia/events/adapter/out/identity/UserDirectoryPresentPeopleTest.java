package dev.axonia.events.adapter.out.identity;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.UserDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class UserDirectoryPresentPeopleTest {

    @Mock
    private UserDirectory userDirectory;

    @InjectMocks
    private UserDirectoryPresentPeople sut;

    @Test
    void keepsOnlyThePeopleTheDirectoryStillHas() {

        // given an erased surrogate with no identity row and a deprovisioned member, beside one still here
        var ids = List.of("erased:alice", "deprovisioned-bob", "carol");
        when(userDirectory.presentOf("default", ids)).thenReturn(Set.of("carol"));

        // when
        var actual = sut.presentAmong("default", ids);

        // then
        assertThat(actual).containsExactly("carol");
    }
}

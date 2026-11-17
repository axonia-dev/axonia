package dev.axonia.mentoring.adapter.out.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.DirectoryUser;
import dev.axonia.identity.api.MemberProfiles;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.mentoring.domain.MentoringPerson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

@MockitoSettings
class PersonDirectoryAdapterTest {

    @Mock
    private MemberProfiles memberProfiles;
    @Mock
    private UserDirectory userDirectory;

    @Test
    void everyIdIsNamedAndTheUnresolvedIsADeletedMember() {

        // given
        var sut = new PersonDirectoryAdapter(memberProfiles, userDirectory);
        when(memberProfiles.resolve(List.of("alice", "ghost", "alice"))).thenReturn(List.of(DirectoryUser.builder()
                .id("alice").displayName("Alice Rochat").givenName("Alice").avatarUrl("https://x/a.png").build()));

        // when
        var actual = sut.resolve(List.of("alice", "ghost", "alice"));

        // then
        assertThat(actual).containsOnly(
                entry("alice", new MentoringPerson("alice", "Alice Rochat", "Alice", "https://x/a.png")),
                entry("ghost", new MentoringPerson("ghost", "Deleted member", null, null)));
    }

    @Test
    void aNameIsReadForTheGivenOrganizationAndTheUnknownIsADeletedMember() {

        // given
        var sut = new PersonDirectoryAdapter(memberProfiles, userDirectory);
        when(memberProfiles.shortName("org-1", "alice")).thenReturn(Optional.of("Alice"));
        when(memberProfiles.shortName("org-1", "ghost")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.shortName("org-1", "alice")).isEqualTo("Alice");
        assertThat(sut.shortName("org-1", "ghost")).isEqualTo("Deleted member");
    }

    @Test
    void presenceIsTheDirectorysAnswerInTheGivenOrganization() {

        // given
        var sut = new PersonDirectoryAdapter(memberProfiles, userDirectory);
        when(userDirectory.presentOf("org-1", List.of("alice"))).thenReturn(Set.of("alice"));
        when(userDirectory.presentOf("org-1", List.of("ghost"))).thenReturn(Set.of());

        // when / then
        assertThat(sut.isPresent("org-1", "alice")).isTrue();
        assertThat(sut.isPresent("org-1", "ghost")).isFalse();
    }
}

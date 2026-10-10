package dev.axonia.expertise.adapter.out.identity;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.expertise.domain.Person;
import dev.axonia.identity.api.DirectoryUser;
import dev.axonia.identity.api.MemberProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@MockitoSettings
class PersonDirectoryAdapterTest {

    @Mock
    private MemberProfiles userService;

    @InjectMocks
    private PersonDirectoryAdapter sut;

    @Test
    void resolve_mapsUserToPersonWithTeamAsOrgUnit() {

        // given
        when(userService.resolve(any())).thenReturn(List.of(DirectoryUser.builder().id("u1").displayName("Alice")
                .avatarUrl("http://avatar").team("Backend").build()));

        // when
        var result = sut.resolve(List.of("u1"));

        // then
        assertThat(result).containsKey("u1");
        var person = result.get("u1");
        assertThat(person.orgUnit()).isEqualTo("Backend");
    }

    @Test
    void resolve_orgUnit_fallsBackToDepartmentWhenTeamBlank() {

        // given
        when(userService.resolve(any())).thenReturn(List
                .of(DirectoryUser.builder().id("u2").displayName("Bob").team("").department("Engineering").build()));

        // when
        var result = sut.resolve(List.of("u2"));

        // then
        assertThat(result.get("u2").orgUnit()).isEqualTo("Engineering");
    }

    @Test
    void resolve_orgUnit_fallsBackToBusinessUnitWhenTeamAndDepartmentBlank() {

        // given
        when(userService.resolve(any())).thenReturn(List.of(DirectoryUser.builder().id("u3").displayName("Carol")
                .team(null).department(null).businessUnit("Acme").build()));

        // when
        var result = sut.resolve(List.of("u3"));

        // then
        assertThat(result.get("u3").orgUnit()).isEqualTo("Acme");
    }

    @Test
    void resolve_orgUnit_nullWhenAllBlank() {

        // given
        when(userService.resolve(any())).thenReturn(List.of(DirectoryUser.builder().id("u4").displayName("Dan")
                .team(null).department("").businessUnit(null).build()));

        // when
        var result = sut.resolve(List.of("u4"));

        // then
        assertThat(result.get("u4").orgUnit()).isNull();
    }

    @Test
    void resolve_anIdTheDirectoryLeavesOut_isAnUnresolvedPerson() {

        // given - an org-less token resolves nobody
        when(userService.resolve(any())).thenReturn(List.of());

        // when
        var result = sut.resolve(List.of("ghost"));

        // then
        assertThat(result).containsExactly(entry("ghost", Person.unresolved("ghost")));
    }
}

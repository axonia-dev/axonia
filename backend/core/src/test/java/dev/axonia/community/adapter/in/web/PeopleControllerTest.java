package dev.axonia.community.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.community.api.CreditablePerson;
import dev.axonia.community.application.MembershipService;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = PeopleController.class)
class PeopleControllerTest extends ControllerTestSupport {

    @MockitoBean
    private MembershipService membershipService;

    @Test
    void searchesTheOrganizationSayingWhoIsAMember() {

        // given
        when(membershipService.searchPeople("backend-guild", "bo")).thenReturn(
                List.of(new CreditablePerson("bob", "Bob Martin", true), new CreditablePerson("bo", "Bo Ek", false)));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/people", "backend-guild").queryParam("q", "bo");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isLenientlyEqualTo("""
                [{"userId":"bob","displayName":"Bob Martin","member":true},
                 {"userId":"bo","displayName":"Bo Ek","member":false}]
                """);
    }

    @Test
    void refusesSomebodyOutsideTheCommunity() {

        // given
        when(membershipService.searchPeople("backend-guild", "bo"))
                .thenThrow(new ForbiddenException("Community membership required"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/people", "backend-guild").queryParam("q", "bo");

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void anUnknownCommunityIsNotFound() {

        // given
        when(membershipService.searchPeople("missing", "bo")).thenThrow(new NotFoundException("Community not found"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/people", "missing").queryParam("q", "bo");

        // then
        actual.assertThat().hasStatus(404);
    }
}

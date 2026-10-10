package dev.axonia.community.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.community.api.CommunityMember;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.MembershipMother;
import dev.axonia.community.application.MembershipService;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import tools.jackson.core.JacksonException;

import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = MembershipController.class)
class MembershipControllerTest extends ControllerTestSupport {

    @MockitoBean
    private MembershipService membershipService;

    @Test
    void listMembers() throws JacksonException {

        // given
        var membership = MembershipMother.any().userId("alice").build();
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "joined_at"));
        var page = new PageImpl<>(List.of(membership), pageable, 1);
        when(membershipService.listMembers(org.mockito.ArgumentMatchers.eq("backend-guild"), refEq(pageable)))
                .thenReturn(page);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members", "backend-guild").queryParam("page", "0")
                .queryParam("size", "20").queryParam("sort", "joined_at,desc");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.content.length()").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.content[0].userId").isEqualTo("alice");
        actual.assertThat().bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    @Test
    void listMembers_unsupportedSortIsRejected() {

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members", "backend-guild").queryParam("sort",
                "user_id,desc");

        // then
        actual.assertThat().hasStatus4xxClientError();
    }

    @Test
    void displayNameIsAnAcceptedSortKey() {

        // given
        var pageable = PageRequest.of(0, 20, Sort.by("display_name"));
        when(membershipService.listMembers(org.mockito.ArgumentMatchers.eq("backend-guild"), refEq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members", "backend-guild").queryParam("sort",
                "display_name");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
    }

    @Test
    void anUnknownSortKeyIsStillRefused() {

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members", "backend-guild").queryParam("sort", "email");

        // then
        actual.assertThat().hasStatus4xxClientError();
    }

    @Test
    void join() throws JacksonException {

        // given
        var membership = MembershipMother.any().userId("alice").build();
        when(membershipService.join("backend-guild")).thenReturn(membership);

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/members", "backend-guild");

        // then
        actual.assertThat().hasStatus(201).bodyJson().isEqualTo(jsonMapper.writeValueAsString(membership));
    }

    @Test
    void join_alreadyMemberConflict() {

        // given
        when(membershipService.join("backend-guild"))
                .thenThrow(new ConflictException("ALREADY_MEMBER", "already a member"));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/members", "backend-guild");

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ALREADY_MEMBER");
    }

    @Test
    void leave() {

        // when
        var actual = mvc.delete().uri("/axonia/communities/{slug}/members/me", "backend-guild");

        // then
        actual.assertThat().hasStatus(204);
    }

    @Test
    void leave_lastOrganizerConflict() {

        // given
        doThrow(new ConflictException("LAST_ORGANIZER_CANNOT_LEAVE", "nope")).when(membershipService)
                .leave("backend-guild");

        // when
        var actual = mvc.delete().uri("/axonia/communities/{slug}/members/me", "backend-guild");

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("LAST_ORGANIZER_CANNOT_LEAVE");
    }

    @Test
    void changeRole() throws JacksonException {

        // given
        var membership = MembershipMother.any().userId("bob").role(CommunityRole.ORGANIZER).build();
        when(membershipService.changeRole("backend-guild", "bob", CommunityRole.ORGANIZER)).thenReturn(membership);

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/members/{userId}/role", "backend-guild", "bob")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""
                        {"role":"ORGANIZER"}""");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(membership));
    }

    @Test
    void changeRole_nullRoleRejected() {

        // when
        var actual = mvc.put().uri("/axonia/communities/{slug}/members/{userId}/role", "backend-guild", "bob")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void myMembership() throws JacksonException {

        // given
        var membership = MembershipMother.any().userId("alice").role(CommunityRole.ORGANIZER).build();
        when(membershipService.myMembership("backend-guild")).thenReturn(membership);

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members/me", "backend-guild");

        // then
        actual.assertThat().hasStatus(200).bodyJson().isEqualTo(jsonMapper.writeValueAsString(membership));
    }

    @Test
    void myMembership_notAMember() {

        // given
        when(membershipService.myMembership("backend-guild")).thenThrow(new NotFoundException("Membership not found"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members/me", "backend-guild");

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void searchMembers() {

        // given
        when(membershipService.searchMembers("backend-guild", "bo"))
                .thenReturn(List.of(new CommunityMember("bob", "Bob Martin")));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members/search", "backend-guild").queryParam("q", "bo");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$[0].displayName")
                .isEqualTo("Bob Martin");
    }

    @Test
    void searchMembers_nonMemberForbidden() {

        // given
        when(membershipService.searchMembers("backend-guild", "bo"))
                .thenThrow(new ForbiddenException("Community membership required"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members/search", "backend-guild").queryParam("q", "bo");

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void searchMembers_unknownSlugNotFound() {

        // given
        when(membershipService.searchMembers("missing", "bo")).thenThrow(new NotFoundException("Community not found"));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/members/search", "missing").queryParam("q", "bo");

        // then
        actual.assertThat().hasStatus(404);
    }
}

package dev.axonia.learning.internal;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.community.Community;
import dev.axonia.community.CommunityService;
import dev.axonia.community.CommunityStatus;
import dev.axonia.community.MembershipService;
import dev.axonia.expertise.CommunityAreas;
import dev.axonia.expertise.ExpertiseVocabulary;
import dev.axonia.identity.DirectoryUser;
import dev.axonia.identity.UserService;
import dev.axonia.learning.LearningCommunity;
import dev.axonia.learning.LearningPerson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class LearningAdaptersTest {

    @Mock
    private CommunityService communityService;
    @Mock
    private MembershipService membershipService;
    @Mock
    private UserService userService;
    @Mock
    private ExpertiseVocabulary expertiseVocabulary;

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void communityForSlugSaysWhetherTheCommunityIsArchived() {

        var sut = new CommunityAccessAdapter(communityService, membershipService);
        when(communityService.getBySlug("legacy"))
                .thenReturn(Community.builder().id(8L).slug("legacy").status(CommunityStatus.ARCHIVED).build());
        when(communityService.getBySlug("platform"))
                .thenReturn(Community.builder().id(7L).slug("platform").status(CommunityStatus.ACTIVE).build());

        assertThat(sut.communityForSlug("legacy")).isEqualTo(new LearningCommunity(8L, true));
        assertThat(sut.communityForSlug("platform")).isEqualTo(new LearningCommunity(7L, false));
    }

    @Test
    void isMemberAsksTheMembershipService() {

        var sut = new CommunityAccessAdapter(communityService, membershipService);
        when(membershipService.isMember(7L, "alice")).thenReturn(true);

        assertThat(sut.isMember(7L, "alice")).isTrue();
    }

    @Test
    void thePersonDirectoryNamesEveryIdAndCallsTheUnresolvedADeletedMember() {

        var sut = new PersonDirectoryAdapter(userService);
        when(userService.resolve(List.of("alice", "ghost", "alice"))).thenReturn(
                List.of(DirectoryUser.builder().id("alice").displayName("Alice").avatarUrl("https://x/a.png").build()));

        assertThat(sut.resolve(List.of("alice", "ghost", "alice"))).containsOnly(
                entry("alice", new LearningPerson("alice", "Alice", "https://x/a.png")),
                entry("ghost", new LearningPerson("ghost", "Deleted member", null)));
    }

    @Test
    void theVocabularyIsExpertises() {

        var sut = new ExpertiseVocabularyAdapter(expertiseVocabulary);
        var areas = new CommunityAreas(Map.of("k8s", "kubernetes"));
        when(expertiseVocabulary.areasOf(7L)).thenReturn(areas);
        when(expertiseVocabulary.canBeAsked("platform")).thenReturn(Map.of("kubernetes", 2));
        when(expertiseVocabulary.writableName("Kubernetes")).thenReturn("kubernetes");

        sut.shareLock("acme", 7L);

        verify(expertiseVocabulary).shareLock("acme", 7L);
        assertThat(sut.areasOf(7L)).isSameAs(areas);
        assertThat(sut.canBeAsked("platform")).containsExactly(entry("kubernetes", 2));
        assertThat(sut.writableName("Kubernetes")).isEqualTo("kubernetes");
    }

    @Test
    void jwtCurrentUserReturnsTheTokenSubject() {

        authenticate();

        assertThat(new JwtCurrentUser().userId()).isEqualTo("alice-sub");
    }

    @Test
    void jwtOrganizationContextReturnsTheTenantClaim() {

        authenticate();

        assertThat(new JwtOrganizationContext().organizationId()).isEqualTo("acme");
    }

    private static void authenticate() {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}

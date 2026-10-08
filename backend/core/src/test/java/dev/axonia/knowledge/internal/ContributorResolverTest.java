package dev.axonia.knowledge.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.community.MemberMatch;
import dev.axonia.knowledge.CommunityAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link ContributorResolver#resolve} is exercised end-to-end through {@code ArtifactAttributionTest}.
 * {@link ContributorResolver#resolveCorrection} is exercised directly here.
 */
@MockitoSettings
class ContributorResolverTest {

    @Mock
    private CommunityAccess communityAccess;

    private ContributorResolver sut;

    @BeforeEach
    void setUp() {

        sut = new ContributorResolver(communityAccess);
    }

    @Test
    void resolveCorrection_refusesWithoutTheAxis_asAPlainForbidden() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(false);

        // when / then a plain ForbiddenException, never the actionable subtype: a correction never falls back to
        // the caller, so there is no field to omit
        assertThatThrownBy(() -> sut.resolveCorrection(1L, "alice", "Bob Martin"))
                .isExactlyInstanceOf(ForbiddenException.class)
                .hasMessage("Only an organizer, or a member the community lets credit contributions, may credit a "
                        + "contribution to another member.");
    }

    @Test
    void resolveCorrection_refusesABlankReferenceAsUnplaceable() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "")).thenReturn(new MemberMatch.NotFound());

        // when / then
        assertThatThrownBy(() -> sut.resolveCorrection(1L, "alice", "  ")).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("CONTRIBUTOR_NOT_FOUND");
    }

    @Test
    void resolveCorrection_refusesAnAmbiguousReference() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob")).thenReturn(new MemberMatch.Ambiguous(2));

        // when / then
        assertThatThrownBy(() -> sut.resolveCorrection(1L, "alice", "Bob")).isInstanceOf(ConflictException.class)
                .hasMessageContaining("2").hasMessageContaining("Bob").extracting("code")
                .isEqualTo("CONTRIBUTOR_AMBIGUOUS");
    }

    @Test
    void resolveCorrection_namesTheMatchedMember() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob Martin")).thenReturn(new MemberMatch.Matched("bob-id"));

        // when / then
        assertThat(sut.resolveCorrection(1L, "alice", "Bob Martin")).isEqualTo("bob-id");
    }
}

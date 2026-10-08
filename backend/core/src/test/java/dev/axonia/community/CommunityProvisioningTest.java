package dev.axonia.community;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.internal.CommunityEntity;
import dev.axonia.community.internal.CommunityKindRepository;
import dev.axonia.community.internal.CommunityRepository;
import dev.axonia.community.internal.MembershipEntity;
import dev.axonia.community.internal.MembershipRepository;
import dev.axonia.community.mother.CommunityEntityMother;
import dev.axonia.community.mother.CommunityKindEntityMother;
import dev.axonia.community.mother.MembershipEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityProvisioningTest {

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private CommunityKindRepository kindRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @InjectMocks
    private CommunityProvisioning sut;

    private static final Instant CREATED_AT = Instant.parse("2026-07-05T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-05T10:00:01Z");

    @Test
    void provisionInitial_savesActiveCommunityWithSeededKind() {

        // given
        var kind = CommunityKindEntityMother.any().id(7L).label("Community of Practice").build();
        when(kindRepository.findByOrganizationIdOrderByLabelAsc("hub-org")).thenReturn(List.of(kind));
        when(communityRepository.save(any())).thenAnswer(inv -> audited((CommunityEntity) inv.getArgument(0)));

        // when
        sut.provisionInitial(backendGuild("We build backend things"));

        // then
        var captor = ArgumentCaptor.forClass(CommunityEntity.class);
        verify(communityRepository).save(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getOrganizationId()).isEqualTo("hub-org");
        assertThat(captured.getSlug()).isEqualTo("backend-guild");
        assertThat(captured.getName()).isEqualTo("Backend Guild");
        assertThat(captured.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(captured.getKindId()).isEqualTo(7L);
        assertThat(captured.getDescription()).isEqualTo("We build backend things");
    }

    @Test
    void provisionInitial_returnsCommunityReadModelOfSavedEntity() {

        // given
        var kind = CommunityKindEntityMother.any().id(7L).label("Community of Practice").build();
        when(kindRepository.findByOrganizationIdOrderByLabelAsc("hub-org")).thenReturn(List.of(kind));
        when(communityRepository.save(any())).thenAnswer(inv -> audited((CommunityEntity) inv.getArgument(0)));

        // when
        var actual = sut.provisionInitial(backendGuild("We build backend things"));

        // then
        assertThat(actual.id()).isEqualTo(1L);
        assertThat(actual.slug()).isEqualTo("backend-guild");
        assertThat(actual.name()).isEqualTo("Backend Guild");
        assertThat(actual.kindId()).isEqualTo(7L);
        assertThat(actual.kind()).isEqualTo("Community of Practice");
        assertThat(actual.description()).isEqualTo("We build backend things");
        assertThat(actual.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(actual.memberCount()).isEqualTo(1L);
        assertThat(actual.createdAt()).isEqualTo(CREATED_AT);
        assertThat(actual.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void provisionInitial_nullTaglineStoredAsEmptyString() {

        // given
        var kind = CommunityKindEntityMother.any().id(7L).label("Community of Practice").build();
        when(kindRepository.findByOrganizationIdOrderByLabelAsc("hub-org")).thenReturn(List.of(kind));
        when(communityRepository.save(any())).thenAnswer(inv -> audited((CommunityEntity) inv.getArgument(0)));

        // when
        var actual = sut.provisionInitial(backendGuild(null));

        // then
        var captor = ArgumentCaptor.forClass(CommunityEntity.class);
        verify(communityRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEmpty();
        assertThat(actual.description()).isEmpty();
    }

    @Test
    void provisionInitial_bindsCreatorAsOrganizer() {

        // given
        var kind = CommunityKindEntityMother.any().id(7L).label("Community of Practice").build();
        when(kindRepository.findByOrganizationIdOrderByLabelAsc("hub-org")).thenReturn(List.of(kind));
        var saved = CommunityEntityMother.any().id(1L).organizationId("hub-org").build();
        when(communityRepository.save(any())).thenReturn(saved);

        // when
        sut.provisionInitial(backendGuild("We build backend things"));

        // then
        var captor = ArgumentCaptor.forClass(MembershipEntity.class);
        verify(membershipRepository).save(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getOrganizationId()).isEqualTo("hub-org");
        assertThat(captured.getCommunityId()).isEqualTo(1L);
        assertThat(captured.getUserId()).isEqualTo("dev-user");
        assertThat(captured.getRole()).isEqualTo(CommunityRole.ORGANIZER);
    }

    @Test
    void provisionInitial_missingSeededKind_throwsIllegalStateException() {

        // given
        when(kindRepository.findByOrganizationIdOrderByLabelAsc("hub-org")).thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> sut.provisionInitial(backendGuild("tagline")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enrollJoiner_savesMemberRowForTheHubCommunity() {

        // given — the hub's single community lives at slug == organization id
        var community = CommunityEntityMother.any().id(7L).organizationId("rust-hub").slug("rust-hub").name("Rust Hub")
                .build();
        when(communityRepository.findByOrganizationIdAndSlug("rust-hub", "rust-hub"))
                .thenReturn(Optional.of(community));
        when(membershipRepository.findByOrganizationIdAndCommunityIdAndUserId("rust-hub", 7L, "sub-2"))
                .thenReturn(Optional.empty());

        // when
        var enrolled = sut.enrollJoiner("rust-hub", "sub-2");

        // then — a fresh row is inserted and the insert is reported so the caller counts the joiner
        assertThat(enrolled).isTrue();
        var captor = ArgumentCaptor.forClass(MembershipEntity.class);
        verify(membershipRepository).save(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getOrganizationId()).isEqualTo("rust-hub");
        assertThat(captured.getCommunityId()).isEqualTo(7L);
        assertThat(captured.getUserId()).isEqualTo("sub-2");
        assertThat(captured.getRole()).isEqualTo(CommunityRole.MEMBER);
    }

    @Test
    void enrollJoiner_existingMembershipIsLeftUntouched() {

        // given
        var community = CommunityEntityMother.any().id(7L).organizationId("rust-hub").slug("rust-hub").name("Rust Hub")
                .build();
        when(communityRepository.findByOrganizationIdAndSlug("rust-hub", "rust-hub"))
                .thenReturn(Optional.of(community));
        when(membershipRepository.findByOrganizationIdAndCommunityIdAndUserId("rust-hub", 7L, "sub-2"))
                .thenReturn(Optional.of(MembershipEntityMother.any().organizationId("rust-hub").communityId(7L)
                        .userId("sub-2").build()));

        // when
        var enrolled = sut.enrollJoiner("rust-hub", "sub-2");

        // then — no insert, and the no-op is reported so the caller leaves the count alone
        assertThat(enrolled).isFalse();
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void enrollJoiner_missingHubCommunityIsABrokenInvariant() {

        // given
        when(communityRepository.findByOrganizationIdAndSlug("rust-hub", "rust-hub")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.enrollJoiner("rust-hub", "sub-2")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rust-hub");
    }

    private static ProvisionCommunityRequest backendGuild(String tagline) {

        return ProvisionCommunityRequest.builder().organizationId("hub-org").slug("backend-guild").name("Backend Guild")
                .tagline(tagline).creatorUserId("dev-user").build();
    }

    private static CommunityEntity audited(CommunityEntity toSave) {

        return toSave.toBuilder().id(1L).createdAt(CREATED_AT).updatedAt(UPDATED_AT).build();
    }
}

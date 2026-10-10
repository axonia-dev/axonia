package dev.axonia.community.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.ProvisionCommunityRequest;
import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.domain.CommunityKindRecordMother;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.CommunityRecordMother;
import dev.axonia.community.domain.MembershipRecord;
import dev.axonia.community.domain.MembershipRecordMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityProvisioningServiceTest {

    @Mock
    private CommunityStore communityStore;

    @Mock
    private CommunityKindStore communityKindStore;

    @Mock
    private MembershipLedger membershipLedger;

    @InjectMocks
    private CommunityProvisioningService sut;

    private static final Instant CREATED_AT = Instant.parse("2026-07-05T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-05T10:00:01Z");

    @Test
    void provisionInitial_savesActiveCommunityWithSeededKind() {

        // given
        var kind = CommunityKindRecordMother.any().id(7L).label("Community of Practice").build();
        when(communityKindStore.listByLabel("hub-org")).thenReturn(List.of(kind));
        when(communityStore.save(any())).thenAnswer(inv -> audited((CommunityRecord) inv.getArgument(0)));

        // when
        sut.provisionInitial(backendGuild("We build backend things"));

        // then
        var captor = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(captor.capture());
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
        var kind = CommunityKindRecordMother.any().id(7L).label("Community of Practice").build();
        when(communityKindStore.listByLabel("hub-org")).thenReturn(List.of(kind));
        when(communityStore.save(any())).thenAnswer(inv -> audited((CommunityRecord) inv.getArgument(0)));

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
        var kind = CommunityKindRecordMother.any().id(7L).label("Community of Practice").build();
        when(communityKindStore.listByLabel("hub-org")).thenReturn(List.of(kind));
        when(communityStore.save(any())).thenAnswer(inv -> audited((CommunityRecord) inv.getArgument(0)));

        // when
        var actual = sut.provisionInitial(backendGuild(null));

        // then
        var captor = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEmpty();
        assertThat(actual.description()).isEmpty();
    }

    @Test
    void provisionInitial_bindsCreatorAsOrganizer() {

        // given
        var kind = CommunityKindRecordMother.any().id(7L).label("Community of Practice").build();
        when(communityKindStore.listByLabel("hub-org")).thenReturn(List.of(kind));
        var saved = CommunityRecordMother.any().id(1L).organizationId("hub-org").build();
        when(communityStore.save(any())).thenReturn(saved);

        // when
        sut.provisionInitial(backendGuild("We build backend things"));

        // then
        var captor = ArgumentCaptor.forClass(MembershipRecord.class);
        verify(membershipLedger).save(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getOrganizationId()).isEqualTo("hub-org");
        assertThat(captured.getCommunityId()).isEqualTo(1L);
        assertThat(captured.getUserId()).isEqualTo("dev-user");
        assertThat(captured.getRole()).isEqualTo(CommunityRole.ORGANIZER);
    }

    @Test
    void provisionInitial_missingSeededKind_throwsIllegalStateException() {

        // given
        when(communityKindStore.listByLabel("hub-org")).thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> sut.provisionInitial(backendGuild("tagline")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enrollJoiner_savesMemberRowForTheHubCommunity() {

        // given - the hub's single community lives at slug == organization id
        var community = CommunityRecordMother.any().id(7L).organizationId("rust-hub").slug("rust-hub").name("Rust Hub")
                .build();
        when(communityStore.findBySlug("rust-hub", "rust-hub")).thenReturn(Optional.of(community));
        when(membershipLedger.find("rust-hub", 7L, "sub-2")).thenReturn(Optional.empty());

        // when
        var enrolled = sut.enrollJoiner("rust-hub", "sub-2");

        // then - a fresh row is inserted and the insert is reported so the caller counts the joiner
        assertThat(enrolled).isTrue();
        var captor = ArgumentCaptor.forClass(MembershipRecord.class);
        verify(membershipLedger).save(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getOrganizationId()).isEqualTo("rust-hub");
        assertThat(captured.getCommunityId()).isEqualTo(7L);
        assertThat(captured.getUserId()).isEqualTo("sub-2");
        assertThat(captured.getRole()).isEqualTo(CommunityRole.MEMBER);
    }

    @Test
    void enrollJoiner_existingMembershipIsLeftUntouched() {

        // given
        var community = CommunityRecordMother.any().id(7L).organizationId("rust-hub").slug("rust-hub").name("Rust Hub")
                .build();
        when(communityStore.findBySlug("rust-hub", "rust-hub")).thenReturn(Optional.of(community));
        when(membershipLedger.find("rust-hub", 7L, "sub-2")).thenReturn(Optional
                .of(MembershipRecordMother.any().organizationId("rust-hub").communityId(7L).userId("sub-2").build()));

        // when
        var enrolled = sut.enrollJoiner("rust-hub", "sub-2");

        // then - no insert, and the no-op is reported so the caller leaves the count alone
        assertThat(enrolled).isFalse();
        verify(membershipLedger, never()).save(any());
    }

    @Test
    void enrollJoiner_missingHubCommunityIsABrokenInvariant() {

        // given
        when(communityStore.findBySlug("rust-hub", "rust-hub")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.enrollJoiner("rust-hub", "sub-2")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rust-hub");
    }

    private static ProvisionCommunityRequest backendGuild(String tagline) {

        return ProvisionCommunityRequest.builder().organizationId("hub-org").slug("backend-guild").name("Backend Guild")
                .tagline(tagline).creatorUserId("dev-user").build();
    }

    private static CommunityRecord audited(CommunityRecord toSave) {

        return toSave.toBuilder().id(1L).createdAt(CREATED_AT).updatedAt(UPDATED_AT).build();
    }
}

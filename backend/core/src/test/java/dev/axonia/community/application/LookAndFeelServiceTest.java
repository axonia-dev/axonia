package dev.axonia.community.application;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.community.api.Community;
import dev.axonia.community.api.CommunityLookChanged;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.FocalPoint;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.CommunityRecordMother;
import dev.axonia.community.domain.LookAndFeelAsset;
import dev.axonia.media.api.ImageUpload;
import dev.axonia.media.api.MediaStorage;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class LookAndFeelServiceTest {

    @Mock
    private CommunityStore communityStore;

    @Mock
    private MembershipAuthorizer membershipAuthorizer;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private MediaStorage mediaStorage;

    @Mock
    private CommunityService communityService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LookAndFeelService sut;

    @BeforeEach
    void seedSeams() {

        when(organizationContext.organizationId()).thenReturn("default");
        lenient().when(currentUser.userId()).thenReturn("dev-user");
    }

    @Test
    void setAccentColor_persistsAndDelegatesToGetBySlug() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var expected = Community.builder().id(1L).slug("backend-guild").accentColor("#1AA6A0").build();
        when(communityService.getBySlug("backend-guild")).thenReturn(expected);

        // when
        var actual = sut.setAccentColor("backend-guild", "#1AA6A0");

        // then
        assertThat(actual).isSameAs(expected);
        var captor = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(captor.capture());
        assertThat(captor.getValue().getAccentColor()).isEqualTo("#1AA6A0");
        verify(membershipAuthorizer).requireOrganizerOrOpenMember("default", 1L, "dev-user", false);
    }

    @Test
    void setAccentColor_rejectsBadHex() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));

        // when / then
        assertThatThrownBy(() -> sut.setAccentColor("backend-guild", "teal")).isInstanceOf(ValidationException.class);
        verify(communityStore, never()).save(any());
    }

    @Test
    void setAccentColor_archivedRejected() {

        // given
        var entity = CommunityRecordMother.any().id(1L).status(CommunityStatus.ARCHIVED).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));

        // when / then
        assertThatThrownBy(() -> sut.setAccentColor("backend-guild", "#1AA6A0")).isInstanceOf(ConflictException.class);
    }

    @Test
    void addLookAndFeelAsset_storesAndDelegatesToGetBySlug() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var expected = Community.builder().id(1L).slug("backend-guild").badgeUrl("/public/media/resolved").build();
        when(communityService.getBySlug("backend-guild")).thenReturn(expected);
        var upload = new ImageUpload(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, "image/png", "logo.png");

        // when
        var actual = sut.addLookAndFeelAsset("backend-guild", LookAndFeelAsset.BADGE, upload);

        // then
        assertThat(actual).isSameAs(expected);
        verify(mediaStorage).store(any(), any(ByteArrayInputStream.class), eq("image/png"), eq(4L));
        verify(communityStore).save(any());
    }

    @Test
    void addLookAndFeelAsset_deletesPreviousObject() {

        // given
        var entity = CommunityRecordMother.any().id(1L).badgeKey("communities/1/badge/old.png").build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(communityService.getBySlug("backend-guild")).thenReturn(Community.builder().build());
        var upload = new ImageUpload(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, "image/png", "logo.png");

        // when
        sut.addLookAndFeelAsset("backend-guild", LookAndFeelAsset.BADGE, upload);

        // then
        verify(mediaStorage).delete("communities/1/badge/old.png");
    }

    @Test
    void removeLookAndFeelAsset_clearsKeyAndDeletesObject() {

        // given
        var entity = CommunityRecordMother.any().id(1L).heroKey("communities/1/hero/old.jpg").build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var expected = Community.builder().id(1L).slug("backend-guild").heroUrl(null).build();
        when(communityService.getBySlug("backend-guild")).thenReturn(expected);

        // when
        var actual = sut.removeLookAndFeelAsset("backend-guild", LookAndFeelAsset.HERO);

        // then
        assertThat(actual).isSameAs(expected);
        verify(mediaStorage).delete("communities/1/hero/old.jpg");
    }

    @Test
    void requireEditable_notFound() {

        // given
        when(communityStore.findBySlug("default", "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.setAccentColor("missing", "#1AA6A0")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void addLookAndFeelAsset_webpExtensionUsed() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(communityService.getBySlug("backend-guild")).thenReturn(Community.builder().build());
        var upload = new ImageUpload(new byte[]{0x52, 0x49, 0x46, 0x46}, "image/webp", "hero.webp");

        // when
        sut.addLookAndFeelAsset("backend-guild", LookAndFeelAsset.HERO, upload);

        // then
        var captor = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(captor.capture());
        assertThat(captor.getValue().getHeroKey()).endsWith(".webp");
    }

    @Test
    void addLookAndFeelAsset_jpegExtensionUsedForDefault() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(communityService.getBySlug("backend-guild")).thenReturn(Community.builder().build());
        var upload = new ImageUpload(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg", "photo.jpg");

        // when
        sut.addLookAndFeelAsset("backend-guild", LookAndFeelAsset.BADGE, upload);

        // then
        var captor = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(captor.capture());
        assertThat(captor.getValue().getBadgeKey()).endsWith(".jpg");
    }

    @Test
    void setAccentColor_publishesLookChanged() {

        // given
        var entity = CommunityRecordMother.any().id(1L).badgeKey("existing-key").build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.setAccentColor("backend-guild", "#1AA6A0");

        // then
        verify(eventPublisher).publishEvent(new CommunityLookChanged("default", "existing-key", "#1AA6A0"));
    }

    @Test
    void addBadge_publishesLookChangedWithNewKey() {

        // given
        var entity = CommunityRecordMother.any().id(1L).accentColor("#DE3B78").build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var upload = new ImageUpload(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, "image/png", "logo.png");

        // when
        sut.addLookAndFeelAsset("backend-guild", LookAndFeelAsset.BADGE, upload);

        // then
        var captor = ArgumentCaptor.forClass(CommunityLookChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().badgeKey()).startsWith("communities/");
        assertThat(captor.getValue().accentColor()).isEqualTo("#DE3B78");
    }

    @Test
    void removeBadge_publishesLookChangedWithNullKey() {

        // given
        var entity = CommunityRecordMother.any().id(1L).badgeKey("communities/1/badge/old.png").accentColor("#DE3B78")
                .build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.removeLookAndFeelAsset("backend-guild", LookAndFeelAsset.BADGE);

        // then
        verify(eventPublisher).publishEvent(new CommunityLookChanged("default", null, "#DE3B78"));
    }

    @Test
    void heroMutations_publishNoLookChanged() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var upload = new ImageUpload(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg", "photo.jpg");

        // when
        sut.addLookAndFeelAsset("backend-guild", LookAndFeelAsset.HERO, upload);
        sut.removeLookAndFeelAsset("backend-guild", LookAndFeelAsset.HERO);

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteQuietly_swallowsRuntimeException() {

        // given
        var entity = CommunityRecordMother.any().id(1L).heroKey("communities/1/hero/old.jpg").build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(entity));
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(communityService.getBySlug("backend-guild")).thenReturn(Community.builder().build());
        doThrow(new RuntimeException("store failure")).when(mediaStorage).delete(any());

        // when / then - storage exception must NOT propagate
        assertThatCode(() -> sut.removeLookAndFeelAsset("backend-guild", LookAndFeelAsset.HERO))
                .doesNotThrowAnyException();
    }

    @Test
    void storesAFocalPointForTheHero() {

        // given
        var entity = CommunityRecordMother.any().heroKey("communities/1/hero/a.jpg").build();
        when(communityStore.findBySlug("default", "guild")).thenReturn(Optional.of(entity));

        // when
        sut.setFocalPoint("guild", LookAndFeelAsset.HERO, FocalPoint.of(50, 78));

        // then
        var saved = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(saved.capture());
        assertThat(saved.getValue().getHeroFocusX()).isEqualTo(50);
        assertThat(saved.getValue().getHeroFocusY()).isEqualTo(78);
        assertThat(saved.getValue().getBadgeFocusX()).isNull();
    }

    @Test
    void storesAFocalPointForTheBadgeWithoutTouchingTheHero() {

        // given
        var entity = CommunityRecordMother.any().badgeKey("communities/1/badge/b.jpg").heroFocusX(50).heroFocusY(78)
                .build();
        when(communityStore.findBySlug("default", "guild")).thenReturn(Optional.of(entity));

        // when
        sut.setFocalPoint("guild", LookAndFeelAsset.BADGE, FocalPoint.of(20, 30));

        // then
        var saved = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(saved.capture());
        assertThat(saved.getValue().getBadgeFocusX()).isEqualTo(20);
        assertThat(saved.getValue().getBadgeFocusY()).isEqualTo(30);
        assertThat(saved.getValue().getHeroFocusX()).isEqualTo(50);
    }

    @Test
    void refusesAFocalPointForAnAssetThatHasNoImage() {

        // given
        var entity = CommunityRecordMother.any().heroKey(null).build();
        when(communityStore.findBySlug("default", "guild")).thenReturn(Optional.of(entity));

        // when / then
        assertThatThrownBy(() -> sut.setFocalPoint("guild", LookAndFeelAsset.HERO, FocalPoint.of(50, 78)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("no image");
        verify(communityStore, never()).save(any());
    }

    @Test
    void replacingAnImageClearsTheFocalPointThatDescribedTheOldOne() {

        // given
        var entity = CommunityRecordMother.any().id(1L).heroKey("communities/1/hero/old.jpg").heroFocusX(50)
                .heroFocusY(78).build();
        when(communityStore.findBySlug("default", "guild")).thenReturn(Optional.of(entity));

        // when
        sut.addLookAndFeelAsset("guild", LookAndFeelAsset.HERO, anImageUpload());

        // then
        var saved = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(saved.capture());
        assertThat(saved.getValue().getHeroFocusX()).isNull();
        assertThat(saved.getValue().getHeroFocusY()).isNull();
    }

    @Test
    void removingAnImageClearsItsFocalPoint() {

        // given
        var entity = CommunityRecordMother.any().badgeKey("communities/1/badge/b.jpg").badgeFocusX(20).badgeFocusY(30)
                .build();
        when(communityStore.findBySlug("default", "guild")).thenReturn(Optional.of(entity));

        // when
        sut.removeLookAndFeelAsset("guild", LookAndFeelAsset.BADGE);

        // then
        var saved = ArgumentCaptor.forClass(CommunityRecord.class);
        verify(communityStore).save(saved.capture());
        assertThat(saved.getValue().getBadgeFocusX()).isNull();
        assertThat(saved.getValue().getBadgeFocusY()).isNull();
    }

    private static ImageUpload anImageUpload() {

        return new ImageUpload(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4}, "image/png", "hero.png");
    }
}

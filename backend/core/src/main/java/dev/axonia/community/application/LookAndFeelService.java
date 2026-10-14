package dev.axonia.community.application;

import java.io.ByteArrayInputStream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.Community;
import dev.axonia.community.api.CommunityLookChanged;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.FocalPoint;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.LookAndFeelAsset;
import dev.axonia.media.api.ImageLimits;
import dev.axonia.media.api.ImageUpload;
import dev.axonia.media.api.ImageValidator;
import dev.axonia.media.api.MediaKeys;
import dev.axonia.media.api.MediaStorage;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LookAndFeelService {

    private final CommunityStore communityStore;
    private final MembershipAuthorizer membershipAuthorizer;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final MediaStorage mediaStorage;
    private final CommunityService communityService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Community setAccentColor(String slug, String accentColor) {

        var organizationId = organizationContext.organizationId();
        var entity = requireEditable(organizationId, slug);
        validateAccent(accentColor);

        communityStore.save(entity.toBuilder().accentColor(accentColor).build());
        eventPublisher.publishEvent(new CommunityLookChanged(organizationId, entity.getBadgeKey(), accentColor));
        return communityService.getBySlug(slug);
    }

    @Transactional
    public Community addLookAndFeelAsset(String slug, LookAndFeelAsset asset, ImageUpload upload) {

        var organizationId = organizationContext.organizationId();
        var entity = requireEditable(organizationId, slug);
        ImageValidator.validate(upload, new ImageLimits(asset.maxBytes(), asset.maxEdge()));

        var key = MediaKeys.communityAsset(entity.getId(), asset.segment(), upload.contentType());
        mediaStorage.store(key, new ByteArrayInputStream(upload.content()), upload.contentType(), upload.size());

        var previousKey = currentKey(entity, asset);
        communityStore.save(withKey(entity, asset, key));
        if (asset == LookAndFeelAsset.BADGE) {
            eventPublisher.publishEvent(new CommunityLookChanged(organizationId, key, entity.getAccentColor()));
        }
        deleteQuietly(previousKey);
        return communityService.getBySlug(slug);
    }

    @Transactional
    public Community removeLookAndFeelAsset(String slug, LookAndFeelAsset asset) {

        var organizationId = organizationContext.organizationId();
        var entity = requireEditable(organizationId, slug);
        var previousKey = currentKey(entity, asset);

        communityStore.save(withKey(entity, asset, null));
        if (asset == LookAndFeelAsset.BADGE) {
            eventPublisher.publishEvent(new CommunityLookChanged(organizationId, null, entity.getAccentColor()));
        }
        deleteQuietly(previousKey);
        return communityService.getBySlug(slug);
    }

    @Transactional
    public Community setFocalPoint(String slug, LookAndFeelAsset asset, FocalPoint focus) {

        var organizationId = organizationContext.organizationId();
        var entity = requireEditable(organizationId, slug);
        if (currentKey(entity, asset) == null) {
            throw new ValidationException("NO_IMAGE_TO_POSITION",
                    "This community has no image to position for " + asset.segment());
        }

        communityStore.save(withFocus(entity, asset, focus));
        return communityService.getBySlug(slug);
    }

    private CommunityRecord requireEditable(String organizationId, String slug) {

        var entity = communityStore.findBySlug(organizationId, slug)
                .orElseThrow(() -> new NotFoundException("Community not found: " + slug));
        membershipAuthorizer.requireOrganizerOrOpenMember(organizationId, entity.getId(), currentUser.userId(),
                entity.isCanManageLookAndFeel());
        if (entity.getStatus() == CommunityStatus.ARCHIVED) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "Cannot edit an archived community");
        }
        return entity;
    }

    private static void validateAccent(String accentColor) {

        if (accentColor != null && !accentColor.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ValidationException("INVALID_ACCENT_COLOR", "Accent color must be a hex value like #1AA6A0");
        }
    }

    private static String currentKey(CommunityRecord entity, LookAndFeelAsset asset) {

        return asset == LookAndFeelAsset.BADGE ? entity.getBadgeKey() : entity.getHeroKey();
    }

    /** The point describes the image, so it never outlives one. */
    private static CommunityRecord withKey(CommunityRecord entity, LookAndFeelAsset asset, String key) {

        var cleared = withFocus(entity, asset, null);
        return asset == LookAndFeelAsset.BADGE
                ? cleared.toBuilder().badgeKey(key).build()
                : cleared.toBuilder().heroKey(key).build();
    }

    private static CommunityRecord withFocus(CommunityRecord entity, LookAndFeelAsset asset, FocalPoint focus) {

        var x = focus == null ? null : focus.x();
        var y = focus == null ? null : focus.y();
        return asset == LookAndFeelAsset.BADGE
                ? entity.toBuilder().badgeFocusX(x).badgeFocusY(y).build()
                : entity.toBuilder().heroFocusX(x).heroFocusY(y).build();
    }

    private void deleteQuietly(String key) {

        if (key == null) {
            return;
        }
        try {
            mediaStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Failed to delete media object {}", key, e);
        }
    }
}

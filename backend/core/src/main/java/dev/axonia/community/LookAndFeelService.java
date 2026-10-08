package dev.axonia.community;

import java.io.ByteArrayInputStream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.community.internal.CommunityEntity;
import dev.axonia.community.internal.CommunityRepository;
import dev.axonia.community.internal.MembershipAuthorizer;
import dev.axonia.media.ImageUpload;
import dev.axonia.media.ImageValidator;
import dev.axonia.media.MediaKeys;
import dev.axonia.media.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LookAndFeelService {

    private final CommunityRepository communityRepository;
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

        communityRepository.save(entity.toBuilder().accentColor(accentColor).build());
        eventPublisher.publishEvent(new CommunityLookChanged(organizationId, entity.getBadgeKey(), accentColor));
        return communityService.getBySlug(slug);
    }

    @Transactional
    public Community addLookAndFeelAsset(String slug, LookAndFeelAsset asset, ImageUpload upload) {

        var organizationId = organizationContext.organizationId();
        var entity = requireEditable(organizationId, slug);
        ImageValidator.validate(upload, asset.maxBytes());

        var key = MediaKeys.communityAsset(entity.getId(), asset.segment(), upload.contentType());
        mediaStorage.store(key, new ByteArrayInputStream(upload.content()), upload.contentType(), upload.size());

        var previousKey = currentKey(entity, asset);
        communityRepository.save(withKey(entity, asset, key));
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

        communityRepository.save(withKey(entity, asset, null));
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

        communityRepository.save(withFocus(entity, asset, focus));
        return communityService.getBySlug(slug);
    }

    private CommunityEntity requireEditable(String organizationId, String slug) {

        var entity = communityRepository.findByOrganizationIdAndSlug(organizationId, slug)
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

    private static String currentKey(CommunityEntity entity, LookAndFeelAsset asset) {

        return asset == LookAndFeelAsset.BADGE ? entity.getBadgeKey() : entity.getHeroKey();
    }

    /** The point describes the image, so it never outlives one. */
    private static CommunityEntity withKey(CommunityEntity entity, LookAndFeelAsset asset, String key) {

        var cleared = withFocus(entity, asset, null);
        return asset == LookAndFeelAsset.BADGE
                ? cleared.toBuilder().badgeKey(key).build()
                : cleared.toBuilder().heroKey(key).build();
    }

    private static CommunityEntity withFocus(CommunityEntity entity, LookAndFeelAsset asset, FocalPoint focus) {

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

package dev.axonia.community.application;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.axonia.community.api.Community;
import dev.axonia.community.api.FocalPoint;
import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.domain.CommunityKindRecord;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.media.api.MediaLinks;

/** How a stored community becomes the {@link Community} every read returns. */
final class CommunityListings {

    private CommunityListings() {
    }

    static Map<Long, String> kindLabels(CommunityKindStore kinds, String organizationId) {

        return kinds.listByLabel(organizationId).stream()
                .collect(Collectors.toMap(CommunityKindRecord::getId, CommunityKindRecord::getLabel));
    }

    static Community toCommunity(CommunityRecord e, long memberCount, String kindLabel, MediaLinks mediaLinks) {

        return Community.builder().id(e.getId()).slug(e.getSlug()).name(e.getName()).kindId(e.getKindId())
                .kind(kindLabel).description(e.getDescription()).status(e.getStatus()).memberCount(memberCount)
                .badgeUrl(resolve(e.getBadgeKey(), mediaLinks)).heroUrl(resolve(e.getHeroKey(), mediaLinks))
                .accentColor(e.getAccentColor()).heroFocus(FocalPoint.fromColumns(e.getHeroFocusX(), e.getHeroFocusY()))
                .badgeFocus(FocalPoint.fromColumns(e.getBadgeFocusX(), e.getBadgeFocusY())).createdAt(e.getCreatedAt())
                .mentoringEnabled(e.isMentoringEnabled()).updatedAt(e.getUpdatedAt()).build();
    }

    private static String resolve(String key, MediaLinks mediaLinks) {

        return Optional.ofNullable(key).map(mediaLinks::urlFor).orElse(null);
    }
}

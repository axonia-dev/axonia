package dev.axonia.expertise;

import java.util.List;

/**
 * @param communityTiers
 *            the tier each community reads, from the signals that survive the visibility controls; a community where
 *            none survives is absent, so a hidden area carries none
 */
public record SelfArea(String canonicalTag, Tier tier, boolean hidden, List<AreaCommunity> communities,
        Evidence evidence, List<CommunityTier> communityTiers) {
}

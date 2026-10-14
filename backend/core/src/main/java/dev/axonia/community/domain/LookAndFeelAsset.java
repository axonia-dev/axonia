package dev.axonia.community.domain;

import dev.axonia.shared.api.error.NotFoundException;

public enum LookAndFeelAsset {

    BADGE("badge", 2L * 1024 * 1024, 512), HERO("hero", 5L * 1024 * 1024, 2560);

    private final String segment;
    private final long maxBytes;
    private final int maxEdge;

    LookAndFeelAsset(String segment, long maxBytes, int maxEdge) {

        this.segment = segment;
        this.maxBytes = maxBytes;
        this.maxEdge = maxEdge;
    }

    public String segment() {

        return segment;
    }

    public long maxBytes() {

        return maxBytes;
    }

    public int maxEdge() {

        return maxEdge;
    }

    public static LookAndFeelAsset fromPath(String value) {

        for (var asset : values()) {
            if (asset.segment.equals(value)) {
                return asset;
            }
        }
        throw new NotFoundException("Unknown look & feel asset: " + value);
    }
}

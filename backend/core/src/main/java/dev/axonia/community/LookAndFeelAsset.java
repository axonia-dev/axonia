package dev.axonia.community;

import dev.axonia.NotFoundException;

public enum LookAndFeelAsset {

    BADGE("badge", 2L * 1024 * 1024), HERO("hero", 5L * 1024 * 1024);

    private final String segment;
    private final long maxBytes;

    LookAndFeelAsset(String segment, long maxBytes) {

        this.segment = segment;
        this.maxBytes = maxBytes;
    }

    public String segment() {

        return segment;
    }

    public long maxBytes() {

        return maxBytes;
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

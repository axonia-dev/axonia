package dev.axonia.media.api;

import java.util.Arrays;
import java.util.Optional;

import static dev.axonia.media.api.HeaderBytes.fourCc;
import static dev.axonia.media.api.HeaderBytes.uint16LittleEndian;
import static dev.axonia.media.api.HeaderBytes.uint24LittleEndian;
import static dev.axonia.media.api.HeaderBytes.unsigned;

/** The canvas size of a WebP file, from whichever of its three first-chunk layouts it uses. */
final class WebpDimensions {

    private static final int CHUNK_PAYLOAD = 20;
    private static final int LOSSY_END = 30;
    private static final int LOSSLESS_END = 25;
    private static final int EXTENDED_END = 30;
    private static final byte[] VP8_START_CODE = {(byte) 0x9D, 0x01, 0x2A};
    private static final int VP8L_SIGNATURE = 0x2F;
    private static final int FOURTEEN_BITS = 0x3FFF;

    private WebpDimensions() {
    }

    static Optional<ImageDimensions> read(byte[] content) {

        if (content.length < CHUNK_PAYLOAD || !"WEBP".equals(fourCc(content, 8))) {
            return Optional.empty();
        }
        return switch (fourCc(content, 12)) {
            case "VP8 " -> lossy(content);
            case "VP8L" -> lossless(content);
            case "VP8X" -> extended(content);
            default -> Optional.empty();
        };
    }

    private static Optional<ImageDimensions> lossy(byte[] content) {

        if (content.length < LOSSY_END || !Arrays.equals(content, 23, 26, VP8_START_CODE, 0, 3)) {
            return Optional.empty();
        }
        return ImageDimensions.of(uint16LittleEndian(content, 26) & FOURTEEN_BITS,
                uint16LittleEndian(content, 28) & FOURTEEN_BITS);
    }

    private static Optional<ImageDimensions> lossless(byte[] content) {

        if (content.length < LOSSLESS_END || unsigned(content, CHUNK_PAYLOAD) != VP8L_SIGNATURE) {
            return Optional.empty();
        }
        var bits = uint16LittleEndian(content, 21) | uint16LittleEndian(content, 23) << 16;
        return ImageDimensions.of((bits & FOURTEEN_BITS) + 1, (bits >>> 14 & FOURTEEN_BITS) + 1);
    }

    private static Optional<ImageDimensions> extended(byte[] content) {

        if (content.length < EXTENDED_END) {
            return Optional.empty();
        }
        return ImageDimensions.of(uint24LittleEndian(content, 24) + 1, uint24LittleEndian(content, 27) + 1);
    }
}

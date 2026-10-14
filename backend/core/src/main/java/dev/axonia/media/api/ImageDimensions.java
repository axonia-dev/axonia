package dev.axonia.media.api;

import java.util.Optional;
import java.util.Set;

import static dev.axonia.media.api.HeaderBytes.fourCc;
import static dev.axonia.media.api.HeaderBytes.int32BigEndian;
import static dev.axonia.media.api.HeaderBytes.uint16BigEndian;
import static dev.axonia.media.api.HeaderBytes.unsigned;

/**
 * Width and height read from an image's header alone. Nothing is decoded: a guard that decoded the image to measure it
 * would spend on the server exactly the memory it exists to spare the browser.
 */
record ImageDimensions(int width, int height) {

    private static final int PNG_IHDR_END = 24;
    private static final int JPEG_FIRST_MARKER = 2;
    private static final int JPEG_MIN_SEGMENT_LENGTH = 2;
    private static final int JPEG_SOF_HEADER = 7;
    private static final int JPEG_SOS = 0xDA;
    private static final int JPEG_EOI = 0xD9;
    private static final int MARKER_PREFIX = 0xFF;
    private static final Set<Integer> JPEG_START_OF_FRAME = Set.of(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA,
            0xCB, 0xCD, 0xCE, 0xCF);
    private static final Set<Integer> JPEG_STANDALONE = Set.of(0x01, 0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7);

    int longestEdge() {

        return Math.max(width, height);
    }

    static Optional<ImageDimensions> read(byte[] content, String contentType) {

        return switch (contentType) {
            case "image/png" -> png(content);
            case "image/jpeg" -> jpeg(content);
            default -> WebpDimensions.read(content);
        };
    }

    static Optional<ImageDimensions> of(int width, int height) {

        return width > 0 && height > 0 ? Optional.of(new ImageDimensions(width, height)) : Optional.empty();
    }

    private static Optional<ImageDimensions> png(byte[] content) {

        if (content.length < PNG_IHDR_END || !"IHDR".equals(fourCc(content, 12))) {
            return Optional.empty();
        }
        return of(int32BigEndian(content, 16), int32BigEndian(content, 20));
    }

    private static Optional<ImageDimensions> jpeg(byte[] content) {

        var position = JPEG_FIRST_MARKER;
        while (position + 1 < content.length) {
            var marker = unsigned(content, position + 1);
            if (unsigned(content, position) != MARKER_PREFIX || marker == MARKER_PREFIX) {
                position++;
            } else if (JPEG_START_OF_FRAME.contains(marker)) {
                return startOfFrame(content, position + 2);
            } else {
                position = nextSegment(content, position, marker);
            }
        }
        return Optional.empty();
    }

    private static int nextSegment(byte[] content, int position, int marker) {

        var lengthAt = position + 2;
        if (JPEG_STANDALONE.contains(marker)) {
            return lengthAt;
        }
        if (marker == JPEG_SOS || marker == JPEG_EOI || content.length < lengthAt + 2) {
            return content.length;
        }
        var length = uint16BigEndian(content, lengthAt);
        return length < JPEG_MIN_SEGMENT_LENGTH ? content.length : lengthAt + length;
    }

    private static Optional<ImageDimensions> startOfFrame(byte[] content, int segment) {

        if (content.length < segment + JPEG_SOF_HEADER) {
            return Optional.empty();
        }
        return of(uint16BigEndian(content, segment + 5), uint16BigEndian(content, segment + 3));
    }
}

package dev.axonia.media.api;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import dev.axonia.shared.api.error.ValidationException;

public final class ImageValidator {

    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp");

    private static final Map<String, byte[]> MAGIC = Map.of("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/webp",
            new byte[]{0x52, 0x49, 0x46, 0x46});

    private static final long BYTES_PER_KB = 1024;
    private static final long BYTES_PER_MB = 1024 * 1024;

    private ImageValidator() {
    }

    public static void validate(ImageUpload upload, ImageLimits limits) {

        checkPresent(upload);
        checkContentType(upload);
        checkSize(upload, limits.maxBytes());
        checkMagic(upload);
        checkDimensions(upload, limits.maxEdge());
    }

    private static void checkPresent(ImageUpload upload) {

        if (upload.content() == null || upload.content().length == 0) {
            throw new ValidationException("INVALID_IMAGE", "The uploaded file is empty");
        }
    }

    private static void checkContentType(ImageUpload upload) {

        if (upload.contentType() == null || !ALLOWED.contains(upload.contentType())) {
            throw new ValidationException("UNSUPPORTED_MEDIA_TYPE", "Unsupported image type. Use PNG, JPEG or WebP");
        }
    }

    private static void checkSize(ImageUpload upload, long maxBytes) {

        if (upload.content().length > maxBytes) {
            throw new ValidationException("FILE_TOO_LARGE",
                    "Image exceeds the maximum size of " + formatLimit(maxBytes));
        }
    }

    /**
     * KB below 1 MB, MB at or above it. A limit under 1 MB - the reaction picker's 128 KB bound - used to divide by
     * {@code 1024 * 1024} unconditionally and report "0 MB", which is honest arithmetic but not a sentence a member can
     * act on.
     */
    private static String formatLimit(long maxBytes) {

        return maxBytes < BYTES_PER_MB
                ? formatUnit(maxBytes, BYTES_PER_KB, "KB")
                : formatUnit(maxBytes, BYTES_PER_MB, "MB");
    }

    /**
     * Whole units render without a decimal point; anything else keeps one, rounded, rather than being truncated away.
     * Rounds to the nearest tenth as a {@code long} rather than comparing {@code double}s for equality, which SpotBugs
     * flags as unreliable even where, as here, every input is exact.
     * <p>
     * The space before the unit is not cosmetic: the client-side guard in the upload dialogs writes "128 KB", and a
     * member meets whichever of the two refusals fires first. Written two ways they read as two different limits.
     */
    private static String formatUnit(long maxBytes, long unitBytes, String unit) {

        var tenths = Math.round(maxBytes * 10.0 / unitBytes);
        var text = tenths % 10 == 0 ? String.valueOf(tenths / 10) : String.valueOf(tenths / 10.0);
        return text + " " + unit;
    }

    private static void checkMagic(ImageUpload upload) {

        var magic = MAGIC.get(upload.contentType());
        if (magic != null && !startsWith(upload.content(), magic)) {
            throw new ValidationException("INVALID_IMAGE", "File content does not match its declared type");
        }
    }

    private static void checkDimensions(ImageUpload upload, int maxEdge) {

        var dimensions = ImageDimensions.read(upload.content(), upload.contentType()).orElseThrow(
                () -> new ValidationException("INVALID_IMAGE", "Image dimensions cannot be read from the file"));
        if (dimensions.longestEdge() > maxEdge) {
            throw new ValidationException("IMAGE_TOO_LARGE",
                    "Image exceeds the maximum of " + maxEdge + " pixels on its longest side");
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {

        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }
}

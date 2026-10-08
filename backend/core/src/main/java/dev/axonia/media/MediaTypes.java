package dev.axonia.media;

import lombok.experimental.UtilityClass;

/** Maps an image content type to the file extension used in storage keys, and a key back to its content type. */
@UtilityClass
public class MediaTypes {

    public String extensionFor(String contentType) {

        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    public String contentTypeFor(String key) {

        if (key.endsWith(".png")) {
            return "image/png";
        }
        return key.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }
}

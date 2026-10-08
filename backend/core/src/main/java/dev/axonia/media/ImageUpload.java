package dev.axonia.media;

/** The raw bytes and metadata of an image as it enters the domain from the web edge. */
public record ImageUpload(byte[] content, String contentType, String originalFilename) {

    public long size() {

        return content == null ? 0 : content.length;
    }
}

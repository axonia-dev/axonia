package dev.axonia.media.api;

/** The smallest byte arrays whose headers declare a given size: enough for ImageValidator, never a decodable image. */
public final class ImageHeaders {

    private ImageHeaders() {
    }

    public static byte[] png(int width, int height) {

        var bytes = new byte[33];
        put(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R');
        putBigEndian32(bytes, 16, width);
        putBigEndian32(bytes, 20, height);
        return bytes;
    }

    public static byte[] jpeg(int width, int height) {

        var bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0, 0, 11, 8, 0, 0, 0, 0, 1, 1, 0x11, 0};
        putBigEndian16(bytes, 7, height);
        putBigEndian16(bytes, 9, width);
        return bytes;
    }

    public static byte[] webpLossy(int width, int height) {

        var bytes = riff("VP8 ", 30);
        put(bytes, 20, 0, 0, 0, 0x9D, 0x01, 0x2A);
        putLittleEndian(bytes, 26, width, 2);
        putLittleEndian(bytes, 28, height, 2);
        return bytes;
    }

    public static byte[] webpLossless(int width, int height) {

        var bytes = riff("VP8L", 25);
        bytes[20] = 0x2F;
        putLittleEndian(bytes, 21, width - 1 | (height - 1) << 14, 4);
        return bytes;
    }

    public static byte[] webpExtended(int width, int height) {

        var bytes = riff("VP8X", 30);
        putLittleEndian(bytes, 24, width - 1, 3);
        putLittleEndian(bytes, 27, height - 1, 3);
        return bytes;
    }

    public static byte[] riff(String chunk, int length) {

        var bytes = new byte[length];
        put(bytes, 0, 'R', 'I', 'F', 'F');
        putLittleEndian(bytes, 4, length - 8, 4);
        put(bytes, 8, 'W', 'E', 'B', 'P');
        for (var i = 0; i < 4; i++) {
            bytes[12 + i] = (byte) chunk.charAt(i);
        }
        putLittleEndian(bytes, 16, length - 20, 4);
        return bytes;
    }

    private static void put(byte[] bytes, int offset, int... values) {

        for (var i = 0; i < values.length; i++) {
            bytes[offset + i] = (byte) values[i];
        }
    }

    private static void putBigEndian16(byte[] bytes, int offset, int value) {

        put(bytes, offset, value >>> 8, value);
    }

    private static void putBigEndian32(byte[] bytes, int offset, int value) {

        put(bytes, offset, value >>> 24, value >>> 16, value >>> 8, value);
    }

    private static void putLittleEndian(byte[] bytes, int offset, int value, int width) {

        for (var i = 0; i < width; i++) {
            bytes[offset + i] = (byte) (value >>> 8 * i);
        }
    }
}

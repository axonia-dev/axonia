package dev.axonia.media.api;

import java.nio.charset.StandardCharsets;

final class HeaderBytes {

    private HeaderBytes() {
    }

    static String fourCc(byte[] content, int offset) {

        return new String(content, offset, 4, StandardCharsets.US_ASCII);
    }

    static int unsigned(byte[] content, int offset) {

        return content[offset] & 0xFF;
    }

    static int uint16BigEndian(byte[] content, int offset) {

        return unsigned(content, offset) << 8 | unsigned(content, offset + 1);
    }

    static int int32BigEndian(byte[] content, int offset) {

        return uint16BigEndian(content, offset) << 16 | uint16BigEndian(content, offset + 2);
    }

    static int uint16LittleEndian(byte[] content, int offset) {

        return unsigned(content, offset) | unsigned(content, offset + 1) << 8;
    }

    static int uint24LittleEndian(byte[] content, int offset) {

        return uint16LittleEndian(content, offset) | unsigned(content, offset + 2) << 16;
    }
}

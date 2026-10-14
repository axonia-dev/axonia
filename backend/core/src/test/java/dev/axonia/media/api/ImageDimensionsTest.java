package dev.axonia.media.api;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImageDimensionsTest {

    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";
    private static final String WEBP = "image/webp";

    @Test
    void read_png() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.png(10_087, 7594), PNG);

        // then
        assertThat(actual).contains(new ImageDimensions(10_087, 7594));
    }

    @Test
    void read_png_truncatedBeforeTheDimensions() {

        // given
        var truncated = Arrays.copyOf(ImageHeaders.png(1, 1), 23);

        // when
        var actual = ImageDimensions.read(truncated, PNG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_png_firstChunkIsNotTheHeader() {

        // given
        var bytes = ImageHeaders.png(1, 1);
        bytes[12] = 'X';

        // when
        var actual = ImageDimensions.read(bytes, PNG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_png_zeroWidth() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.png(0, 1), PNG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_png_zeroHeight() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.png(1, 0), PNG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_png_widthBeyondASignedInt() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.png(-1, 1), PNG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_jpeg_startOfFrameFirst() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.jpeg(640, 480), JPEG);

        // then
        assertThat(actual).contains(new ImageDimensions(640, 480));
    }

    @Test
    void read_jpeg_walksSegmentsAndFillBytesBeforeTheFrame() {

        // given - APP0, a fill byte, then DHT (C4), which sits in the SOF range but is not one
        var app0 = bytes(0xFF, 0xE0, 0, 4, 0, 0);
        var huffman = bytes(0xFF, 0xFF, 0xC4, 0, 3, 0);
        var frame = Arrays.copyOfRange(ImageHeaders.jpeg(3000, 2000), 2, 15);
        var bytes = concat(bytes(0xFF, 0xD8), app0, huffman, frame);

        // when
        var actual = ImageDimensions.read(bytes, JPEG);

        // then
        assertThat(actual).contains(new ImageDimensions(3000, 2000));
    }

    @Test
    void read_jpeg_progressiveFrame() {

        // given
        var bytes = ImageHeaders.jpeg(800, 600);
        bytes[3] = (byte) 0xC2;

        // when
        var actual = ImageDimensions.read(bytes, JPEG);

        // then
        assertThat(actual).contains(new ImageDimensions(800, 600));
    }

    @ParameterizedTest
    @ValueSource(ints = {0xDA, 0xD9})
    void read_jpeg_scanOrEndBeforeAnyFrame(int marker) {

        // given
        var bytes = concat(bytes(0xFF, 0xD8, 0xFF, marker, 0, 4, 0, 0),
                Arrays.copyOfRange(ImageHeaders.jpeg(1, 1), 2, 15));

        // when
        var actual = ImageDimensions.read(bytes, JPEG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_jpeg_segmentLengthBelowItsOwnSize() {

        // given
        var bytes = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 1, 0xFF, 0xC0);

        // when
        var actual = ImageDimensions.read(bytes, JPEG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_jpeg_truncatedInsideASegmentLength() {

        // when
        var actual = ImageDimensions.read(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0), JPEG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_jpeg_truncatedInsideTheFrame() {

        // given
        var truncated = Arrays.copyOf(ImageHeaders.jpeg(1, 1), 10);

        // when
        var actual = ImageDimensions.read(truncated, JPEG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_jpeg_onlyTheMagic() {

        // when
        var actual = ImageDimensions.read(bytes(0xFF, 0xD8, 0xFF), JPEG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_jpeg_garbageWithNoMarker() {

        // when
        var actual = ImageDimensions.read(bytes(0xFF, 0xD8, 0x00, 0xC0, 0, 0), JPEG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_jpeg_skipsStrayBytesBetweenSegments() {

        // given - APP0, then garbage that is not a marker, then the frame
        var app0 = bytes(0xFF, 0xE0, 0, 2);
        var frame = Arrays.copyOfRange(ImageHeaders.jpeg(1024, 768), 2, 15);
        var bytes = concat(bytes(0xFF, 0xD8), app0, bytes(0x00, 0x12, 0xC0), frame);

        // when
        var actual = ImageDimensions.read(bytes, JPEG);

        // then
        assertThat(actual).contains(new ImageDimensions(1024, 768));
    }

    @ParameterizedTest
    @ValueSource(ints = {0xD0, 0xD7, 0x01})
    void read_jpeg_stepsOverAStandaloneMarkerWithoutReadingALength(int marker) {

        // given - read as a length, the frame marker after it would skip past the frame
        var frame = Arrays.copyOfRange(ImageHeaders.jpeg(1200, 900), 2, 15);
        var bytes = concat(bytes(0xFF, 0xD8, 0xFF, marker), frame);

        // when
        var actual = ImageDimensions.read(bytes, JPEG);

        // then
        assertThat(actual).contains(new ImageDimensions(1200, 900));
    }

    @Test
    void read_webpLossy() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.webpLossy(2560, 1440), WEBP);

        // then
        assertThat(actual).contains(new ImageDimensions(2560, 1440));
    }

    @Test
    void read_webpLossy_ignoresTheScaleBits() {

        // given
        var bytes = ImageHeaders.webpLossy(100, 50);
        bytes[27] |= (byte) 0xC0;
        bytes[29] |= (byte) 0xC0;

        // when
        var actual = ImageDimensions.read(bytes, WEBP);

        // then
        assertThat(actual).contains(new ImageDimensions(100, 50));
    }

    @Test
    void read_webpLossy_wrongStartCode() {

        // given
        var bytes = ImageHeaders.webpLossy(1, 1);
        bytes[23] = 0;

        // when
        var actual = ImageDimensions.read(bytes, WEBP);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_webpLossy_truncated() {

        // when
        var actual = ImageDimensions.read(Arrays.copyOf(ImageHeaders.webpLossy(1, 1), 29), WEBP);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_webpLossless() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.webpLossless(16_384, 300), WEBP);

        // then
        assertThat(actual).contains(new ImageDimensions(16_384, 300));
    }

    @Test
    void read_webpLossless_wrongSignature() {

        // given
        var bytes = ImageHeaders.webpLossless(1, 1);
        bytes[20] = 0;

        // when
        var actual = ImageDimensions.read(bytes, WEBP);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_webpLossless_truncated() {

        // when
        var actual = ImageDimensions.read(Arrays.copyOf(ImageHeaders.webpLossless(1, 1), 24), WEBP);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_webpExtended() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.webpExtended(16_777_216, 2), WEBP);

        // then
        assertThat(actual).contains(new ImageDimensions(16_777_216, 2));
    }

    @Test
    void read_webpExtended_truncated() {

        // when
        var actual = ImageDimensions.read(Arrays.copyOf(ImageHeaders.webpExtended(1, 1), 29), WEBP);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_webp_unknownChunk() {

        // when
        var actual = ImageDimensions.read(ImageHeaders.riff("ALPH", 30), WEBP);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_webp_riffThatIsNotWebp() {

        // given
        var bytes = ImageHeaders.webpExtended(1, 1);
        bytes[8] = 'A';

        // when
        var actual = ImageDimensions.read(bytes, WEBP);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void read_webp_onlyTheMagic() {

        // when
        var actual = ImageDimensions.read(bytes('R', 'I', 'F', 'F'), WEBP);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void longestEdge() {

        // when / then
        assertThat(new ImageDimensions(300, 900).longestEdge()).isEqualTo(900);
        assertThat(new ImageDimensions(900, 300).longestEdge()).isEqualTo(900);
    }

    private static byte[] bytes(int... values) {

        var bytes = new byte[values.length];
        for (var i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {

        var length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        var joined = new byte[length];
        var offset = 0;
        for (var part : parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }
        return joined;
    }
}

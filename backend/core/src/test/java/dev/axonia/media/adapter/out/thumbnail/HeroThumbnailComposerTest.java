package dev.axonia.media.adapter.out.thumbnail;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;

import org.junit.jupiter.api.Test;

import dev.axonia.media.adapter.out.thumbnail.HeroThumbnailComposer.Slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HeroThumbnailComposerTest {

    private static final Color ACCENT = new Color(0xde3b78);

    private final HeroThumbnailComposer composer = new HeroThumbnailComposer();

    @Test
    void composesAJpegAtTheBandsDoubleResolution() throws IOException {

        // when
        var thumbnail = decode(composer.compose(verticalGradient(600, 1200), slice(0)));

        // then
        assertThat(thumbnail.getWidth()).isEqualTo(1104);
        assertThat(thumbnail.getHeight()).isEqualTo(280);
    }

    @Test
    void theNextSessionOfACommunityTakesAnotherBandOfTheHero() throws IOException {

        // given
        var hero = verticalGradient(600, 1200);

        // when
        var first = decode(composer.compose(hero, slice(0)));
        var second = decode(composer.compose(hero, slice(1)));
        var third = decode(composer.compose(hero, slice(2)));

        // then
        assertThat(pixels(second)).isNotEqualTo(pixels(first));
        assertThat(pixels(third)).isNotEqualTo(pixels(first)).isNotEqualTo(pixels(second));
    }

    @Test
    void aSliceBeyondTheHeroIsClampedInsideIt() throws IOException {

        // given
        var hero = verticalGradient(600, 1200);

        // when
        var far = decode(composer.compose(hero, slice(40)));
        var further = decode(composer.compose(hero, slice(42)));

        // then
        assertThat(pixels(far)).isEqualTo(pixels(further));
    }

    /** A 4:1 banner has no height to step down, so the next sessions step sideways through a closer window. */
    @Test
    void theNextSessionsOfABannerHeroStepSideways() throws IOException {

        // given
        var banner = horizontalGradient(1600, 400);

        // when
        var first = decode(composer.compose(banner, slice(0)));
        var second = decode(composer.compose(banner, slice(1)));
        var third = decode(composer.compose(banner, slice(2)));

        // then
        assertThat(pixels(second)).isNotEqualTo(pixels(first));
        assertThat(pixels(third)).isNotEqualTo(pixels(first)).isNotEqualTo(pixels(second));
    }

    /**
     * Over a dark flat hero the scrim's fall to the right moves each channel a few levels across hundreds of pixels;
     * left at 8 bits every column in a band shares one value, which reads as vertical bands. Dithered, no long run of
     * columns averages to the same level.
     */
    @Test
    void theScrimFallsSmoothlyRatherThanInBands() throws IOException {

        // given
        var hero = new BufferedImage(1104, 280, BufferedImage.TYPE_INT_RGB);
        var graphics = hero.createGraphics();
        graphics.setColor(new Color(0x2a1a30));
        graphics.fillRect(0, 0, 1104, 280);
        graphics.dispose();

        // when
        var thumbnail = decode(composer.compose(hero, slice(0)));

        // then
        for (var shift : new int[]{16, 8, 0}) {
            assertThat(longestRunOfFlatColumns(thumbnail, shift, 662)).as("channel >> %d", shift).isLessThan(40);
        }
    }

    /** Colour is kept at full resolution: subsampled chroma smears the accent scrim into blocks. */
    @Test
    void theJpegKeepsFullResolutionColour() throws IOException {

        // when
        var jpeg = composer.compose(verticalGradient(600, 1200), slice(0));

        // then
        var reader = ImageIO.getImageReadersByFormatName("jpeg").next();
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            reader.setInput(input);
            var tree = reader.getImageMetadata(0).getAsTree("javax_imageio_jpeg_image_1.0");
            var components = ((IIOMetadataNode) tree).getElementsByTagName("componentSpec");
            assertThat(components.getLength()).isEqualTo(3);
            for (var i = 0; i < components.getLength(); i++) {
                var component = (IIOMetadataNode) components.item(i);
                assertThat(component.getAttribute("HsamplingFactor")).isEqualTo("1");
                assertThat(component.getAttribute("VsamplingFactor")).isEqualTo("1");
            }
        } finally {
            reader.dispose();
        }
    }

    @Test
    void aHeroWiderThanTheBandIsCroppedAcrossItsWidth() throws IOException {

        // when
        var thumbnail = decode(composer.compose(verticalGradient(4000, 200), slice(0)));

        // then
        assertThat(thumbnail.getWidth()).isEqualTo(1104);
        assertThat(thumbnail.getHeight()).isEqualTo(280);
    }

    @Test
    void theScrimIsHeaviestBehindTheTitleOnTheLeft() throws IOException {

        // given
        var white = new BufferedImage(1104, 280, BufferedImage.TYPE_INT_RGB);
        var graphics = white.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 1104, 280);
        graphics.dispose();

        // when
        var thumbnail = decode(composer.compose(white, slice(0)));

        // then
        assertThat(luminance(thumbnail.getRGB(4, 140))).isLessThan(luminance(thumbnail.getRGB(1099, 140)));
        assertThat(luminance(thumbnail.getRGB(640, 140))).as("held heavy across the title's side")
                .isCloseTo(luminance(thumbnail.getRGB(4, 140)), within(3.0));
    }

    @Test
    void aWebpHeroDecodesThroughThePlainImageReader() throws IOException {

        // when
        BufferedImage hero;
        try (var webp = getClass().getResourceAsStream("/roundup/hero.webp")) {
            hero = ImageIO.read(webp);
        }

        // then
        assertThat(hero).isNotNull();
        assertThat(decode(composer.compose(hero, slice(0))).getWidth()).isEqualTo(1104);
    }

    private static Slice slice(int index) {

        return Slice.builder().focusX(50).focusY(50).index(index).accent(ACCENT).build();
    }

    private static BufferedImage verticalGradient(int width, int height) {

        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (var y = 0; y < height; y++) {
            var shade = 255 * y / (height - 1);
            for (var x = 0; x < width; x++) {
                image.setRGB(x, y, new Color(shade, 255 - shade, shade / 2).getRGB());
            }
        }
        return image;
    }

    private static BufferedImage horizontalGradient(int width, int height) {

        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (var x = 0; x < width; x++) {
            var shade = 255 * x / (width - 1);
            for (var y = 0; y < height; y++) {
                image.setRGB(x, y, new Color(shade, 255 - shade, shade / 2).getRGB());
            }
        }
        return image;
    }

    private static BufferedImage decode(byte[] jpeg) throws IOException {

        assertThat(jpeg).startsWith((byte) 0xFF, (byte) 0xD8);
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    /** The longest run of adjacent columns, from {@code fromX} on, whose mean in one channel stays the same. */
    private static int longestRunOfFlatColumns(BufferedImage image, int shift, int fromX) {

        var longest = 1;
        var run = 1;
        var previous = Double.NaN;
        for (var x = fromX; x < image.getWidth(); x++) {
            var sum = 0.0;
            for (var y = 0; y < image.getHeight(); y++) {
                sum += image.getRGB(x, y) >> shift & 0xff;
            }
            var mean = sum / image.getHeight();
            run = Math.abs(mean - previous) < 0.02 ? run + 1 : 1;
            previous = mean;
            longest = Math.max(longest, run);
        }
        return longest;
    }

    private static int[] pixels(BufferedImage image) {

        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static double luminance(int rgb) {

        var color = new Color(rgb);
        return Arrays.stream(new int[]{color.getRed(), color.getGreen(), color.getBlue()}).average().orElseThrow();
    }
}

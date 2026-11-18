package dev.axonia.media.adapter.out.thumbnail;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.SplittableRandom;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadataNode;

import org.springframework.stereotype.Component;

import lombok.Builder;

/**
 * A session poster's band: a slice of the community hero at the band's 552:140 aspect at 2x, blurred, under an accent
 * scrim that holds heavy across the title's side and only lifts over the right 40%, so text baked into the hero never
 * reads next to the session title.
 */
@Component
public class HeroThumbnailComposer {

    static final int WIDTH = 1104;
    static final int HEIGHT = 280;

    private static final double SLICE_STEP = 0.35;
    private static final double BANNER_ZOOM = 0.6;
    private static final int BLUR_SIGMA = 16;
    private static final float JPEG_QUALITY = 0.90f;
    private static final String JPEG_METADATA = "javax_imageio_jpeg_image_1.0";
    private static final int CHANNELS = 3;
    private static final double SCRIM_HOLD = 0.6;
    private static final float SCRIM_HELD_ALPHA = 0.92f;
    private static final float SCRIM_EDGE_ALPHA = 0.30f;
    private static final double DITHER_LEVELS = 1.5;
    private static final long DITHER_SEED = 0x5eed;
    private static final Color INK = new Color(0x120c16);

    @Builder
    public record Slice(int focusX, int focusY, int index, Color accent) {
    }

    public byte[] compose(BufferedImage hero, Slice slice) throws IOException {

        var band = cropped(hero, slice);
        var channels = blurred(band);
        scrim(channels, slice.accent());
        dither(channels, band);
        return jpeg(band);
    }

    private static BufferedImage cropped(BufferedImage hero, Slice slice) {

        var heroWidth = hero.getWidth();
        var heroHeight = hero.getHeight();
        var windowWidth = (double) heroWidth;
        var windowHeight = windowWidth * HEIGHT / WIDTH;
        if (windowHeight > heroHeight) {
            windowHeight = heroHeight;
            windowWidth = windowHeight * WIDTH / HEIGHT;
        }
        var centreX = heroWidth * slice.focusX() / 100.0;
        var centreY = heroHeight * slice.focusY() / 100.0;
        if (heroHeight - windowHeight >= SLICE_STEP * windowHeight) {
            centreY += offsetOf(slice.index()) * SLICE_STEP * windowHeight;
        } else if (slice.index() > 0) {
            windowWidth *= BANNER_ZOOM;
            windowHeight *= BANNER_ZOOM;
            centreX += offsetOf(slice.index()) * SLICE_STEP * windowWidth;
        }
        var left = clamp(centreX - windowWidth / 2, heroWidth - windowWidth);
        var top = clamp(centreY - windowHeight / 2, heroHeight - windowHeight);

        var band = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var graphics = band.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(hero, 0, 0, WIDTH, HEIGHT, (int) Math.round(left), (int) Math.round(top),
                (int) Math.round(left + windowWidth), (int) Math.round(top + windowHeight), INK, null);
        graphics.dispose();
        return band;
    }

    /**
     * Slice 0 sits on the focus point, then 1 below, 2 above, 3 further below, and so on. A banner too flat to step
     * down takes a closer window for every slice past 0 and steps it sideways instead.
     */
    private static int offsetOf(int index) {

        var distance = (index + 1) / 2;
        return index % 2 == 0 ? -distance : distance;
    }

    private static double clamp(double start, double max) {

        return Math.clamp(start, 0, Math.max(0, max));
    }

    /** The band as three channel planes, blurred and kept in floating point until the one rounding in dither. */
    private static float[][] blurred(BufferedImage band) {

        var kernel = gaussianKernel();
        var pixels = band.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
        var channels = new float[CHANNELS][pixels.length];
        for (var c = 0; c < CHANNELS; c++) {
            var shift = 16 - 8 * c;
            for (var i = 0; i < pixels.length; i++) {
                channels[c][i] = pixels[i] >> shift & 0xff;
            }
            channels[c] = convolve(convolve(channels[c], kernel, 1, 0), kernel, 0, 1);
        }
        return channels;
    }

    private static float[] gaussianKernel() {

        var radius = 3 * BLUR_SIGMA;
        var kernel = new float[2 * radius + 1];
        var sum = 0f;
        for (var i = -radius; i <= radius; i++) {
            kernel[i + radius] = (float) Math.exp(-(i * i) / (2.0 * BLUR_SIGMA * BLUR_SIGMA));
            sum += kernel[i + radius];
        }
        for (var i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }

    /** One pass of a separable blur along (dx, dy), repeating the edge pixels beyond the band. */
    private static float[] convolve(float[] source, float[] kernel, int dx, int dy) {

        var radius = kernel.length / 2;
        var target = new float[source.length];
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                float sum = 0;
                for (var i = -radius; i <= radius; i++) {
                    var sampleX = Math.clamp(x + i * dx, 0, WIDTH - 1);
                    var sampleY = Math.clamp(y + i * dy, 0, HEIGHT - 1);
                    sum += source[sampleY * WIDTH + sampleX] * kernel[i + radius];
                }
                target[y * WIDTH + x] = sum;
            }
        }
        return target;
    }

    /** The accent scrim, held heavy across the title's side and lifting over the right 40%, blended per pixel. */
    private static void scrim(float[][] channels, Color accent) {

        var dark = mix(accent, 0.30).getRGBColorComponents(null);
        var light = mix(accent, 0.45).getRGBColorComponents(null);
        for (var x = 0; x < WIDTH; x++) {
            var fall = (float) Math.max(0, (x / (WIDTH - 1.0) - SCRIM_HOLD) / (1 - SCRIM_HOLD));
            var alpha = SCRIM_HELD_ALPHA + (SCRIM_EDGE_ALPHA - SCRIM_HELD_ALPHA) * fall;
            for (var c = 0; c < CHANNELS; c++) {
                var tone = 255 * alpha * (dark[c] + (light[c] - dark[c]) * fall);
                for (var y = 0; y < HEIGHT; y++) {
                    channels[c][y * WIDTH + x] = tone + (1 - alpha) * channels[c][y * WIDTH + x];
                }
            }
        }
    }

    /**
     * Rounds to 8 bits through a seeded triangular noise of about 1.5 levels per channel. A dark scrim falls only a few
     * levels across hundreds of pixels, which plain rounding shows as vertical bands; the noise trades them for grain
     * fine enough to vanish under the blur's softness, and the same band always gets the same grain.
     */
    private static void dither(float[][] channels, BufferedImage band) {

        var noise = new SplittableRandom(DITHER_SEED);
        var pixels = new int[WIDTH * HEIGHT];
        for (var i = 0; i < pixels.length; i++) {
            var pixel = 0;
            for (var c = 0; c < CHANNELS; c++) {
                var grain = DITHER_LEVELS * (noise.nextDouble() + noise.nextDouble() - 1);
                pixel = pixel << 8 | (int) Math.clamp(Math.round(channels[c][i] + grain), 0, 255);
            }
            pixels[i] = pixel;
        }
        band.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);
    }

    private static Color mix(Color accent, double share) {

        return new Color(channel(accent.getRed(), INK.getRed(), share),
                channel(accent.getGreen(), INK.getGreen(), share), channel(accent.getBlue(), INK.getBlue(), share));
    }

    private static int channel(int accent, int ink, double share) {

        return (int) Math.round(accent * share + ink * (1 - share));
    }

    private static byte[] jpeg(BufferedImage band) throws IOException {

        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);
        var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(band), params);
        var tree = (IIOMetadataNode) metadata.getAsTree(JPEG_METADATA);
        var components = tree.getElementsByTagName("componentSpec");
        for (var i = 0; i < components.getLength(); i++) {
            var component = (IIOMetadataNode) components.item(i);
            component.setAttribute("HsamplingFactor", "1");
            component.setAttribute("VsamplingFactor", "1");
        }
        metadata.setFromTree(JPEG_METADATA, tree);
        var bytes = new ByteArrayOutputStream();
        try (var output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(band, null, metadata), params);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}

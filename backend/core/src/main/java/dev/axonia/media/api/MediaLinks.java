package dev.axonia.media.api;

/**
 * The address a browser or a mail client loads a stored image from. Nothing stores one: it is minted from the key when
 * a response is built, so moving where media is served from never orphans a picture.
 *
 * <p>
 * This is the form for the application's own pages, which load an image from their own origin. Anything read outside
 * those pages, a mail above all, has no origin to resolve against and takes {@link AbsoluteMediaLinks} instead. The two
 * are separate types on purpose: a class injects the one that matches where its output is read, and
 * {@code MediaAddressFormTest} fails a build in which an off-page package takes this one.
 */
public class MediaLinks {

    public static final String PATH = "/public/media";

    private final String base;

    public MediaLinks(String base) {

        this.base = base;
    }

    public String urlFor(String key) {

        return base + "/" + key;
    }
}

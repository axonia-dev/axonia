package dev.axonia.media.api;

/**
 * The absolute address of a stored image, for output that is read outside the application's pages: a mail, and whatever
 * later leaves for another origin or another program. A page response takes {@link MediaLinks}.
 */
public class AbsoluteMediaLinks {

    private final String base;

    public AbsoluteMediaLinks(String base) {

        this.base = base;
    }

    public String urlFor(String key) {

        return base + "/" + key;
    }
}

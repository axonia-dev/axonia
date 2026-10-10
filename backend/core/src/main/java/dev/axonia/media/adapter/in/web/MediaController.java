package dev.axonia.media.adapter.in.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import dev.axonia.media.api.MediaContent;
import dev.axonia.media.api.MediaKeys;
import dev.axonia.media.api.MediaLinks;
import dev.axonia.media.api.MediaStorage;
import dev.axonia.media.api.MediaTypes;
import lombok.RequiredArgsConstructor;

/**
 * Serves stored images. Public by URL: the unguessable file name in the key is the capability, and nothing here asks
 * who is calling. Only a key in a shape the backend itself writes reaches the store.
 *
 * <p>
 * The response is written by hand, not returned as a resource: a resource return type answers a {@code Range} header on
 * a stream with 416, and an image must load whatever a client sends.
 */
@RestController
@RequiredArgsConstructor
class MediaController {

    private static final String PREFIX = MediaLinks.PATH + "/";
    private static final String CACHE_CONTROL = "public, max-age=31536000, s-maxage=86400, immutable";
    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; sandbox";

    private final MediaStorage mediaStorage;

    @RequestMapping(path = MediaLinks.PATH + "/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    void serve(HttpServletRequest request, HttpServletResponse response) throws IOException {

        var key = keyOf(request.getRequestURI());
        if (!MediaKeys.isServable(key)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var head = HttpMethod.HEAD.matches(request.getMethod());
        var needsNoBody = head || request.getHeader(HttpHeaders.IF_NONE_MATCH) != null;
        if (needsNoBody && answeredWithoutOpening(key, head, request, response)) {
            return;
        }
        // A mismatched revalidation stamped the image's caching headers; no failure from here may keep them.
        response.reset();
        var content = mediaStorage.open(key);
        if (content.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var sent = false;
        try {
            send(key, content.get(), request, response);
            sent = true;
        } finally {
            if (!sent && !response.isCommitted()) {
                response.reset();
            }
        }
    }

    /**
     * A HEAD and a revalidation need to know the object exists and how long it is, never its bytes, so the store is
     * asked for the length alone. True when the response is complete: no such object, a 304, or a HEAD.
     */
    private boolean answeredWithoutOpening(String key, boolean head, HttpServletRequest request,
            HttpServletResponse response) {

        var length = mediaStorage.length(key);
        if (length.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }
        if (notModified(key, request, response)) {
            return true;
        }
        if (head) {
            describe(key, length.get(), response);
        }
        return head;
    }

    /** Stamps what every answer for an existing object carries, the tag included, and says whether that is a 304. */
    private static boolean notModified(String key, HttpServletRequest request, HttpServletResponse response) {

        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        return new ServletWebRequest(request, response).checkNotModified("\"" + key + "\"");
    }

    private static void describe(String key, long length, HttpServletResponse response) {

        response.setContentType(MediaTypes.contentTypeFor(key));
        if (length >= 0) {
            response.setContentLengthLong(length);
        }
    }

    private static String keyOf(String requestUri) {

        return requestUri.startsWith(PREFIX) ? requestUri.substring(PREFIX.length()) : "";
    }

    /** Closing the object is part of sending it: a close that fails before the response commits is a failed send. */
    private static void send(String key, MediaContent content, HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        try (var stream = content.stream()) {
            notModified(key, request, response);
            describe(key, content.length(), response);
            stream.transferTo(response.getOutputStream());
        }
    }
}

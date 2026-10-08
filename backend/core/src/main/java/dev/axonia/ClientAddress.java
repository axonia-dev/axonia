package dev.axonia;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Who is calling, for the purpose of rate limiting.
 *
 * <p>
 * The address is read from the header our own edge stamps, never from {@code X-Forwarded-For}: a proxy appends to a
 * caller-supplied forwarded header rather than replacing it, so its leftmost entry is chosen by the caller. The edge
 * gate is what makes the stamp trustworthy - a request that did not come through the edge never reaches a limiter.
 *
 * <p>
 * The socket fallback exists for tests, not for the health probe: all three callers of this class sit on gated paths,
 * and the exempt probe never reaches a limiter or the diagnostics endpoint, so it never asks. In production, reaching
 * the fallback means the edge stopped stamping, which is why it logs and why a diagnostic reports it as
 * {@code socket-fallback} rather than hiding it.
 */
@Slf4j
public final class ClientAddress {

    public static final String HEADER = "X-Axonia-Client-Ip";

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {

        var stamped = request.getHeader(HEADER);
        if (!isUsable(stamped)) {
            log.warn("No client address stamp on {}; falling back to the socket address", request.getRequestURI());
            return request.getRemoteAddr();
        }
        return stamped.strip();
    }

    public static boolean isStamped(HttpServletRequest request) {

        return isUsable(request.getHeader(HEADER));
    }

    /**
     * The one definition of "there is a stamp here". Shared rather than repeated because the two callers report on each
     * other: {@link #isStamped} is what the diagnostics endpoint prints as {@code source}, so any drift between them
     * would have the diagnostic claim {@code edge-stamp} for an address {@link #of} took from the socket.
     */
    private static boolean isUsable(String stamped) {

        return stamped != null && !stamped.isBlank();
    }
}

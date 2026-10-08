package dev.axonia.events;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.ClientAddress;
import dev.axonia.RateLimitDecision;
import dev.axonia.events.internal.CalendarFeedRateLimiter;
import dev.axonia.events.internal.SubscriptionTokens;
import lombok.RequiredArgsConstructor;

/**
 * The anonymous calendar feed. Lives OUTSIDE {@code /axonia} like {@code /public/hubs} and {@code /public/invites}: the
 * security chain permits everything outside the app prefix, and holding a valid token is what authorizes the scoped
 * read.
 *
 * <p>
 * Three checks the surrounding filters do not perform, because {@code /public/**} is outside them:
 * {@code OrganizationLifecycleFilter.shouldNotFilter} returns true for this prefix, so a SUSPENDED or CLOSED
 * organization would otherwise keep publishing its schedule to every subscriber indefinitely; membership is re-read on
 * every fetch, so leaving a community empties nothing silently but answers 404; and the endpoint is rate limited, since
 * clients poll unattended and nothing here asks for a credential.
 *
 * <p>
 * Anything short of a valid token, an ACTIVE organization and a current membership is a uniform 404, so dead, forged
 * and revoked links are indistinguishable. That uniformity is decided in {@link CalendarSubscriptionService}, and this
 * controller's part in it is to add nothing that would tell those four cases apart - no status of its own, no body of
 * its own, and no header one refusal carries that another does not.
 *
 * <p>
 * Both budgets are spent before the token is looked up, so neither refusal says anything about whether a token
 * resolves.
 */
@RestController
@RequestMapping("/public/calendar")
@RequiredArgsConstructor
public class PublicCalendarController {

    private static final String CALENDAR = "text/calendar;charset=UTF-8";

    /** A header value is one line. A community name is whatever its members typed. */
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private final CalendarSubscriptionService calendarSubscriptionService;
    private final CalendarFeedRateLimiter rateLimiter;
    private final Clock clock;

    /**
     * The stamp is recorded on a {@code 304} as well as on a {@code 200}: a client that asks and is told nothing
     * changed is a subscription that is alive, and the member's only evidence that the URL they pasted works is that
     * this endpoint says so.
     *
     * <p>
     * The conditional request is answered by Spring rather than by a comparison written here. {@code ETag} and
     * {@code If-None-Match} are handed to {@code HttpEntityMethodProcessor}, which routes them through
     * {@code ServletWebRequest.checkNotModified} and turns a match into a bodiless {@code 304}. RFC 9110 requires the
     * weak comparison function for {@code GET} and permits a list of validators and a {@code W/} prefix, none of which
     * a hand-rolled string equality gets right - and every one of which an unattended poller is entitled to send.
     *
     * <p>
     * {@code Cache-Control: no-cache, private} rather than the application-wide default, which is the only response
     * header this endpoint overrides. That default carries {@code no-store}, which asks a client to keep nothing of the
     * response, and a validator it was told not to keep is one it can never send back: the {@code ETag} and the
     * {@code 304} it exists for would be dead on arrival. {@code no-cache} keeps every fetch revalidating against this
     * endpoint, so a schedule still cannot go stale, and {@code private} keeps one member's schedule out of a shared
     * proxy on the way.
     */
    @GetMapping(value = "/{token}.ics", produces = CALENDAR)
    ResponseEntity<String> feed(@PathVariable String token,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest request) {

        var caller = rateLimiter.allowCaller(ClientAddress.of(request));
        if (!caller.allowed()) {
            return refusal(caller);
        }
        var subscription = rateLimiter.allowSubscription(token);
        if (!subscription.allowed()) {
            return refusal(subscription);
        }
        var feed = calendarSubscriptionService.feed(token, clock.instant());
        calendarSubscriptionService.recordFetch(token, userAgent);
        return ResponseEntity.ok().eTag(etagOf(feed.ics())).cacheControl(CacheControl.noCache().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionOf(feed))
                .contentType(MediaType.parseMediaType(CALENDAR)).body(feed.ics());
    }

    /** Bodiless on purpose: an unattended calendar client has nothing to do with a problem document. */
    private static ResponseEntity<String> refusal(RateLimitDecision decision) {

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds())).build();
    }

    /**
     * A strong validator over the exact document served, so an unattended poll that finds nothing changed costs one
     * empty response instead of a whole schedule.
     *
     * <p>
     * The digest is {@link SubscriptionTokens}' rather than one of this class's own. Both want the single hash every
     * JVM is required to provide, and a second copy of it would mean a second {@code NoSuchAlgorithmException} catch
     * that no test can reach and that the coverage gate would have to be told to ignore.
     */
    private static String etagOf(String ics) {

        return "\"" + SubscriptionTokens.hash(ics) + "\"";
    }

    /**
     * Inline, because a calendar client consumes the document rather than saving it, and named after the community so a
     * member who does open it in a file browser can tell one subscription from another.
     *
     * <p>
     * Control characters are dropped before the name reaches the header. {@link ContentDisposition} percent-encodes the
     * {@code filename*} half but escapes only quotes and backslashes in the plain {@code filename} half, so a name
     * carrying CR or LF would arrive there intact. A community name is validated only as non-blank, and while writing
     * one takes an authenticated editor inside the organization, an anonymous surface should not be where that
     * distinction has to hold.
     */
    private static String dispositionOf(CalendarFeed feed) {

        var name = CONTROL_CHARACTERS.matcher(feed.calendarName()).replaceAll("");
        return ContentDisposition.inline().filename(name + ".ics", StandardCharsets.UTF_8).build().toString();
    }
}

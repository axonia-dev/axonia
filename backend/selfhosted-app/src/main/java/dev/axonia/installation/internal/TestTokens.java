package dev.axonia.installation.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

/**
 * {@code v1.<key id>.<expiry in epoch seconds>.<HMAC>}: proof that these values, at this revision, were accepted by the
 * mail server in a test by this person in the last ten minutes. The HMAC covers every part, each prefixed by its
 * length, under the settings key the token names, so an instance still on a previous key during a rotation checks a
 * token an updated one issued, and the other way round.
 */
@Component
class TestTokens {

    static final Duration VALIDITY = Duration.ofMinutes(10);

    private static final String VERSION = "v1";

    private static final String PURPOSE = "axonia/settings/test-token";

    private static final Pattern EPOCH_SECONDS = Pattern.compile("\\d{1,12}");

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SettingsKeys keys;

    private final Clock clock;

    TestTokens(SettingsKeys keys, Clock clock) {

        this.keys = keys;
        this.clock = clock;
    }

    record Tested(String fingerprint, long revision, String actor) {
    }

    record Issued(String token, Instant expiresAt) {
    }

    /** Valid for ten minutes from the instant the test was accepted, truncated to the second. */
    Issued issue(Tested tested, Instant acceptedAt) {

        var key = keys.current();
        var expiresAt = acceptedAt.truncatedTo(ChronoUnit.SECONDS).plus(VALIDITY);
        var expires = expiresAt.getEpochSecond();
        return new Issued(String.join(".", VERSION, key.id(), Long.toString(expires), mac(key, expires, tested)),
                expiresAt);
    }

    boolean verify(String token, Tested tested) {

        var parts = token.split("\\.", 4);
        if (parts.length != 4 || !VERSION.equals(parts[0]) || !EPOCH_SECONDS.matcher(parts[2]).matches()) {
            return false;
        }
        var expires = Long.parseLong(parts[2]);
        return clock.instant().getEpochSecond() < expires && keys.byId(parts[1])
                .map(key -> MessageDigest.isEqual(mac(key, expires, tested).getBytes(StandardCharsets.US_ASCII),
                        parts[3].getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    private static String mac(SettingsKey key, long expires, Tested tested) {

        var message = Stream.of(VERSION, key.id(), Long.toString(expires), Long.toString(tested.revision()),
                tested.actor(), tested.fingerprint()).map(part -> part.length() + ":" + part)
                .collect(Collectors.joining());
        return ENCODER.encodeToString(Hmac.sign(key.derive(PURPOSE), message.getBytes(StandardCharsets.UTF_8)));
    }
}

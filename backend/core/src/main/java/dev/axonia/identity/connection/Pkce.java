package dev.axonia.identity.connection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

import dev.axonia.TokenHasher;

/** RFC 7636 with the S256 method only. */
public final class Pkce {

    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");

    private Pkce() {
    }

    public static boolean isChallenge(String value) {
        return value != null && CHALLENGE.matcher(value).matches();
    }

    public static boolean matches(String verifier, String challenge) {

        if (verifier == null || challenge == null || !VERIFIER.matcher(verifier).matches()) {
            return false;
        }
        var computed = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(TokenHasher.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                challenge.getBytes(StandardCharsets.US_ASCII));
    }
}

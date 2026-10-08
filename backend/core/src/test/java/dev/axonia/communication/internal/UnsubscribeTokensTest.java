package dev.axonia.communication.internal;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.MessageType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one HMAC guard between an anonymous mail footer link and a real preference write: {@link #verify} must accept
 * exactly what {@link #sign} produced with the same secret, and refuse everything else - tampered, wrongly signed,
 * malformed - identically ({@code Optional.empty()}), never a distinguishable failure.
 */
class UnsubscribeTokensTest {

    private static final String SECRET = "test-unsubscribe-secret";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UnsubscribeTokens tokens = new UnsubscribeTokens(CommunicationPropertiesMother.withSecret(SECRET));

    @Test
    void aSignedTokenRoundTrips() {

        var token = tokens.sign("user-1", "org-1", MessageType.SESSION_WRAP_UP);

        assertThat(tokens.verify(token)).contains(new UnsubscribeClaim("user-1", "org-1", MessageType.SESSION_WRAP_UP));
    }

    /**
     * The token's payload is itself base64url-encoded (not just the signature - see the class javadoc on why), so
     * tampering means corrupting one character of the encoded string rather than substituting a literal substring:
     * flipping the first character still decodes to valid bytes (any base64url character is a legal replacement for any
     * other), just different ones, which is exactly what a real tamper attempt looks like from the outside.
     */
    @Test
    void aTamperedTokenIsRefused() {

        var token = tokens.sign("user-1", "org-1", MessageType.SESSION_WRAP_UP);
        var tampered = flipFirstCharacter(token);

        assertThat(tokens.verify(tampered)).isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() {

        var other = new UnsubscribeTokens(CommunicationPropertiesMother.withSecret("a-different-secret"));

        assertThat(tokens.verify(other.sign("user-1", "org-1", MessageType.SESSION_WRAP_UP))).isEmpty();
    }

    /**
     * The trap this test guards: Spring's binder does not fail on an unresolved {@code ${...}} placeholder, it binds
     * the placeholder's own literal text. Without {@code CommunicationProperties.unsubscribeSecret}'s {@code Pattern},
     * a PROD boot with no {@code AXONIA_UNSUBSCRIBE_SECRET} set would sign every token with the string
     * {@code "${AXONIA_UNSUBSCRIBE_SECRET}"} - committed to this repository, guessable by anybody who ever read it.
     */
    @Test
    void anUnresolvedPlaceholderRefusesToBoot() {

        assertThatThrownBy(() -> validate(CommunicationPropertiesMother.withSecret("${AXONIA_UNSUBSCRIBE_SECRET}")))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void aTokenWithNoSeparatorIsRefused() {

        assertThat(tokens.verify("not-a-token-at-all")).isEmpty();
    }

    @Test
    void aPayloadThatIsNotValidBase64IsRefused() {

        var encodedSignature = ENCODER.encodeToString("signature".getBytes(StandardCharsets.UTF_8));

        assertThat(tokens.verify("not-base64!!!." + encodedSignature)).isEmpty();
    }

    @Test
    void aSignatureThatIsNotValidBase64IsRefused() {

        var encodedPayload = ENCODER.encodeToString("user-1:org-1:session_wrap_up".getBytes(StandardCharsets.UTF_8));

        assertThat(tokens.verify(encodedPayload + ".not-base64!!!")).isEmpty();
    }

    /**
     * A payload {@link UnsubscribeTokens#sign} itself produced always has exactly three {@code :}-separated fields, so
     * this can only happen to a token this class never minted - a forgery that, against all odds, still carries a valid
     * signature over a malformed payload. {@link #signRaw} is the only way to construct one: it signs with the same
     * secret {@link #tokens} verifies against but bypasses {@code sign}'s own well-formed shape.
     */
    @Test
    void aValidlySignedButMalformedPayloadIsRefused() {

        assertThat(tokens.verify(signRaw("user-1:org-1"))).isEmpty();
    }

    /** Same shape of forgery as above, this time with a field count that parses but a type code that is malformed. */
    @Test
    void aValidlySignedButMalformedMessageTypeIsRefused() {

        assertThat(tokens.verify(signRaw("user-1:org-1:Not-A-Code"))).isEmpty();
    }

    /**
     * {@code parseClaim}'s fixed three-way split on {@code :} would otherwise silently misparse a {@code userId} or
     * {@code organizationId} carrying one, turning a forwarding bug into a wrongly-targeted write instead of a token
     * that safely fails to verify. {@code sign} refuses to mint such a token at all.
     */
    @Test
    void signRejectsAUserIdContainingTheFieldSeparator() {

        assertThatThrownBy(() -> tokens.sign("user:1", "org-1", MessageType.SESSION_WRAP_UP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signRejectsAnOrganizationIdContainingTheFieldSeparator() {

        assertThatThrownBy(() -> tokens.sign("user-1", "org:1", MessageType.SESSION_WRAP_UP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String flipFirstCharacter(String value) {

        var flipped = value.charAt(0) == 'A' ? 'B' : 'A';
        return flipped + value.substring(1);
    }

    private static String signRaw(String payload) {

        try {
            var payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var signature = mac.doFinal(payloadBytes);
            return ENCODER.encodeToString(payloadBytes) + "." + ENCODER.encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validate(CommunicationProperties properties) {

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(properties);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
        }
    }
}

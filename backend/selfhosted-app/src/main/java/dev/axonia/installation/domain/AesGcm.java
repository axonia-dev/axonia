package dev.axonia.installation.domain;

import java.security.GeneralSecurityException;
import java.util.Optional;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM; the transformation is a constructor argument only so a test can reach the catch. */
public final class AesGcm {

    public static final AesGcm STANDARD = new AesGcm("AES/GCM/NoPadding");

    private static final int TAG_BITS = 128;

    private final String transformation;

    AesGcm(String transformation) {

        this.transformation = transformation;
    }

    public byte[] encrypt(byte[] key, byte[] nonce, byte[] associatedData, byte[] plaintext) {

        try {
            return cipher(Cipher.ENCRYPT_MODE, key, nonce, associatedData).doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw unavailable(e);
        }
    }

    /** Empty when the key, the nonce, the associated data or the ciphertext is not the one sealed. */
    public Optional<byte[]> decrypt(byte[] key, byte[] nonce, byte[] associatedData, byte[] ciphertext) {

        try {
            return Optional.of(cipher(Cipher.DECRYPT_MODE, key, nonce, associatedData).doFinal(ciphertext));
        } catch (AEADBadTagException e) {
            return Optional.empty();
        } catch (GeneralSecurityException e) {
            throw unavailable(e);
        }
    }

    private Cipher cipher(int mode, byte[] key, byte[] nonce, byte[] associatedData) throws GeneralSecurityException {

        var cipher = Cipher.getInstance(transformation);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(associatedData);
        return cipher;
    }

    private IllegalStateException unavailable(GeneralSecurityException cause) {

        return new IllegalStateException(transformation + " is unavailable on this JVM", cause);
    }
}

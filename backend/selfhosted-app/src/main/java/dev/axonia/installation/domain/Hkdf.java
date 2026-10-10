package dev.axonia.installation.domain;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;

import lombok.experimental.UtilityClass;

/** HKDF-SHA256 with no salt; the algorithm is a parameter only so a test can reach the catch. */
@UtilityClass
class Hkdf {

    private static final String ALGORITHM = "HKDF-SHA256";

    static byte[] derive(byte[] material, String purpose, int length) {

        return derive(ALGORITHM, material, purpose, length);
    }

    static byte[] derive(String algorithm, byte[] material, String purpose, int length) {

        try {
            return KDF.getInstance(algorithm).deriveData(HKDFParameterSpec.ofExtract().addIKM(material)
                    .thenExpand(purpose.getBytes(StandardCharsets.UTF_8), length));
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(algorithm + " is unavailable on this JVM", e);
        }
    }
}

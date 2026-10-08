package dev.axonia.media.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where uploaded media lives: {@code local} disk, an {@code s3} object store (any store speaking the S3 API), or
 * {@code azure-blob}.
 *
 * <p>
 * What every type needs sits directly under {@code app.media}, and what one type needs under that type's name. The
 * binder hands an unresolved {@code ${...}} over as its literal text, so every setting that says where the store is or
 * how to sign in refuses one at startup; otherwise a deploy missing a variable would look healthy and fail the first
 * upload.
 */
@Validated
@ConfigurationProperties(prefix = "app.media")
public record MediaStorageProperties(String type, Local local, @Valid S3 s3, @Valid AzureBlob azureBlob) {

    private static final String RESOLVED = "(?!.*\\$\\{).*";
    private static final String UNRESOLVED_MESSAGE = "must be a resolved value, not an unresolved property placeholder";

    public MediaStorageProperties {

        local = local == null ? new Local(null) : local;
        s3 = s3 == null ? new S3(null, null, null, false, null, null) : s3;
        azureBlob = azureBlob == null ? new AzureBlob(null, null) : azureBlob;
    }

    public record Local(String basePath) {
    }

    public record S3(@Pattern(regexp = RESOLVED, message = UNRESOLVED_MESSAGE) String endpoint,
            @Pattern(regexp = RESOLVED, message = UNRESOLVED_MESSAGE) String bucket,
            @Pattern(regexp = RESOLVED, message = UNRESOLVED_MESSAGE) String region, boolean pathStyle,
            @Pattern(regexp = RESOLVED, message = UNRESOLVED_MESSAGE) String accessKey,
            @Pattern(regexp = RESOLVED, message = UNRESOLVED_MESSAGE) String secretKey) {
    }

    /**
     * There is no key setting: the platform's identity, or a service principal the environment names, is the way in.
     */
    public record AzureBlob(@Pattern(regexp = RESOLVED, message = UNRESOLVED_MESSAGE) String endpoint,
            @Pattern(regexp = RESOLVED, message = UNRESOLVED_MESSAGE) String container) {
    }
}

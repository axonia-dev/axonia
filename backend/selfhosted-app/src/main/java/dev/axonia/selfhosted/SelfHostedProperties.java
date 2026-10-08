package dev.axonia.selfhosted;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The one company a Self-hosted installation serves, and how its identity provider's tokens are read. Every value is
 * required unless it has a default, and none may keep an unresolved placeholder: the configuration binder hands one
 * over as its literal text rather than failing, so each string refuses the two characters that start one.
 *
 * <p>
 * The organization id and the subject claim are permanent once the installation has started: the id is the key every
 * row is stored under, and the subject claim is what each person's account is matched on.
 */
@Validated
@ConfigurationProperties("axonia.selfhosted")
public record SelfHostedProperties(@NotNull @Valid Organization organization,
        @NotBlank @Pattern(regexp = RESOLVED, message = UNRESOLVED) String ownerGroup, @NotNull @Valid Oidc oidc) {

    static final String RESOLVED = "(?!.*\\$\\{).*";

    static final String UNRESOLVED = "must be a resolved value, not an unresolved property placeholder";

    /** The Organization the installation founds on its first start and serves from then on. */
    public record Organization(
            @NotBlank @Size(max = 63) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "must be lowercase letters and digits, optionally joined by single hyphens") String id,
            @NotBlank @Size(max = 120) @Pattern(regexp = RESOLVED, message = UNRESOLVED) String name) {
    }

    /** The company's identity provider, and the claims of its access tokens axonia reads. */
    public record Oidc(
            @NotBlank @Pattern(regexp = "(?!.*\\$\\{)https?://\\S+", message = "must be an absolute http or https URL, with no unresolved property placeholder") String issuerUri,
            @NotBlank @Pattern(regexp = RESOLVED, message = UNRESOLVED) String audience,
            @DefaultValue("sub") @NotBlank @Pattern(regexp = RESOLVED, message = UNRESOLVED) String subjectClaim,
            @DefaultValue("groups") @NotBlank @Pattern(regexp = RESOLVED, message = UNRESOLVED) String groupsClaim,
            @DefaultValue( {
                    "email",
                    "preferred_username"}) @NotEmpty List<@NotBlank @Pattern(regexp = RESOLVED, message = UNRESOLVED) String> emailClaims,
            @DefaultValue("name") @NotBlank @Pattern(regexp = RESOLVED, message = UNRESOLVED) String nameClaim){
    }
}

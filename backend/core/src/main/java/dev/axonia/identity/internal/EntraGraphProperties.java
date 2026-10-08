package dev.axonia.identity.internal;

import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * axonia's own Microsoft Entra app registration, used for Microsoft Graph calls.
 *
 * <p>
 * These identify <strong>axonia</strong> and are therefore global. The <em>customer</em> is identified by the
 * {@code aadTenantId} carried on each Organization's SSO connection, because one deployment federates many customers.
 *
 * <p>
 * The app registration must be multi-tenant and hold the {@code GroupMember.Read.All} and {@code ProfilePhoto.Read.All}
 * application permissions (not delegated), with admin consent granted in each customer tenant.
 *
 * <p>
 * The binder does not fail on an unresolved {@code ${...}} - it hands over the literal placeholder text, which would
 * otherwise compose a consent link a customer's global administrator cannot use. {@code Pattern} catches that at boot.
 * A profile with no Graph app configured, which is every local and test context, binds null and passes: {@code Pattern}
 * ignores null by definition. Do not "fix" that with {@code @NotBlank}.
 */
@Validated
@ConfigurationProperties(prefix = "axonia.idp.entra")
record EntraGraphProperties(
        @Pattern(regexp = "(?!.*\\$\\{).*", message = "must be a resolved value, not an unresolved property placeholder") String clientId,
        @Pattern(regexp = "(?!.*\\$\\{).*", message = "must be a resolved value, not an unresolved property placeholder") String clientSecret) {

    boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}

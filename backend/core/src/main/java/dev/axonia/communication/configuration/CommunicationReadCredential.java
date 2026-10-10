package dev.axonia.communication.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validates {@code axonia.tenant-discovery.datasource.password} independently of {@code TenantDiscoveryPool}'s own
 * binding of the same property onto Spring's {@code DataSourceProperties} - a framework class this module cannot add a
 * constraint to. The configuration binder does NOT fail on an unresolved {@code ${...}} placeholder, it binds the
 * placeholder's own literal text; without this, a PROD boot missing {@code DATABASE_COMMUNICATION_READ_PASSWORD} would
 * hand Hikari the string {@code "${DATABASE_COMMUNICATION_READ_PASSWORD}"} as a real password, and the only symptom
 * would be every scheduled job's tick failing to authenticate with no clue why - a background job nobody watches
 * degrading far more quietly than a request path would. Same shape {@code CommunicationProperties.unsubscribeSecret}
 * and {@code EdgeProperties.sharedSecrets} already use for their own PROD secrets.
 */
@Validated
@ConfigurationProperties(prefix = "axonia.tenant-discovery.datasource")
record CommunicationReadCredential(
        @NotBlank @Pattern(regexp = "^(?!.*\\$\\{).+$", message = "must not contain an unresolved placeholder") String password) {
}

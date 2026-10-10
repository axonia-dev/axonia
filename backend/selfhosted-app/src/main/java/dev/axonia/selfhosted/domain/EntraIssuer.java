package dev.axonia.selfhosted.domain;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Reads the Microsoft Entra tenant out of a workforce tenant's access-token issuer, v2 or v1, in the global cloud. A
 * multi-tenant authority names no tenant, and a sovereign cloud's Graph is not the one axonia calls, so neither is read
 * as Entra.
 */
@UtilityClass
public class EntraIssuer {

    private static final String TENANT = "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";

    private static final List<Pattern> ISSUERS = List.of(
            Pattern.compile("https://login\\.microsoftonline\\.com/" + TENANT + "/v2\\.0/?"),
            Pattern.compile("https://sts\\.windows\\.net/" + TENANT + "/?"));

    public Optional<String> tenantOf(String issuerUri) {
        return ISSUERS.stream().map(issuer -> issuer.matcher(issuerUri)).filter(Matcher::matches)
                .map(matcher -> matcher.group(1)).findFirst();
    }
}

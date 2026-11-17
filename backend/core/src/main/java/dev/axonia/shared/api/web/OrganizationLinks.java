package dev.axonia.shared.api.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import lombok.experimental.UtilityClass;

/** Makes a link that leaves the app open under the organization it belongs to. */
@UtilityClass
public class OrganizationLinks {

    public String withOrganization(String url, String organizationId) {

        var separator = url.contains("?") ? "&" : "?";
        return url + separator + "org=" + URLEncoder.encode(organizationId, StandardCharsets.UTF_8);
    }
}

package dev.axonia.identity.application.scim;

/**
 * The SCIM 2.0 enterprise user extension fields Axonia consumes (org-chart labels), carried under the URN
 * {@code urn:ietf:params:scim:schemas:extension:enterprise:2.0:User}. Holds the raw SCIM names; translation to Axonia's
 * vocabulary happens in {@link ScimUserMapper}.
 */
public record ScimEnterpriseUser(String organization, String division, String department) {
}

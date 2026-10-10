package dev.axonia.selfhosted.domain;

/** What the configuration says the installation is. */
public record Installation(String organizationId, String organizationName, String ownerGroup, String subjectClaim) {
}

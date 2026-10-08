package dev.axonia.identity.pat;

/** Answers whether a member's profile is currently active (not de-provisioned) within an Organization. */
public interface MemberAccounts {
    boolean isActive(String organizationId, String userId);
}

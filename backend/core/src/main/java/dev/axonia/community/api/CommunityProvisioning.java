package dev.axonia.community.api;

/** Provisions a hub's single community and enrolls its joiners, inside the caller's transaction and RLS scope. */
public interface CommunityProvisioning {

    Community provisionInitial(ProvisionCommunityRequest request);

    /** {@code true} when a new membership was inserted, {@code false} when one already existed. */
    boolean enrollJoiner(String organizationId, String userId);
}

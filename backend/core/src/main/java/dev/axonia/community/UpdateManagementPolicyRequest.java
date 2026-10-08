package dev.axonia.community;

import jakarta.validation.constraints.NotNull;

public record UpdateManagementPolicyRequest(@NotNull Boolean canEditCommunity, @NotNull Boolean canManageLookAndFeel,
        @NotNull Boolean canManageMembers, @NotNull Boolean canManageContributionPolicy,
        @NotNull Boolean canArchiveCommunity, @NotNull Boolean canCurateTags, @NotNull Boolean canViewHealth,
        @NotNull Boolean canInviteMembers, @NotNull Boolean canManageCommunication,
        @NotNull Boolean canCreditContributions) {
}

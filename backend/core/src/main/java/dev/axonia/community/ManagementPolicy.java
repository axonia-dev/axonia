package dev.axonia.community;

public record ManagementPolicy(boolean canEditCommunity, boolean canManageLookAndFeel, boolean canManageMembers,
        boolean canManageContributionPolicy, boolean canArchiveCommunity, boolean canCurateTags, boolean canViewHealth,
        boolean canInviteMembers, boolean canManageCommunication, boolean canCreditContributions) {

    public static ManagementPolicy curated() {

        return new ManagementPolicy(false, false, false, false, false, false, false, false, false, false);
    }
}

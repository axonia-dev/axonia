package dev.axonia.community.domain;

import java.time.Instant;

import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.TopicManagementPolicy;
import lombok.Builder;
import lombok.Value;

/** A community as it is stored: every column, id and version included, so a write round-trips exactly. */
@Value
@Builder(toBuilder = true)
public class CommunityRecord {

    Long id;

    String organizationId;
    String slug;
    String name;
    Long kindId;
    String description;
    CommunityStatus status;
    String badgeKey;
    String heroKey;
    String accentColor;

    Integer heroFocusX;
    Integer heroFocusY;
    Integer badgeFocusX;
    Integer badgeFocusY;

    boolean canStartSeries;
    boolean canEditStructure;
    boolean canQueueTopics;
    boolean canManageSchedule;

    boolean canEditCommunity;
    boolean canManageLookAndFeel;
    boolean canManageMembers;
    boolean canManageContributionPolicy;
    boolean canArchiveCommunity;
    boolean canCurateTags;
    boolean canViewHealth;
    boolean canInviteMembers;
    boolean canManageCommunication;
    boolean canCreditContributions;

    @Builder.Default
    boolean notifySessionUpcoming = true;

    @Builder.Default
    boolean notifySessionWrapUp = true;

    @Builder.Default
    int sessionUpcomingLeadDays = 3;

    @Builder.Default
    int wrapUpGraceHours = 48;

    @Builder.Default
    TopicManagementPolicy topicManagementPolicy = TopicManagementPolicy.ORGANIZER_ONLY;

    Instant createdAt;
    Instant updatedAt;
    Long version;
}

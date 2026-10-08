package dev.axonia.community.internal;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import dev.axonia.community.CommunityStatus;
import dev.axonia.community.TopicManagementPolicy;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("communities")
public class CommunityEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private String slug;
    private String name;
    private Long kindId;
    private String description;
    private CommunityStatus status;
    private String badgeKey;
    private String heroKey;
    private String accentColor;

    private Integer heroFocusX;
    private Integer heroFocusY;
    private Integer badgeFocusX;
    private Integer badgeFocusY;

    private boolean canStartSeries;
    private boolean canEditStructure;
    private boolean canQueueTopics;
    private boolean canManageSchedule;

    private boolean canEditCommunity;
    private boolean canManageLookAndFeel;
    private boolean canManageMembers;
    private boolean canManageContributionPolicy;
    private boolean canArchiveCommunity;
    private boolean canCurateTags;
    private boolean canViewHealth;
    private boolean canInviteMembers;
    private boolean canManageCommunication;
    private boolean canCreditContributions;

    @Builder.Default
    private boolean notifySessionUpcoming = true;

    @Builder.Default
    private boolean notifySessionWrapUp = true;

    @Builder.Default
    private int sessionUpcomingLeadDays = 3;

    @Builder.Default
    private int wrapUpGraceHours = 48;

    @Builder.Default
    private TopicManagementPolicy topicManagementPolicy = TopicManagementPolicy.ORGANIZER_ONLY;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}

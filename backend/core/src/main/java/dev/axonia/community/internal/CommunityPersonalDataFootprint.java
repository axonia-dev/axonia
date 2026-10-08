package dev.axonia.community.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.Classification;
import dev.axonia.ColumnFootprint;
import dev.axonia.DataCategory;
import dev.axonia.Disposition;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.TableFootprint;

/**
 * A community is a group, not a person: {@code communities} and {@code community_kind} describe the group's own
 * settings and look and feel, never a member. {@code memberships} is the one place community carries a person -
 * {@code user_id} - and it goes with them.
 *
 * <p>
 * Unlike every other module's SUBJECT/PERSONAL_CONTENT column, {@code memberships.user_id} has no matching
 * {@code MemberDataEraser}: {@code dev.axonia.privacy.internal.MemberPurge} deletes the row itself, through
 * {@code MembershipService#leaveForErasure}, before any eraser runs - that departure has cross-cutting side effects
 * (role-binding drop, member-count decrement, identity-provider sync) that belong in the orchestrator, not a per-module
 * eraser. The disposition declared below is still accurate; only the code path that fulfils it differs.
 */
@Component
public class CommunityPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(memberships(), communities(), communityKind(), chatDestinations());
    }

    private TableFootprint memberships() {

        return TableFootprint.builder().table("memberships").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(
                        Set.of("id", "organization_id", "community_id", "role", "joined_at", "updated_at", "version"))
                .exportedContext(Set.of("role", "joined_at", "updated_at")).exportedLabels(Set.of("community")).build();
    }

    private TableFootprint communities() {

        return TableFootprint.builder().table("communities").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of())
                .notPersonal(Set.of("id", "organization_id", "slug", "name", "description", "status", "created_at",
                        "updated_at", "version", "kind_id", "can_start_series", "can_edit_structure",
                        "can_queue_topics", "badge_key", "hero_key", "accent_color", "hero_focus_x", "hero_focus_y",
                        "badge_focus_x", "badge_focus_y", "can_manage_schedule", "topic_management_policy",
                        "can_edit_community", "can_manage_look_and_feel", "can_manage_members",
                        "can_manage_contribution_policy", "can_archive_community", "can_curate_tags", "can_view_health",
                        "can_invite_members", "can_manage_communication", "notify_session_upcoming",
                        "notify_session_wrap_up", "session_upcoming_lead_days", "wrap_up_grace_hours",
                        "can_credit_contributions"))
                .build();
    }

    private TableFootprint communityKind() {

        return TableFootprint.builder().table("community_kind").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of())
                .notPersonal(Set.of("id", "organization_id", "label", "created_at", "updated_at", "version")).build();
    }

    /**
     * No personal column and no {@code exportedContext}: the builder leaves both export sets unset, unlike
     * {@code memberships()}'s. {@code webhook_url} is a bearer token for a room that never leaves this module unmasked
     * ({@link ChatDestinationEntity}'s own javadoc), so nothing here is offered to a subject access export - a table
     * with no personal columns has nothing to export.
     */
    private TableFootprint chatDestinations() {

        return TableFootprint.builder().table("community_chat_destination").category(DataCategory.HOW_YOU_TAKE_PART)
                .personal(List.of())
                .notPersonal(Set.of("id", "organization_id", "community_id", "name", "webhook_url", "broken",
                        "consecutive_failures", "last_posted_at", "last_failure_at", "last_error", "created_at",
                        "updated_at", "version"))
                .build();
    }
}

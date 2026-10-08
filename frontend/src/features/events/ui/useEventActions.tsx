import type { ReactNode } from "react";

import { useContributionPolicy } from "@/features/community/api/use-contribution-policy";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { isHeld, sessionTitle } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { isOpen } from "@/features/events/domain/slot";
import { AddResourceButton } from "@/features/knowledge/ui/AddResourceButton";
import { ContributionActions } from "@/features/recognition/ui/ContributionActions";

export interface EventActions {
  /** isOrganizer — session governance, slot reorder, opening sessions. */
  canManage: boolean;
  /** isOrganizer || management.canManageCommunication — a session's own reminder and wrap-up:
   *  whether they are stated at all, previewed, moved, or suppressed. */
  canManageMessages: boolean;
  /** isOrganizer || policy.canStartSeries */
  canStartSeries: boolean;
  /** isOrganizer || policy.canEditStructure */
  canEditStructure: boolean;
  /** isOrganizer || policy.canQueueTopics */
  canQueueTopics: boolean;
  /** isOrganizer || policy.canManageSchedule */
  canManageSchedule: boolean;
  renderSessionActions?: (session: Session) => ReactNode;
  renderSlotActions?: (
    slot: Slot,
    section: Section,
    session: Session,
  ) => ReactNode;
}

export function useEventActions(slug: string): EventActions {
  const canManage = useIsOrganizer(slug);
  const policy = useContributionPolicy(slug);
  const management = useManagementAccess(slug);

  function renderSessionActions(session: Session) {
    if (!isHeld(session)) return null;
    return (
      <AddResourceButton
        slug={slug}
        sessionId={session.id}
        defaultTitle={sessionTitle(session)}
        {...(session.recordingUrl !== null
          ? { defaultUrl: session.recordingUrl }
          : {})}
        provenanceLabel={sessionTitle(session)}
      />
    );
  }

  function renderSlotActions(slot: Slot) {
    if (isOpen(slot)) {
      return null;
    }
    return (
      <ContributionActions
        contribution={{
          contributionType: "SLOT_PRESENTED",
          contributionId: slot.id,
        }}
        contributorUserIds={slot.presenterUserIds}
      />
    );
  }

  return {
    canManage,
    canManageMessages: management.communication,
    canStartSeries: canManage || policy.canStartSeries,
    canEditStructure: canManage || policy.canEditStructure,
    canQueueTopics: canManage || policy.canQueueTopics,
    canManageSchedule: canManage || policy.canManageSchedule,
    renderSessionActions,
    renderSlotActions,
  };
}

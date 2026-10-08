import { type ReactNode } from "react";

import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { useSessionResources } from "@/features/knowledge/api/use-session-resources";
import { AddResourceButton } from "@/features/knowledge/ui/AddResourceButton";
import { SlotResources } from "@/features/knowledge/ui/SlotResources";

interface Props {
  slug: string;
  session: Session;
  slot: Slot;
  canManage: boolean;
  canAdd: boolean;
  /** The appreciation node, handed in opaque by `renderSlotActions`. */
  actions?: ReactNode;
  provenanceLabel: string;
  defaultTitle: string;
  /** Organizer attribution: the people who gave the talk, when the viewer may say so. */
  presenterUserIds?: string[];
}

/**
 * A slot's material, on the card rather than behind it.
 *
 * Its resources, the control that captures another, and the reactions and love it has drawn, all
 * stated where the talk is. They were folded behind a summary line for a while, on the reasoning
 * that a reaction strip competes with the title above it; the line cost a click to reach content
 * it described less well than the content described itself, which is the worse trade. The taller
 * card is the price, paid deliberately.
 *
 * A slot carrying nothing, showing no appreciation and offering no way to capture anything renders
 * no element at all rather than an empty one, which as a flex child would spend the card's row gap
 * on both of its sides.
 */
export function SlotBody({
  slug,
  session,
  slot,
  canManage,
  canAdd,
  actions,
  provenanceLabel,
  defaultTitle,
  presenterUserIds,
}: Readonly<Props>) {
  const resources = useSessionResources(slug, session.id);
  const hasResources = (resources.data ?? []).some(
    (r) => r.segmentId === slot.id,
  );

  if (!hasResources && !actions && !canAdd) {
    return null;
  }
  return (
    <div className="flex flex-col gap-2">
      <SlotResources
        slug={slug}
        sessionId={session.id}
        segmentId={slot.id}
        canManage={canManage}
      />
      {(actions ?? canAdd) && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          {canAdd && (
            <AddResourceButton
              slug={slug}
              sessionId={session.id}
              segmentId={slot.id}
              defaultTitle={defaultTitle}
              provenanceLabel={provenanceLabel}
              presenterUserIds={presenterUserIds ?? []}
            />
          )}
          {actions && <div className="ml-auto">{actions}</div>}
        </div>
      )}
    </div>
  );
}

import { Calendar, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

import { useUnsave } from "@/features/bookmarks/api/use-save";
import type { SavedSessionView } from "@/features/bookmarks/domain/saved";
import {
  CommunityBadge,
  SavedRowShell,
} from "@/features/bookmarks/ui/SavedRowShell";
import { relativeTime } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";

const STATUS_LABEL: Record<string, string> = {
  SCHEDULED: "Upcoming",
  HELD: "Held",
  CANCELLED: "Cancelled",
  SKIPPED: "Skipped",
};

const STATUS_TONE: Record<string, string> = {
  SCHEDULED: "bg-primary/10 text-primary",
  HELD: "bg-surface-sunken text-muted-foreground",
  CANCELLED: "bg-danger/15 text-danger",
  SKIPPED: "bg-surface-sunken text-muted-foreground",
};

function slotSummary(count: number): string {
  if (count === 0) {
    return "no topics yet";
  }
  return `${count} ${count === 1 ? "slot" : "slots"}`;
}

interface Props {
  item: SavedSessionView;
  /** Resolved display name for the facilitator, if known - the row stays a
   * pure presentational leaf, so the caller (MySavedView) owns the lookup. */
  facilitatorName?: string | undefined;
  /** False inside a community's own page, where every row shares that community. */
  showCommunity: boolean;
}

export function SavedSessionRow({
  item,
  facilitatorName,
  showCommunity,
}: Readonly<Props>) {
  const { session, community } = item;
  const unsave = useUnsave({ kind: "session", id: session.id });
  const now = useNow();
  const href = community
    ? `/communities/${community.slug}/sessions/${session.id}`
    : "#";
  const statusLabel = STATUS_LABEL[session.status] ?? session.status;
  const statusTone =
    STATUS_TONE[session.status] ?? "bg-surface-sunken text-muted-foreground";

  return (
    <SavedRowShell
      icon={
        <span className="bg-secondary/10 text-secondary flex size-10 items-center justify-center rounded-lg">
          <Calendar size={20} />
        </span>
      }
      onUnsave={() => unsave.mutate()}
      unsavePending={unsave.isPending}
    >
      <Link
        to={href}
        className="text-foreground hover:text-primary inline-flex items-center gap-1 font-semibold underline-offset-2 hover:underline"
      >
        {session.title ?? "Session"}
        <ChevronRight size={14} className="text-muted-foreground shrink-0" />
      </Link>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        <span
          className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusTone}`}
        >
          {statusLabel}
        </span>
        <p className="text-muted-foreground text-xs">
          {relativeTime(session.scheduledAt, now)}
          {facilitatorName ? ` · ${facilitatorName}` : ""} ·{" "}
          {slotSummary(session.slotCount)}
        </p>
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        {showCommunity && community && <CommunityBadge community={community} />}
        <p className="text-muted-foreground text-xs">
          saved {relativeTime(item.savedAt, now)}
        </p>
      </div>
    </SavedRowShell>
  );
}

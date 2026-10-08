import { Link } from "react-router-dom";

import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  openSpotLabel,
  presenterIdsOf,
  timeLabel,
} from "@/features/events/domain/schedule";
import { PresenterAvatars } from "@/features/events/ui/PresenterAvatars";
import { ProjectedNotice } from "@/features/events/ui/ProjectedNotice";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import { SpotsPill } from "@/features/events/ui/SpotsPill";
import type { SneakPeek } from "@/features/events/ui/timeline/timeline";
import {
  accessibleName,
  sneakPeek,
} from "@/features/events/ui/timeline/timeline";
import type { User } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";

interface Props {
  entry: ScheduleEntry;
  label: string;
  href: string;
  nextUp: boolean;
  users: Record<string, User>;
}

function moreTalksLabel(count: number): string {
  if (count === 0) {
    return "";
  }
  if (count === 1) {
    return " +1 more talk";
  }
  return ` +${count} more talks`;
}

function peekLine(peek: SneakPeek, users: Record<string, User>): string {
  const presenter =
    peek.presenterId !== null
      ? ` · ${displayNameOf(peek.presenterId, users)}`
      : "";
  const more = moreTalksLabel(peek.more);
  return `${peek.title}${presenter}${more}`;
}

function metaLine(entry: ScheduleEntry): string {
  const duration =
    entry.durationMinutes !== null ? `${entry.durationMinutes} min` : null;
  return [timeLabel(entry), duration].filter((p) => p !== null).join(" · ");
}

function StatusChip({
  entry,
  spots,
}: Readonly<{ entry: ScheduleEntry; spots: string | null }>) {
  if (entry.virtual) {
    return (
      <span className="border-border-strong text-muted-foreground rounded-full border border-dashed px-2 py-0.5 text-xs">
        Projected
      </span>
    );
  }
  if (entry.status === "HELD") {
    return (
      <span className="bg-surface-sunken text-muted-foreground rounded-full px-2 py-0.5 text-xs">
        Held
      </span>
    );
  }
  if (spots !== null) {
    return <SpotsPill label={spots} />;
  }
  return null;
}

function CardTitleRow({
  label,
  meta,
  nextUp,
  muted,
}: Readonly<{ label: string; meta: string; nextUp: boolean; muted: boolean }>) {
  return (
    <div className="flex items-center justify-between gap-2">
      <span
        className={cn(
          "text-foreground font-medium",
          nextUp && "font-display font-semibold",
          muted && "text-muted-foreground",
        )}
      >
        {label}
      </span>
      {meta !== "" && (
        <span className="text-muted-foreground shrink-0 text-xs">{meta}</span>
      )}
    </div>
  );
}

function CardFooter({
  entry,
  users,
}: Readonly<{ entry: ScheduleEntry; users: Record<string, User> }>) {
  const spots = openSpotLabel(entry);
  const presenterIds = presenterIdsOf(entry);
  const hasChip = spots !== null || entry.virtual || entry.status === "HELD";
  if (presenterIds.length === 0 && !hasChip) return null;
  return (
    <div className="mt-2 flex items-center gap-2">
      <PresenterAvatars userIds={presenterIds} users={users} />
      <span className="ml-auto">
        <StatusChip entry={entry} spots={spots} />
      </span>
    </div>
  );
}

export function TimelineCard({
  entry,
  label,
  href,
  nextUp,
  users,
}: Readonly<Props>) {
  const peek = nextUp ? sneakPeek(entry) : null;
  const muted = entry.virtual || entry.status === "HELD";

  return (
    <Link
      to={href}
      aria-label={accessibleName(entry, label)}
      className={cn(
        "rounded-md border p-4 transition-[transform,box-shadow,border-color,opacity] duration-200",
        "group-hover:border-primary group-hover:shadow-md motion-safe:group-hover:-translate-y-0.5",
        entry.virtual
          ? "border-border-strong group-hover:bg-surface border-dashed bg-transparent opacity-80 group-hover:opacity-100"
          : "bg-surface border-border",
        nextUp && "border-l-primary border-l-4 shadow-sm",
      )}
    >
      <CardTitleRow
        label={label}
        meta={metaLine(entry)}
        nextUp={nextUp}
        muted={muted}
      />
      {peek !== null && (
        <p className="text-muted-foreground mt-1 text-sm">
          {peekLine(peek, users)}
        </p>
      )}
      {entry.virtual && <ProjectedNotice className="mt-1" />}
      <CardFooter entry={entry} users={users} />
    </Link>
  );
}

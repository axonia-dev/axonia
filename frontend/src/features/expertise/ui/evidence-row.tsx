import type { LucideIcon } from "lucide-react";
import {
  CircleCheck,
  FileText,
  Lightbulb,
  Presentation,
  X,
} from "lucide-react";
import { Link } from "react-router-dom";

import { SectionTypeIcon } from "@/features/events/ui/section-type-visual";
import type {
  EvidenceItem,
  SignalType,
  SourceType,
} from "@/features/expertise/domain/expertise";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";
import { relativeTime } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";

const SIGNAL_LABELS: Record<SignalType, string> = {
  PRESENTED: "Presented",
  CONTRIBUTED: "Contributed",
  OFFERED: "Offered",
  COMPLETED: "Completed",
};

const SIGNAL_ICONS: Record<SignalType, LucideIcon> = {
  PRESENTED: Presentation,
  CONTRIBUTED: FileText,
  OFFERED: Lightbulb,
  COMPLETED: CircleCheck,
};

const SOURCE_PATHS: Record<SourceType, string> = {
  SLOT: "schedule",
  ARTIFACT: "library",
  TOPIC: "backlog",
  ACTION_ITEM: "backlog",
};

// Where an evidence row links. An artifact filters the library to itself (by
// title, matching the "View in library" link on a session); a slot opens the
// session it happened in; the rest open the source's landing page.
function sourceHref(item: EvidenceItem): string {
  const base = `/communities/${item.communitySlug}/${SOURCE_PATHS[item.sourceType]}`;
  if (
    item.sourceType === "ARTIFACT" &&
    item.label !== null &&
    item.label !== ""
  ) {
    return `${base}?q=${encodeURIComponent(item.label)}`;
  }
  if (item.sourceType === "SLOT" && item.sessionId !== null) {
    return `/communities/${item.communitySlug}/sessions/${item.sessionId}`;
  }
  return base;
}

// The real type badge (section type for slots, artifact type for artifacts),
// falling back to the generic per-signal glyph when the type no longer resolves.
function EvidenceIcon({ item }: Readonly<{ item: EvidenceItem }>) {
  const { sourceType, typeId, typeLabel, typeIcon, typeAccentColor } = item;

  if (
    typeId !== null &&
    typeLabel !== null &&
    (sourceType === "SLOT" || sourceType === "ARTIFACT")
  ) {
    const Badge = sourceType === "SLOT" ? SectionTypeIcon : TypeIcon;
    return (
      <Badge
        typeId={typeId}
        label={typeLabel}
        icon={typeIcon}
        accentColor={typeAccentColor}
        size={13}
      />
    );
  }

  const Icon = SIGNAL_ICONS[item.signalType];
  return (
    <span
      aria-hidden
      className="text-muted-foreground bg-surface-sunken flex size-6 shrink-0 items-center justify-center rounded-md"
    >
      <Icon size={13} />
    </span>
  );
}

function evidenceText(item: EvidenceItem): string {
  const signalLabel = SIGNAL_LABELS[item.signalType];
  const title = item.label !== null && item.label !== "" ? item.label : null;
  return title !== null ? `${signalLabel}: ${title}` : signalLabel;
}

interface RowProps {
  item: EvidenceItem;
  onDismiss?: ((item: EvidenceItem) => void) | undefined;
}

export function EvidenceRow({ item, onDismiss }: Readonly<RowProps>) {
  const date = relativeTime(item.occurredAt, useNow());
  const text = evidenceText(item);
  const linkable = item.communitySlug !== null && item.communitySlug !== "";

  return (
    <li className="flex items-center gap-2.5 py-1.5">
      <EvidenceIcon item={item} />
      {linkable ? (
        <Link
          to={sourceHref(item)}
          className="text-foreground hover:text-primary min-w-0 flex-1 truncate text-sm font-medium underline-offset-2 hover:underline"
        >
          {text}
        </Link>
      ) : (
        <span className="text-foreground min-w-0 flex-1 truncate text-sm font-medium">
          {text}
        </span>
      )}
      <span className="text-muted-foreground shrink-0 text-xs">{date}</span>
      {onDismiss && (
        <button
          type="button"
          aria-label={`Dismiss ${text}`}
          onClick={() => onDismiss(item)}
          className="text-muted-foreground hover:text-danger shrink-0 cursor-pointer transition-colors"
        >
          <X size={15} />
        </button>
      )}
    </li>
  );
}

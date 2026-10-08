import { Calendar, FileText, List, type LucideIcon } from "lucide-react";
import { Button } from "react-aria-components";

import type { SavedKind } from "@/features/bookmarks/domain/saved-filter";
import { cn } from "@/lib/cn";

const KIND_OPTIONS: ReadonlyArray<{
  id: SavedKind;
  label: string;
  Icon: LucideIcon;
}> = [
  { id: "all", label: "All", Icon: List },
  { id: "artifact", label: "Artifacts", Icon: FileText },
  { id: "session", label: "Sessions", Icon: Calendar },
];

/**
 * The `All · Artifacts · Sessions` facet atop the saved list. A connected
 * segmented control — deliberately a different visual family from the pink
 * filter chips — so "which kind" reads as a distinct axis from the type and
 * "to review" filters. The active segment carries the primary accent fill.
 */
export function KindSegment({
  value,
  onChange,
}: Readonly<{ value: SavedKind; onChange: (kind: SavedKind) => void }>) {
  return (
    <div
      role="group"
      aria-label="Filter by kind"
      className="border-border bg-surface inline-flex items-center gap-0.5 rounded-full border p-[3px] shadow-sm"
    >
      {KIND_OPTIONS.map(({ id, label, Icon }) => {
        const active = value === id;
        return (
          <Button
            key={id}
            aria-pressed={active}
            onPress={() => onChange(id)}
            className={cn(
              "focus-visible:ring-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm font-semibold transition-colors outline-none focus-visible:ring-2",
              active
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            <Icon size={14} aria-hidden />
            {label}
          </Button>
        );
      })}
    </div>
  );
}

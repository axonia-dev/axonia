import { ChevronDown } from "lucide-react";
import { useState } from "react";

import { isDeclared } from "@/features/expertise/domain/declared-areas";
import type {
  EvidenceItem,
  SelfArea,
  SelfDeclaration,
  Tier,
} from "@/features/expertise/domain/expertise";
import { AreaRow } from "@/features/expertise/ui/AreaRow";
import { OpenToQuestionsMarker } from "@/features/expertise/ui/OpenToQuestionsMarker";
import { TIER_META } from "@/features/expertise/ui/tier-meta";
import { cn } from "@/lib/cn";

interface Props {
  tier: Tier;
  areas: SelfArea[];
  defaultOpen: boolean;
  pageSize?: number;
  onHide: (tag: string) => void;
  onUnhide: (tag: string) => void;
  onDismiss: (item: EvidenceItem, tag: string) => void;
  declarations: SelfDeclaration[];
  onRemoveDeclaration?: ((tag: string) => void) | undefined;
  isRemovingDeclaration?: ((tag: string) => boolean) | undefined;
  slug?: string | undefined;
}

export function TierLane({
  tier,
  areas,
  defaultOpen,
  pageSize = 6,
  onHide,
  onUnhide,
  onDismiss,
  declarations,
  onRemoveDeclaration,
  isRemovingDeclaration,
  slug,
}: Readonly<Props>) {
  const [open, setOpen] = useState(defaultOpen);
  const [shown, setShown] = useState(pageSize);
  const meta = TIER_META[tier];

  if (areas.length === 0) return null;

  return (
    <section className="flex flex-col gap-2">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
        className="group flex cursor-pointer items-center gap-2 text-left"
      >
        <span className={cn("size-2 rounded-full", meta.dotClassName)} />
        <span className="text-foreground text-sm font-semibold">
          {meta.label}
        </span>
        <span className="text-muted-foreground text-xs">· {areas.length}</span>
        <span className="bg-border ml-1 h-px flex-1" />
        <ChevronDown
          size={15}
          aria-hidden
          className={cn(
            "text-muted-foreground transition-transform",
            !open && "-rotate-90",
          )}
        />
      </button>

      {open && (
        <div className="flex flex-col gap-2">
          {areas.slice(0, shown).map((area) => (
            <AreaRow
              key={area.canonicalTag}
              area={area}
              onHide={onHide}
              onUnhide={onUnhide}
              onDismiss={onDismiss}
              slug={slug}
              marker={
                isDeclared(area, declarations) ? (
                  <OpenToQuestionsMarker
                    tag={area.canonicalTag}
                    onRemove={onRemoveDeclaration}
                    isRemoving={isRemovingDeclaration?.(area.canonicalTag)}
                  />
                ) : null
              }
            />
          ))}
          {areas.length > shown && (
            <button
              type="button"
              onClick={() => setShown((n) => n + pageSize)}
              className="text-primary self-center py-1 text-xs font-semibold"
            >
              Show {areas.length - shown} more
            </button>
          )}
        </div>
      )}
    </section>
  );
}

import {
  type FocusEvent,
  type PointerEvent,
  useCallback,
  useEffect,
  useState,
} from "react";
import { Button as AriaButton } from "react-aria-components";

import { KindBadge } from "@/components/patterns/KindBadge";
import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import { MorphPart } from "@/components/patterns/morph/MorphPart";
import type { LobbyItem } from "@/features/community/domain/lobby";
import {
  accentVariables,
  focalPosition,
} from "@/features/community/ui/community-accent";
import {
  nextSessionLine,
  SESSION_THIS_WEEK,
} from "@/features/community/ui/lobby/lobby-copy";
import { useLobbyMorphId } from "@/features/community/ui/lobby/lobby-morph";
import { LobbyJoinButton } from "@/features/community/ui/lobby/LobbyJoinButton";
import type { LobbyFaces } from "@/features/community/ui/lobby/use-lobby-faces";
import { MembershipChip } from "@/features/community/ui/MembershipChip";
import { cn } from "@/lib/cn";
import { plural } from "@/lib/plural";
import { useNow } from "@/lib/use-wake-at";

const PREVIEW_FACES = 4;

export type GrowFrom = "top" | "top-left" | "top-right";

const ORIGINS: ReadonlyMap<GrowFrom, string> = new Map([
  ["top", "origin-top"],
  ["top-left", "origin-top-left"],
  ["top-right", "origin-top-right"],
]);

/** The pulsing mark of a session in the next seven days. */
export function LiveDot({ className }: Readonly<{ className?: string }>) {
  return (
    <span
      role="img"
      aria-label={SESSION_THIS_WEEK}
      className={cn(
        "bg-success size-2 flex-none rounded-full motion-safe:animate-[signal-pulse_2.2s_ease-out_infinite]",
        className,
      )}
    />
  );
}

/** The community's hero at its focal point, or its accent as a deep wash when it has none. */
export function LobbyArt({
  item,
  className,
}: Readonly<{ item: LobbyItem; className?: string }>) {
  if (item.heroUrl == null) {
    return (
      <span
        aria-hidden
        className={cn(
          "absolute inset-0 bg-linear-to-br from-[color-mix(in_srgb,var(--community-accent)_80%,black)] via-[color-mix(in_srgb,var(--community-accent)_40%,black)] to-[color-mix(in_srgb,var(--community-accent)_12%,black)]",
          className,
        )}
      />
    );
  }
  return (
    <img
      src={item.heroUrl}
      alt=""
      className={cn("absolute inset-0 size-full object-cover", className)}
      style={{ objectPosition: focalPosition(item.heroFocus) }}
    />
  );
}

function usePreview(enabled: boolean) {
  const [open, setOpen] = useState(false);
  const onFocusVisible = useCallback(() => setOpen(true), []);
  return {
    open: enabled && open,
    onFocusVisible,
    handlers: {
      onPointerEnter: (event: PointerEvent) => {
        if (event.pointerType !== "touch") setOpen(true);
      },
      onPointerLeave: (event: PointerEvent) => {
        if (event.pointerType !== "touch") setOpen(false);
      },
      onBlur: (event: FocusEvent<HTMLElement>) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
      },
    },
  };
}

/** Reports a focus ring as it appears: only keyboard focus draws one, never a tap or a restore after a click. */
function FocusVisibleSignal({
  visible,
  onVisible,
}: Readonly<{ visible: boolean; onVisible: () => void }>) {
  useEffect(() => {
    if (visible) onVisible();
  }, [visible, onVisible]);
  return null;
}

function CardFace({
  item,
  open,
  showRole,
  onOpenThreshold,
  onFocusVisible,
  morph,
}: Readonly<{
  item: LobbyItem;
  open: boolean;
  showRole: boolean;
  onOpenThreshold: (item: LobbyItem) => void;
  onFocusVisible: () => void;
  morph: string | undefined;
}>) {
  return (
    <AriaButton
      aria-label={item.name}
      onPress={() => onOpenThreshold(item)}
      className={cn(
        "group relative isolate block aspect-video w-full cursor-pointer overflow-hidden rounded-lg text-left outline-none",
        "data-[focus-visible]:ring-focus-ring data-[focus-visible]:ring-offset-background data-[focus-visible]:ring-2 data-[focus-visible]:ring-offset-2",
        open && "rounded-b-none",
      )}
    >
      {({ isFocusVisible }) => (
        <>
          <FocusVisibleSignal
            visible={isFocusVisible}
            onVisible={onFocusVisible}
          />
          <MorphPart part="banner" id={morph}>
            <LobbyArt item={item} />
          </MorphPart>
          <span
            aria-hidden
            className="absolute inset-0 bg-linear-to-t from-black/80 via-black/20 to-transparent"
          />
          {showRole && item.role !== null && (
            <span className="absolute top-2 left-2">
              <MembershipChip role={item.role} />
            </span>
          )}
          <span className="absolute inset-x-3 bottom-2.5 flex items-center gap-2">
            <MorphPart part="name" id={morph}>
              <span className="font-display min-w-0 flex-1 truncate text-base font-bold text-white">
                {item.name}
              </span>
            </MorphPart>
            {item.nextSessionThisWeek && <LiveDot />}
          </span>
        </>
      )}
    </AriaButton>
  );
}

function PreviewPanel({
  item,
  open,
  faces,
}: Readonly<{ item: LobbyItem; open: boolean; faces: LobbyFaces }>) {
  const now = useNow();
  return (
    <div
      inert={!open}
      data-preview-panel
      className={cn(
        "bg-surface border-border text-muted-foreground absolute inset-x-0 top-full flex flex-col gap-2 rounded-b-lg border border-t-0 p-3 text-xs shadow-2xl",
        "transition-opacity duration-200",
        open ? "opacity-100" : "pointer-events-none opacity-0",
      )}
    >
      <span className="flex items-center justify-between gap-2">
        <KindBadge
          kindId={item.kindId}
          label={item.kind}
          accentColor={item.accentColor ?? null}
        />
        {item.role === null && <LobbyJoinButton slug={item.slug} />}
      </span>
      <span className="text-foreground line-clamp-2 font-semibold">
        {nextSessionLine(item, now)}
      </span>
      <span className="flex items-center gap-3">
        <MemberFacepile
          members={faces(item, PREVIEW_FACES)}
          max={PREVIEW_FACES}
          size="size-6 text-[0.5625rem]"
        />
        {plural(item.memberCount, "member")}
      </span>
    </div>
  );
}

/**
 * A 16:9 tile on a shelf or in a grid. Under a mouse or keyboard focus it grows in place and
 * opens a panel below it with the next session, the faces and Join; a tap, having no hover,
 * opens the threshold straight away, as a click does.
 */
export function LobbyCard({
  item,
  faces,
  onOpenThreshold,
  showRole = false,
  growFrom = "top",
  preview = true,
  place,
}: Readonly<{
  item: LobbyItem;
  faces: LobbyFaces;
  onOpenThreshold: (item: LobbyItem) => void;
  showRole?: boolean;
  /** The edge a card at the end of a row grows from, so it stays inside the row. */
  growFrom?: GrowFrom;
  /** Off where a card cannot grow without being clipped or leaving the screen. */
  preview?: boolean;
  /** Where the card sits in the lobby, which decides whether it wears the community's morph. */
  place?: { list: string; index: number };
}>) {
  const { open, onFocusVisible, handlers } = usePreview(preview);
  const morph = useLobbyMorphId(
    item.slug,
    place === undefined ? null : { kind: "card", ...place },
  );
  return (
    <div
      data-preview={open ? "open" : "closed"}
      style={accentVariables(item)}
      className={cn("relative", open && "z-20")}
      {...handlers}
    >
      <div
        className={cn(
          "relative rounded-lg transition-shadow duration-200 ease-out motion-safe:transition-[transform,box-shadow] motion-safe:duration-300",
          ORIGINS.get(growFrom),
          open && "shadow-2xl motion-safe:scale-115",
        )}
      >
        <CardFace
          item={item}
          open={open}
          showRole={showRole}
          onOpenThreshold={onOpenThreshold}
          onFocusVisible={onFocusVisible}
          morph={morph}
        />
        <PreviewPanel item={item} open={open} faces={faces} />
      </div>
    </div>
  );
}

import { useMemo } from "react";

import { KindBadge } from "@/components/patterns/KindBadge";
import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import { MorphPart } from "@/components/patterns/morph/MorphPart";
import { Skeleton } from "@/components/ui/Skeleton";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { accentVariables } from "@/features/community/ui/community-accent";
import { LiveDot, LobbyArt } from "@/features/community/ui/lobby/lobby-art";
import { nextSessionLine } from "@/features/community/ui/lobby/lobby-copy";
import { useLobbyMorphId } from "@/features/community/ui/lobby/lobby-morph";
import { LobbyCardFace } from "@/features/community/ui/lobby/LobbyCardFace";
import { LobbyJoinButton } from "@/features/community/ui/lobby/LobbyJoinButton";
import type { LobbyFaces } from "@/features/community/ui/lobby/use-lobby-faces";
import { MembershipChip } from "@/features/community/ui/MembershipChip";
import { plural } from "@/lib/plural";
import { useNow } from "@/lib/use-wake-at";

const CARD_FACES = 4;

function Facts({
  item,
  faces,
}: Readonly<{ item: LobbyItem; faces: LobbyFaces }>) {
  const now = useNow();
  return (
    <div className="text-muted-foreground flex flex-1 flex-col gap-2 p-3 text-xs">
      <span className="flex min-h-7 items-center justify-between gap-2">
        <KindBadge
          kindId={item.kindId}
          label={item.kind}
          accentColor={item.accentColor ?? null}
        />
        {item.role === null && (
          <span className="relative z-10">
            <LobbyJoinButton slug={item.slug} />
          </span>
        )}
      </span>
      <span className="text-foreground line-clamp-2 font-semibold">
        {nextSessionLine(item, now)}
      </span>
      <span className="mt-auto flex items-center gap-3">
        <MemberFacepile
          members={faces(item, CARD_FACES)}
          max={CARD_FACES}
          size="size-6 text-[0.5625rem]"
        />
        {plural(item.memberCount, "member")}
      </span>
    </div>
  );
}

/**
 * A card on a shelf or in a grid: the community's art and name over its facts, the next
 * session, the faces and Join. Pressing anywhere on it opens a joined community straight away,
 * and any other community's threshold; a pointer over it only lifts it a little.
 */
export function LobbyCard({
  item,
  faces,
  onOpenThreshold,
  showRole = false,
  place,
}: Readonly<{
  item: LobbyItem;
  faces: LobbyFaces;
  onOpenThreshold: (item: LobbyItem) => void;
  showRole?: boolean;
  /** Where the card sits in the lobby, which decides whether it wears the community's morph. */
  place?: { list: string; index: number };
}>) {
  const surface = useMemo(
    () => (place === undefined ? null : { kind: "card" as const, ...place }),
    [place],
  );
  const morph = useLobbyMorphId(item.slug, surface);
  return (
    <article
      aria-label={item.name}
      style={accentVariables(item)}
      className="group bg-surface border-border has-[:focus-visible]:ring-focus-ring has-[:focus-visible]:ring-offset-background relative flex h-full flex-col rounded-lg border transition-[translate,box-shadow,border-color] duration-200 ease-out hover:border-[color-mix(in_srgb,var(--community-accent)_45%,var(--border))] hover:shadow-lg has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-offset-2 motion-safe:hover:-translate-y-0.5"
    >
      <div className="relative isolate aspect-video overflow-hidden rounded-t-[calc(var(--radius-lg)-1px)]">
        <MorphPart part="banner" id={morph}>
          <LobbyArt
            item={item}
            className="transition-transform duration-500 ease-out motion-safe:group-hover:scale-105"
          />
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
      </div>
      <Facts item={item} faces={faces} />
      <LobbyCardFace
        item={item}
        surface={surface}
        onOpenThreshold={onOpenThreshold}
      />
    </article>
  );
}

/** A card still loading, the shape of the card to come. */
export function LobbyCardSkeleton() {
  return (
    <div className="border-border flex flex-col overflow-hidden rounded-lg border">
      <Skeleton className="aspect-video w-full rounded-none" />
      <div className="flex h-27 flex-col gap-2 p-3">
        <Skeleton className="h-5 w-32" />
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="mt-auto h-6 w-24" />
      </div>
    </div>
  );
}

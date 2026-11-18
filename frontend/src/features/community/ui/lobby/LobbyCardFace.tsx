import type { MouseEvent } from "react";
import { Button as AriaButton } from "react-aria-components";

import { MorphLink } from "@/components/patterns/morph/MorphLink";
import { useSeedCommunityDetail } from "@/features/community/api/seed-community-detail";
import type { LobbyItem } from "@/features/community/domain/lobby";
import {
  type MorphSurface,
  useLeaveFrom,
} from "@/features/community/ui/lobby/lobby-morph";

/** The press target lies over the whole card, so anywhere on it opens it; Join sits above it. */
const OVERLAY = "absolute inset-0 z-1 cursor-pointer rounded-lg outline-none";

function isPlainClick(event: MouseEvent): boolean {
  return (
    event.button === 0 &&
    !event.metaKey &&
    !event.ctrlKey &&
    !event.shiftKey &&
    !event.altKey
  );
}

function OpenLink({
  item,
  surface,
}: Readonly<{ item: LobbyItem; surface: MorphSurface | null }>) {
  const seed = useSeedCommunityDetail();
  const leave = useLeaveFrom(item.slug, surface);
  return (
    <MorphLink
      to={`/communities/${item.slug}`}
      ready={() => seed(item)}
      onClick={(event) => {
        if (isPlainClick(event)) leave();
      }}
      aria-label={item.name}
      className={OVERLAY}
    />
  );
}

/**
 * What pressing the card does: a joined community opens in one click,
 * straight out of the card, and any other community opens its threshold.
 */
export function LobbyCardFace({
  item,
  surface,
  onOpenThreshold,
}: Readonly<{
  item: LobbyItem;
  surface: MorphSurface | null;
  onOpenThreshold: (item: LobbyItem) => void;
}>) {
  if (item.role !== null) return <OpenLink item={item} surface={surface} />;
  return (
    <AriaButton
      aria-label={item.name}
      onPress={() => onOpenThreshold(item)}
      className={OVERLAY}
    />
  );
}

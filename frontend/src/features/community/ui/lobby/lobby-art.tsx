import type { LobbyItem } from "@/features/community/domain/lobby";
import { focalPosition } from "@/features/community/ui/community-accent";
import { SESSION_THIS_WEEK } from "@/features/community/ui/lobby/lobby-copy";
import { cn } from "@/lib/cn";

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

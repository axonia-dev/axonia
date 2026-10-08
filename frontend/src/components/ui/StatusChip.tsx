import type { ReactNode } from "react";
import { Button as AriaButton } from "react-aria-components";

import { cn } from "@/lib/cn";

export type StatusTone =
  "success" | "warning" | "danger" | "neutral" | "primary";

const tones: Record<StatusTone, string> = {
  success:
    "text-success bg-[color-mix(in_srgb,var(--success)_15%,var(--surface))]",
  warning:
    "text-warning bg-[color-mix(in_srgb,var(--warning)_15%,var(--surface))]",
  danger:
    "text-danger bg-[color-mix(in_srgb,var(--danger)_15%,var(--surface))]",
  neutral:
    "text-muted-foreground bg-[color-mix(in_srgb,var(--muted-foreground)_13%,var(--surface))]",
  primary:
    "text-primary-strong bg-[color-mix(in_srgb,var(--primary)_15%,var(--surface))]",
};

const SHAPE =
  "inline-flex h-6 flex-none items-center gap-1.5 rounded-full px-2.5 text-xs font-medium whitespace-nowrap";

const PRESSABLE =
  "focus-visible:ring-focus-ring cursor-pointer outline-none focus-visible:ring-2 hover:opacity-80 motion-safe:transition-opacity";

/** The chip's own look, for the cases where something else owns the element wearing it - a menu
 *  trigger, say. A chip that opens a panel must not read as a different component from one that
 *  only states something, so the shape and the tint are shared rather than re-declared. */
export function statusChipClass(tone: StatusTone): string {
  return cn(SHAPE, tones[tone]);
}

/** The same chip once it can be pressed or followed, for an anchor or a button of someone else's. */
export function pressableChipClass(tone: StatusTone): string {
  return cn(SHAPE, tones[tone], PRESSABLE);
}

export interface StatusChipProps {
  tone: StatusTone;
  label: string;
  icon?: ReactNode;
  /** Makes the chip pressable. Omit for a chip that only states something. */
  onPress?: () => void;
  "aria-describedby"?: string;
}

export function StatusChip({
  tone,
  label,
  icon,
  onPress,
  "aria-describedby": describedBy,
}: Readonly<StatusChipProps>) {
  const body = (
    <>
      {icon ?? (
        <span
          aria-hidden
          className="size-1.5 flex-none rounded-full bg-current"
        />
      )}
      {label}
    </>
  );
  // A chip that leads somewhere is a button, not a span with a click handler: the same shape and
  // the same tint, so a pressable state never reads as a different component from the one beside
  // it. Without `onPress` it stays a span, so nothing announces an action that does not exist.
  if (onPress) {
    return (
      <AriaButton
        {...(describedBy ? { "aria-describedby": describedBy } : {})}
        onPress={onPress}
        className={pressableChipClass(tone)}
      >
        {body}
      </AriaButton>
    );
  }
  return (
    <span aria-describedby={describedBy} className={cn(SHAPE, tones[tone])}>
      {body}
    </span>
  );
}

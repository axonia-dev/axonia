import type { ReactNode } from "react";
import {
  Button as AriaButton,
  type ButtonProps as AriaButtonProps,
} from "react-aria-components";

import { cn } from "@/lib/cn";

type IconButtonTone = "default" | "danger";

const tones: Record<IconButtonTone, string> = {
  default: "hover:bg-surface-sunken hover:text-foreground",
  danger:
    "hover:text-danger hover:bg-[color-mix(in_srgb,var(--danger)_12%,var(--surface))]",
};

/**
 * The shared styling of a square icon control (edit, delete, close, ...). Exposed so a control that
 * needs its own react-aria markup - e.g. a hover-swapped icon - stays pixel-identical to IconButton
 * without duplicating the class list.
 */
export function iconButtonClass(tone: IconButtonTone = "default"): string {
  return cn(
    "text-muted-foreground inline-flex size-8 flex-none cursor-pointer items-center justify-center rounded-lg transition-colors duration-150 outline-none",
    "focus-visible:ring-focus-ring focus-visible:ring-offset-background focus-visible:ring-2 focus-visible:ring-offset-2",
    "disabled:pointer-events-none disabled:opacity-50 data-[pending]:opacity-50",
    "[&_svg]:size-4",
    tones[tone],
  );
}

export interface IconButtonProps extends Omit<
  AriaButtonProps,
  "className" | "children"
> {
  tone?: IconButtonTone;
  label: string;
  children: ReactNode;
}

export function IconButton({
  tone = "default",
  label,
  children,
  ...props
}: Readonly<IconButtonProps>) {
  return (
    <AriaButton {...props} aria-label={label} className={iconButtonClass(tone)}>
      {children}
    </AriaButton>
  );
}

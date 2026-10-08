import {
  Button as AriaButton,
  type ButtonProps as AriaButtonProps,
} from "react-aria-components";

import { cn } from "@/lib/cn";

type Variant = "primary" | "secondary" | "outline" | "ghost" | "danger";
type Size = "sm" | "md";

export interface ButtonProps extends Omit<AriaButtonProps, "className"> {
  variant?: Variant;
  size?: Size;
  className?: string;
}

const base =
  "inline-flex cursor-pointer items-center justify-center gap-1.5 whitespace-nowrap rounded-md font-medium outline-none transition-[color,background-color,box-shadow,transform] duration-150 focus-visible:ring-2 focus-visible:ring-focus-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:opacity-50 disabled:pointer-events-none data-[pending]:opacity-50 active:scale-[.98]";

const variants: Record<Variant, string> = {
  primary: "bg-primary text-primary-foreground hover:bg-primary-strong",
  secondary:
    "bg-surface-sunken text-foreground border border-border hover:border-border-strong",
  // An outline button keeps its own fill on hover and answers by firming its border and
  // lifting instead. Swapping the fill to `surface-sunken` would have dissolved it into every
  // sunken container it sits on - a slot card, an invite row, a secret box - which are exactly
  // the places an outline button is used.
  outline:
    "bg-surface text-foreground border border-border-strong shadow-sm hover:border-foreground/30 hover:shadow-md",
  ghost: "bg-transparent text-primary hover:bg-surface-sunken",
  danger: "bg-danger text-danger-foreground hover:opacity-90",
};

const sizes: Record<Size, string> = {
  sm: "px-3 py-1.5 text-xs",
  md: "px-4 py-2 text-sm",
};

/**
 * The shared styling of a Button, exposed so a control that must render as something other
 * than a react-aria `<button>` - e.g. an anchor navigating to an external URL - stays
 * pixel-identical to Button without duplicating the class list.
 */
export function buttonClass(
  variant: Variant = "primary",
  size: Size = "md",
  className?: string,
): string {
  return cn(base, variants[variant], sizes[size], className);
}

export function Button({
  variant = "primary",
  size = "md",
  className,
  ...props
}: Readonly<ButtonProps>) {
  return (
    <AriaButton {...props} className={buttonClass(variant, size, className)} />
  );
}

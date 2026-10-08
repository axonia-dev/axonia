import { CopyButton } from "@/components/ui/CopyButton";
import { IdentifierText } from "@/components/ui/IdentifierText";
import { cn } from "@/lib/cn";

export interface CopyFieldProps {
  label: string;
  value: string;
  copyLabel: string;
  hint?: string;
  /**
   * Marks a value nothing is waiting on, so it stops reading as a third thing to go and paste.
   *
   * Drawn as a rule down the side with the fill dropped, rather than as one more filled box with a
   * word in it: a field that is not required has to be separable from its neighbours at a glance,
   * before any label is read. The violet is the secondary accent, deliberately not one of the
   * status tones - "you do not have to do this" is a category, not a verdict, and a warning colour
   * beside two addresses that are fine would say the opposite of what is meant.
   */
  optional?: boolean;
}

/** A value shown in full beside the control that copies it, under a name. The copying itself, and
 *  the three outcomes it can have, belong to {@link CopyButton}. */
export function CopyField({
  label,
  value,
  copyLabel,
  hint,
  optional = false,
}: Readonly<CopyFieldProps>) {
  return (
    <div
      className={cn(
        "px-3.5 py-3",
        optional
          ? "border-secondary rounded-r-lg border-l-2"
          : "bg-surface-sunken rounded-lg",
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-muted-foreground text-[11px] font-semibold tracking-wide uppercase">
          {label}
        </span>
        {optional && (
          <span className="text-secondary rounded-full bg-[color-mix(in_srgb,var(--secondary)_16%,var(--surface))] px-2 py-0.5 text-[10px] font-bold tracking-wide uppercase">
            Optional
          </span>
        )}
      </div>
      <div className="mt-1.5 flex items-center gap-2">
        <code className="min-w-0 flex-1 font-mono text-xs wrap-anywhere">
          <IdentifierText value={value} />
        </code>
        <CopyButton value={value} label={copyLabel} />
      </div>
      {hint !== undefined && (
        <p className="text-muted-foreground mt-2 text-xs">{hint}</p>
      )}
    </div>
  );
}

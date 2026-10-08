import { useId, useState } from "react";
import { SwitchButton, SwitchField } from "react-aria-components";

import { cn } from "@/lib/cn";

interface PolicyAxis<K extends string> {
  key: K;
  label: string;
}

interface AxisRowProps {
  label: string;
  selected: boolean;
  danger: boolean;
  onChange: (next: boolean) => void;
}

/**
 * One switch row. Beyond toggling, it tells the truth about the current state —
 * "Organizers only" when closed, "All members" when open — and, on opening,
 * radiates a short glow ring from the track. Danger-zone rows glow amber.
 */
function AxisRow({
  label,
  selected,
  danger,
  onChange,
}: Readonly<AxisRowProps>) {
  const [pulsing, setPulsing] = useState(false);

  function handleChange(next: boolean) {
    onChange(next);
    if (next) setPulsing(true);
  }

  return (
    <SwitchField isSelected={selected} onChange={handleChange} className="flex">
      <SwitchButton className="group flex flex-1 cursor-pointer items-center gap-3">
        <span
          aria-hidden="true"
          onAnimationEnd={() => setPulsing(false)}
          className={cn(
            "border-border bg-surface-sunken group-data-[focus-visible]:ring-focus-ring flex h-5 w-9 shrink-0 rounded-full border p-0.5 transition-colors group-data-[focus-visible]:ring-2",
            danger
              ? "group-data-[selected]:border-warning group-data-[selected]:bg-warning [--policy-pulse-color:var(--warning)]"
              : "group-data-[selected]:border-primary group-data-[selected]:bg-primary",
            pulsing && "motion-safe:animate-[policy-pulse_500ms_ease-out]",
          )}
        >
          <span className="bg-muted-foreground group-data-[selected]:bg-primary-foreground h-3.5 w-3.5 rounded-full transition-transform group-data-[selected]:translate-x-4" />
        </span>
        <span className="text-foreground flex-1 text-left text-sm font-medium">
          {label}
        </span>
        <span
          aria-hidden="true"
          className="relative shrink-0 text-right text-xs font-semibold"
        >
          <span className="text-muted-foreground transition-opacity group-data-[selected]:opacity-0">
            Organizers only
          </span>
          <span
            className={cn(
              "absolute top-0 right-0 opacity-0 transition-opacity group-data-[selected]:opacity-100",
              danger ? "text-warning" : "text-success",
            )}
          >
            All members
          </span>
        </span>
      </SwitchButton>
    </SwitchField>
  );
}

function pillClass(hasOpen: boolean, danger: boolean): string {
  if (!hasOpen) return "text-muted-foreground border-border";
  return danger
    ? "border-warning/40 text-warning"
    : "border-success/40 text-success";
}

interface PolicyToggleGroupProps<K extends string> {
  title: string;
  danger?: boolean;
  axes: ReadonlyArray<PolicyAxis<K>>;
  value: Record<K, boolean>;
  onToggle: (key: K, next: boolean) => void;
}

/**
 * One area of the Community policy room: a titled cluster of switches over a
 * subset of a policy's axes. The header carries a live counter of how many axes
 * are open to members. The page owns the policy state; this component only
 * renders and reports a single toggle.
 */
export function PolicyToggleGroup<K extends string>({
  title,
  danger = false,
  axes,
  value,
  onToggle,
}: Readonly<PolicyToggleGroupProps<K>>) {
  const openCount = axes.filter(({ key }) => value[key]).length;
  const hasOpen = openCount > 0;
  const titleId = useId();

  return (
    // Named by its own heading, so each area is a landmark a screen reader can jump between and
    // every switch is reachable through the group that governs it rather than as one long list.
    <section
      aria-labelledby={titleId}
      className="border-border flex flex-col gap-3 border-t pt-5 first:border-t-0 first:pt-0"
    >
      <div className="flex items-baseline justify-between gap-3">
        <h2
          id={titleId}
          className={cn(
            "text-sm font-semibold tracking-wide",
            danger && "text-danger",
          )}
        >
          {title}
        </h2>
        <span
          className={cn(
            "rounded-full border px-2 py-0.5 text-xs tabular-nums transition-colors",
            pillClass(hasOpen, danger),
          )}
        >
          {openCount} / {axes.length} open
        </span>
      </div>
      <div className="flex flex-col gap-3">
        {axes.map(({ key, label }) => (
          <AxisRow
            key={key}
            label={label}
            selected={value[key]}
            danger={danger}
            onChange={(next) => onToggle(key, next)}
          />
        ))}
      </div>
    </section>
  );
}

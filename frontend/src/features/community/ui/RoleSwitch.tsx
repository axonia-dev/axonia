import { useState } from "react";
import { SwitchButton, SwitchField } from "react-aria-components";

import type { CommunityRole } from "@/features/community/domain/membership";
import { cn } from "@/lib/cn";

interface RoleSwitchProps {
  role: CommunityRole;
  /** Accessible name for the switch, e.g. the person's display name. */
  label: string;
  isDisabled?: boolean;
  onChange: (next: CommunityRole) => void;
}

/**
 * The Members-page sibling of the Policy room's AxisRow switch: flipping it on
 * makes the person an organizer. The role word crossfades Member⇄Organizer and
 * the track radiates the shared `policy-pulse` glow on promotion.
 */
export function RoleSwitch({
  role,
  label,
  isDisabled,
  onChange,
}: Readonly<RoleSwitchProps>) {
  const [pulsing, setPulsing] = useState(false);
  const selected = role === "ORGANIZER";

  function handleChange(next: boolean) {
    onChange(next ? "ORGANIZER" : "MEMBER");
    if (next) setPulsing(true);
  }

  return (
    <SwitchField
      aria-label={`${label}, organizer`}
      isSelected={selected}
      {...(isDisabled && { isDisabled })}
      onChange={handleChange}
    >
      <SwitchButton className="group flex cursor-pointer items-center gap-3 disabled:cursor-not-allowed disabled:opacity-60">
        <span
          aria-hidden="true"
          className="relative h-4 w-[68px] shrink-0 text-right text-xs font-semibold"
        >
          <span className="text-muted-foreground transition-opacity group-data-[selected]:opacity-0">
            Member
          </span>
          <span className="text-primary absolute top-0 right-0 opacity-0 transition-opacity group-data-[selected]:opacity-100">
            Organizer
          </span>
        </span>
        <span
          aria-hidden="true"
          onAnimationEnd={() => setPulsing(false)}
          className={cn(
            "border-border bg-surface-sunken group-data-[focus-visible]:ring-focus-ring group-data-[selected]:border-primary group-data-[selected]:bg-primary flex h-5 w-9 shrink-0 rounded-full border p-0.5 transition-colors group-data-[focus-visible]:ring-2",
            pulsing && "motion-safe:animate-[policy-pulse_500ms_ease-out]",
          )}
        >
          <span className="bg-muted-foreground group-data-[selected]:bg-primary-foreground h-3.5 w-3.5 rounded-full transition-transform group-data-[selected]:translate-x-4" />
        </span>
      </SwitchButton>
    </SwitchField>
  );
}

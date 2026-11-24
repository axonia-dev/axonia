import { Lightbulb, Sprout } from "lucide-react";
import { type ReactNode, useEffect, useId, useRef } from "react";
import { Link } from "react-router-dom";

import { AreaTag } from "@/components/ui/AreaTag";
import { Switch } from "@/components/ui/Switch";
import { notify, notifyError } from "@/components/ui/toast";
import { useSetAvailability } from "@/features/mentoring/api/use-mentoring";
import type {
  Availability,
  MentoringPage,
} from "@/features/mentoring/domain/mentoring";
import { mentoringErrorMessage } from "@/features/mentoring/mentoring-errors";
import { LimitStepper } from "@/features/mentoring/ui/LimitStepper";
import { ListingAsk } from "@/features/mentoring/ui/ListingAsk";
import { ListingSwitch } from "@/features/mentoring/ui/ListingSwitch";
import {
  expertisePath,
  learningPath,
} from "@/features/mentoring/ui/mentoring-paths";
import { OtherMentors } from "@/features/mentoring/ui/OtherMentors";
import { cn } from "@/lib/cn";

/**
 * Every change saves at once and says so: there is no Save button to forget. The change shows
 * before the server answers, so no control is disabled meanwhile and a keyboard keeps its place.
 */
export function useSaveAvailability(slug: string) {
  const set = useSetAvailability(slug);
  return {
    save: (next: Availability) =>
      set.mutate(next, {
        onSuccess: () => notify("Saved"),
        onError: (error) => notifyError(mentoringErrorMessage(error)),
      }),
  };
}

function Behind({
  label,
  areas,
  empty,
  nudge,
  more,
  to,
  icon,
}: Readonly<{
  label: string;
  areas: string[];
  empty: string;
  nudge: string;
  more: string;
  to: string;
  icon: ReactNode;
}>) {
  return (
    <div className="border-border-strong mt-4 border-t border-dashed pt-4">
      <p className="text-muted-foreground mb-2 text-[11px] font-bold tracking-wider uppercase">
        {label}
      </p>
      {areas.length === 0 ? (
        <div className="flex items-start gap-2.5 rounded-xl border border-[color-mix(in_srgb,var(--warning)_40%,transparent)] bg-[color-mix(in_srgb,var(--warning)_12%,var(--surface))] px-3.5 py-3 text-sm">
          <span aria-hidden className="text-warning-ink mt-0.5 [&_svg]:size-4">
            {icon}
          </span>
          <p className="text-foreground">
            <span className="block font-semibold">{empty}</span>
            <Link to={to} className="font-semibold underline">
              {nudge}
            </Link>
          </p>
        </div>
      ) : (
        <ul className="flex flex-wrap items-center gap-1.5">
          {areas.map((area) => (
            <li key={area} className="max-w-full">
              <AreaTag area={area} size="md" />
            </li>
          ))}
          <li>
            <Link
              to={to}
              className="border-border-strong text-muted-foreground hover:text-foreground inline-flex h-6 items-center rounded-full border border-dashed px-2.5 text-xs font-semibold"
            >
              {more}
            </Link>
          </li>
        </ul>
      )}
    </div>
  );
}

const KINDS = {
  mentor: {
    emoji: "🧭",
    on: "border-[color-mix(in_srgb,var(--mentor)_60%,transparent)] bg-[color-mix(in_srgb,var(--mentor)_9%,var(--surface))]",
  },
  mentee: {
    emoji: "🌱",
    on: "border-[color-mix(in_srgb,var(--mentee)_60%,transparent)] bg-[color-mix(in_srgb,var(--mentee)_9%,var(--surface))]",
  },
} as const;

function Door({
  kind,
  title,
  isOn,
  isDisabled,
  onChange,
  children,
}: Readonly<{
  kind: keyof typeof KINDS;
  title: string;
  isOn: boolean;
  isDisabled: boolean;
  onChange: (on: boolean) => void;
  children: ReactNode;
}>) {
  const titleId = useId();
  return (
    <section
      aria-labelledby={titleId}
      data-door={kind}
      className={cn(
        "border-border bg-surface rounded-2xl border-[1.5px] p-5 motion-safe:transition-colors motion-safe:duration-300",
        isOn && KINDS[kind].on,
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <span aria-hidden className="block text-3xl leading-none">
          {KINDS[kind].emoji}
        </span>
        <Switch
          aria-labelledby={titleId}
          isSelected={isOn}
          isDisabled={isDisabled}
          onChange={onChange}
        />
      </div>
      <h3
        id={titleId}
        className="font-display text-foreground mt-2.5 text-lg font-semibold"
      >
        {title}
      </h3>
      {children}
    </section>
  );
}

/**
 * The member's two ways in: taking mentees and looking for a mentor. Only the switch toggles a
 * door, and each door shows what the member brings through it, or where to add it.
 */
export type DoorKind = "mentor" | "mentee";

export function AvailabilityDoors({
  slug,
  page,
  focusDoor,
}: Readonly<{
  slug: string;
  page: MentoringPage;
  focusDoor?: DoorKind | undefined;
}>) {
  const { save } = useSaveAvailability(slug);
  const { mine, archived } = page;
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (focusDoor === undefined) return;
    box.current
      ?.querySelector<HTMLElement>(`[data-door="${focusDoor}"] input`)
      ?.focus();
  }, [focusDoor]);
  return (
    <div ref={box} className="flex flex-col gap-3">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Door
          kind="mentor"
          title="Taking mentees"
          isOn={mine.takingMentees}
          isDisabled={archived}
          onChange={(takingMentees) => save({ ...mine, takingMentees })}
        >
          {mine.takingMentees && (
            <LimitStepper
              limit={mine.menteeLimit}
              isDisabled={archived}
              onChange={(menteeLimit) => save({ ...mine, menteeLimit })}
            />
          )}
          <Behind
            label="Can mentor on"
            areas={page.canMentorOn}
            empty="No area to mentor on yet."
            nudge="Add areas in My expertise"
            more="Add in My expertise"
            to={expertisePath(slug)}
            icon={<Lightbulb />}
          />
          <OtherMentors slug={slug} other={page.otherMentors} />
          <ListingSwitch
            slug={slug}
            listing={page.listing}
            isDisabled={archived}
          />
          <ListingAsk slug={slug} listing={page.listing} />
        </Door>
        <Door
          kind="mentee"
          title="Looking for a mentor"
          isOn={mine.lookingForMentor}
          isDisabled={archived}
          onChange={(lookingForMentor) => save({ ...mine, lookingForMentor })}
        >
          <Behind
            label="Wants to grow in"
            areas={page.wantsToGrowIn}
            empty="No area to grow in yet."
            nudge="Add areas in Learning"
            more="Add in Learning"
            to={learningPath(slug)}
            icon={<Sprout />}
          />
        </Door>
      </div>
      {archived && (
        <p className="text-muted-foreground text-sm">
          This community is archived.
        </p>
      )}
    </div>
  );
}

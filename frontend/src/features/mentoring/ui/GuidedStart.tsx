import { Check } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import { AreaTag } from "@/components/ui/AreaTag";
import { Button } from "@/components/ui/Button";
import { Switch } from "@/components/ui/Switch";
import type { MentoringPage } from "@/features/mentoring/domain/mentoring";
import {
  type DoorKind,
  useSaveAvailability,
} from "@/features/mentoring/ui/AvailabilityDoors";
import {
  expertisePath,
  learningPath,
} from "@/features/mentoring/ui/mentoring-paths";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

/** The id of the people lists, where the third step leads. */
export const PEOPLE_ID = "mentoring-people";

type Progress = "done" | "now" | "later";

const BADGE: Record<Progress, string> = {
  done: "bg-success text-success-foreground",
  now: "bg-warning text-[#1a0e14]",
  later: "bg-surface-sunken text-muted-foreground",
};

function Step({
  number,
  title,
  progress,
  children,
}: Readonly<{
  number: number;
  title: string;
  progress: Progress;
  children: ReactNode;
}>) {
  return (
    <li
      aria-current={progress === "now" ? "step" : undefined}
      className={cn(
        "border-border bg-surface flex items-start gap-3 rounded-2xl border px-3.5 py-3",
        progress === "now" &&
          "border-[color-mix(in_srgb,var(--warning)_55%,transparent)] bg-[color-mix(in_srgb,var(--warning)_8%,var(--surface))]",
      )}
    >
      <span
        className={cn(
          "font-display grid size-8 flex-none place-items-center rounded-full text-sm font-bold motion-safe:transition-colors",
          BADGE[progress],
        )}
      >
        {progress === "done" ? (
          <Check aria-label="Done" className="size-4" />
        ) : (
          number
        )}
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <p className="text-foreground text-sm font-semibold">{title}</p>
        {children}
      </div>
    </li>
  );
}

/**
 * A first visit's three steps in place of the doors: say which areas, switch on, propose.
 * Each step links or acts where it is done.
 */
export function GuidedStart({
  slug,
  page,
  onSwitchedOn,
}: Readonly<{
  slug: string;
  page: MentoringPage;
  /** Switching either on gives way to the doors; focus moves to that door's switch. */
  onSwitchedOn: (door: DoorKind) => void;
}>) {
  const { save } = useSaveAvailability(slug);
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const { mine } = page;
  const areas = [...new Set([...page.wantsToGrowIn, ...page.canMentorOn])];
  const hasAreas = areas.length > 0;
  const switchedOn = mine.takingMentees || mine.lookingForMentor;
  const locked = page.archived;
  let switchStep: Progress = hasAreas ? "now" : "later";
  if (switchedOn) switchStep = "done";
  return (
    <section
      aria-labelledby="guided-start-title"
      className="border-border flex flex-col gap-4 rounded-3xl border bg-[color-mix(in_srgb,var(--secondary)_6%,var(--surface))] p-5"
    >
      <div>
        <h2
          id="guided-start-title"
          className="font-display text-foreground text-xl font-semibold"
        >
          Start mentoring in three steps
        </h2>
        <p className="text-muted-foreground text-sm">
          Two members meet every week or two, for months, and work through
          topics together.
        </p>
      </div>
      <ol className="flex flex-col gap-2.5">
        <Step number={1} title="Areas" progress={hasAreas ? "done" : "now"}>
          {hasAreas && (
            <ul className="flex flex-wrap gap-1.5">
              {areas.map((area) => (
                <li key={area} className="max-w-full">
                  <AreaTag area={area} />
                </li>
              ))}
            </ul>
          )}
          <p className="text-muted-foreground text-sm">
            <Link
              to={learningPath(slug)}
              className="text-foreground inline-block font-semibold underline"
            >
              Areas to grow in, in Learning
            </Link>
            {" · "}
            <Link
              to={expertisePath(slug)}
              className="text-foreground inline-block font-semibold underline"
            >
              Areas to mentor on, in My expertise
            </Link>
          </p>
        </Step>
        <Step number={2} title="Switch on" progress={switchStep}>
          <div className="flex flex-wrap gap-x-6 gap-y-2">
            <Switch
              label="Looking for a mentor"
              isSelected={mine.lookingForMentor}
              isDisabled={locked}
              onChange={(lookingForMentor) => {
                if (lookingForMentor) onSwitchedOn("mentee");
                save({ ...mine, lookingForMentor });
              }}
            />
            <Switch
              label="Taking mentees"
              isSelected={mine.takingMentees}
              isDisabled={locked}
              onChange={(takingMentees) => {
                if (takingMentees) onSwitchedOn("mentor");
                save({ ...mine, takingMentees });
              }}
            />
          </div>
        </Step>
        <Step
          number={3}
          title="Propose"
          progress={switchedOn ? "now" : "later"}
        >
          <p className="text-muted-foreground text-sm">
            Propose to someone listed below, or wait for a proposal.
          </p>
          <div>
            <Button
              variant="secondary"
              size="sm"
              onPress={() =>
                document.getElementById(PEOPLE_ID)?.scrollIntoView({
                  behavior: reduced ? "auto" : "smooth",
                  block: "start",
                })
              }
            >
              See who is here
            </Button>
          </div>
        </Step>
      </ol>
    </section>
  );
}

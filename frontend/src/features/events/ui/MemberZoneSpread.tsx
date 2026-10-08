import { Users } from "lucide-react";
import { Fragment, useContext } from "react";
import {
  Button as AriaButton,
  Disclosure,
  DisclosurePanel,
  DisclosureStateContext,
} from "react-aria-components";

import { useMemberZones } from "@/features/events/api/use-member-zones";
import type { ZoneRow } from "@/features/events/domain/zone-spread";
import {
  projectZones,
  spreadSummary,
} from "@/features/events/domain/zone-spread";
import type { ZonedParts } from "@/features/events/domain/zoned-instant";
import { zonedToInstant } from "@/features/events/domain/zoned-instant";

const HAIRLINE_HOURS = [6, 12, 18];

function Track({ row }: Readonly<{ row: ZoneRow }>) {
  return (
    <div className="bg-surface-sunken relative hidden h-1.5 rounded-full sm:block">
      {HAIRLINE_HOURS.map((hour) => (
        <span
          key={hour}
          aria-hidden
          className="bg-border-strong absolute inset-y-0 w-px"
          style={{ left: `${((hour / 24) * 100).toString()}%` }}
        />
      ))}
      <span
        aria-hidden
        className="bg-primary absolute inset-y-0 rounded-full"
        style={{
          left: `${row.offsetPct.toString()}%`,
          width: `${row.widthPct.toString()}%`,
          minWidth: "3px",
        }}
      />
    </div>
  );
}

function Hours({ row }: Readonly<{ row: ZoneRow }>) {
  const span =
    row.localEnd === row.localStart
      ? row.localStart
      : `${row.localStart} - ${row.localEnd}`;
  return (
    <span className="text-foreground text-right text-xs tabular-nums">
      {span}
      {row.day !== null && (
        <span className="text-muted-foreground"> {row.day}</span>
      )}
    </span>
  );
}

function Toggle() {
  const isExpanded = useContext(DisclosureStateContext)?.isExpanded ?? false;
  return (
    <AriaButton
      slot="trigger"
      className="text-primary focus-visible:ring-focus-ring hover:bg-surface-sunken -mx-1 cursor-pointer rounded px-1 text-xs font-medium outline-none focus-visible:ring-2"
    >
      {isExpanded ? "Hide" : "Which"}
    </AriaButton>
  );
}

/**
 * Where a candidate hour lands for the people expected to attend it.
 *
 * A note beside the field rather than a verdict on it: it states each zone's local hours and
 * grades none of them, because an evening slot is what a good many communities meet in on purpose
 * and a rule here would be the product deciding that for them. Shut, it is one line naming the two
 * ends of the spread; the per-zone rows are a disclosure away.
 *
 * Nothing at all below two zones, nothing for an occurrence whose hour is still open, and nothing
 * while the roster's zones are still loading: a form that flashes a line and then withdraws it is
 * worse than one that never showed it.
 */
export function MemberZoneSpread({
  slug,
  when,
  zone,
  durationMinutes,
}: Readonly<{
  slug: string;
  when: ZonedParts;
  zone: string;
  durationMinutes: number | null;
}>) {
  const zones = useMemberZones(slug);
  const startsAt = zonedToInstant(when, zone);
  // An hour left open is not midnight: `zonedToInstant` has to pick one to compose a date, and
  // placing a spread on it would answer a question nobody has asked yet.
  const untimed = when.time.trim() === "";

  if (zones.data === undefined || startsAt === null || untimed) return null;

  // An unstated duration stays unstated: the rows mark the moment rather than invent a length for
  // it, and the summary then names two start times, which is what it says it does.
  const rows = projectZones(zones.data, startsAt, durationMinutes ?? 0, zone);
  const summary = spreadSummary(rows);

  if (summary === null) return null;

  return (
    <Disclosure className="border-border bg-surface-sunken/40 flex flex-col rounded-md border px-3 py-2.5">
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-muted-foreground min-w-0 text-xs">{summary}</p>
        <Toggle />
      </div>
      {/* The space above the rows sits inside the panel: a shut one is `hidden="until-found"`,
          which stays a flex item, so a gap on the Disclosure would still be drawn before it. */}
      <DisclosurePanel>
        <div className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1.5 pt-2 sm:grid-cols-[auto_minmax(0,1fr)_104px_auto]">
          {rows.map((row) => (
            <Fragment key={row.zone}>
              <span className="text-foreground truncate text-xs">
                {row.city}
              </span>
              <span className="text-muted-foreground inline-flex items-center gap-1 text-xs tabular-nums">
                <Users aria-hidden className="h-3 w-3" />
                {row.count}
                <span className="sr-only">
                  {row.count === 1 ? "member" : "members"}
                </span>
              </span>
              <Track row={row} />
              <Hours row={row} />
            </Fragment>
          ))}
        </div>
      </DisclosurePanel>
    </Disclosure>
  );
}

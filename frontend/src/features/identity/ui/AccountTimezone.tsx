import { useEffect, useMemo, useState } from "react";

import { SettingsCard, SettingsRow } from "@/components/patterns/SettingsCard";
import { ComboBox } from "@/components/ui/ComboBox";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { useMe } from "@/features/identity/api/use-me";
import {
  useClearTimeZone,
  useDeclareTimeZone,
} from "@/features/identity/api/use-time-zone";
import { ignoreTypedZoneText, viewerZone, zoneOptions } from "@/lib/zones";

const MODES = [
  { id: "automatic", label: "Automatic" },
  { id: "manual", label: "Manual" },
];

/**
 * Re-renders on the next minute boundary, and reschedules from each render, so the clock below is
 * the time it is rather than the time the page was opened. An unaligned interval would be right
 * on average and visibly a minute behind the rest of the time.
 */
function useMinuteTick(): void {
  const [, setTick] = useState(0);
  useEffect(() => {
    const timer = setTimeout(
      () => setTick((count) => count + 1),
      60_000 - (Date.now() % 60_000),
    );
    return () => clearTimeout(timer);
  });
}

/** The hour it is where the member is, as a clock rather than as a sentence. */
function localTime(timeZone: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date());
}

/**
 * Which clock this member's sessions and messages are rendered on.
 *
 * Facts and one control, and nothing else. A two-option toggle beside a named zone already says
 * what it does, so there is no prose under it: the card states the hour, the zone, and whether the
 * value came from the browser or from the member.
 *
 * Returning to Automatic clears the declaration rather than writing the browser's reading as a
 * second declared value. The observation keeps refreshing underneath either way, so clearing lands
 * on where this member is now rather than on where they were when they declared.
 */
export function AccountTimezone() {
  const me = useMe();
  useMinuteTick();
  const declare = useDeclareTimeZone();
  const clear = useClearTimeZone();
  const zone = me.data?.timeZone ?? viewerZone();
  const isOwn = me.data?.timeZoneIsOwn ?? false;
  const zones = useMemo(() => zoneOptions(zone), [zone]);

  if (me.data === undefined) return null;

  return (
    <SettingsCard title="Timezone">
      <SettingsRow>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="font-display text-foreground text-2xl font-semibold tabular-nums">
            {localTime(zone)}
          </span>
          {/* Named here only while there is no picker below stating the same zone. */}
          {!isOwn && (
            <span className="text-muted-foreground truncate text-sm">
              {zone}
            </span>
          )}
        </div>
        <SegmentedControl
          label="Timezone source"
          hideLabel
          value={isOwn ? "manual" : "automatic"}
          options={MODES}
          onChange={(next) =>
            next === "automatic" ? clear.mutate() : declare.mutate(zone)
          }
        />
      </SettingsRow>
      {isOwn && (
        <SettingsRow>
          <div className="flex-1">
            <ComboBox
              label="Timezone"
              hideLabel
              value={zone}
              options={zones}
              onInputChange={ignoreTypedZoneText}
              onSelect={(option) => declare.mutate(option.id)}
            />
          </div>
        </SettingsRow>
      )}
    </SettingsCard>
  );
}

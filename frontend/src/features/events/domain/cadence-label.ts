import type { CadenceUnit, MonthlyRule } from "@/features/events/domain/series";

const ORDINALS = ["first", "second", "third", "fourth", "last"] as const;
const DAYS_PER_WEEK = 7;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** A `YYYY-MM-DD` start date, read as a civil date: the zone it is shown in plays no part. */
function civil(startsOn: string): Date | null {
  if (!ISO_DATE.test(startsOn)) return null;
  const date = new Date(`${startsOn}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** "on the second Wednesday" or "on day 13", as the series' start date reads under each rule. A
 *  fifth weekday reads as the last, matching how the backend steps it. */
export function monthlyRuleLabel(rule: MonthlyRule, startsOn: string): string {
  const date = civil(startsOn);
  if (rule === "DAY_OF_MONTH") {
    return date === null ? "on the same day" : `on day ${date.getUTCDate()}`;
  }
  if (date === null) return "on the same weekday";
  const ordinal = ORDINALS[Math.floor((date.getUTCDate() - 1) / DAYS_PER_WEEK)];
  const weekday = date.toLocaleDateString("en-GB", {
    weekday: "long",
    timeZone: "UTC",
  });
  return `on the ${ordinal} ${weekday}`;
}

interface CadenceLabelInput {
  count: number;
  unit: CadenceUnit;
  monthlyBy: MonthlyRule;
  startsOn: string;
}

/** "Every month on the second Wednesday", "Every 3 weeks". */
export function cadenceLabel({
  count,
  unit,
  monthlyBy,
  startsOn,
}: CadenceLabelInput): string {
  if (!Number.isInteger(count) || count < 1 || count > 999) {
    return "Enter a whole number of 1 or more";
  }
  const noun = unit.toLowerCase();
  const every = count === 1 ? `Every ${noun}` : `Every ${count} ${noun}s`;
  return unit === "MONTH"
    ? `${every} ${monthlyRuleLabel(monthlyBy, startsOn)}`
    : every;
}

import { z } from "zod";

import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { scheduleEntrySchema } from "@/features/events/domain/schedule";

export const memoryTracesSchema = z.object({
  loves: z.number(),
  recorded: z.boolean(),
  artifacts: z.number(),
});
export const memoryCursorSchema = z.object({ at: z.string(), id: z.number() });
export const memorySchema = z.object({
  entry: scheduleEntrySchema,
  traces: memoryTracesSchema,
});
export const memoryPageSchema = z.object({
  items: z.array(memorySchema),
  next: memoryCursorSchema.nullable(),
});

export type MemoryTraces = z.infer<typeof memoryTracesSchema>;
export type MemoryCursor = z.infer<typeof memoryCursorSchema>;
export type Memory = z.infer<typeof memorySchema>;
export type MemoryPage = z.infer<typeof memoryPageSchema>;

export const NO_TRACES: MemoryTraces = {
  loves: 0,
  recorded: false,
  artifacts: 0,
};

const DAY_MS = 86_400_000;
const FADE_FLOOR = 0.35;
const FADE_DAYS = 180;
const WEEKS_UNTIL_MONTHS_DAYS = 35;
const MONTHS_PER_YEAR = 12;
const YEARS_FROM_MONTHS = 24;

/** A past session nobody has closed yet: it still owes an answer on whether it was held. */
export function isAwaitingAnswer(entry: ScheduleEntry): boolean {
  return entry.status === "SCHEDULED";
}

function daysBetween(dayIso: string, todayIso: string): number {
  return Math.floor(
    (new Date(todayIso).getTime() - new Date(dayIso).getTime()) / DAY_MS,
  );
}

function wholeMonthsBetween(dayIso: string, todayIso: string): number {
  const day = new Date(dayIso);
  const today = new Date(todayIso);
  const months =
    (today.getUTCFullYear() - day.getUTCFullYear()) * MONTHS_PER_YEAR +
    today.getUTCMonth() -
    day.getUTCMonth();
  return today.getUTCDate() < day.getUTCDate() ? months - 1 : months;
}

function plural(count: number, unit: string): string {
  return `${count} ${unit}${count === 1 ? "" : "s"} ago`;
}

export function agoLabel(dayIso: string, todayIso: string): string {
  const days = daysBetween(dayIso, todayIso);
  if (days < 1) return "earlier today";
  if (days === 1) return "yesterday";
  if (days < 7) return `${days} days ago`;
  if (days < WEEKS_UNTIL_MONTHS_DAYS)
    return plural(Math.floor(days / 7), "week");
  const months = wholeMonthsBetween(dayIso, todayIso);
  if (months < YEARS_FROM_MONTHS) return plural(months, "month");
  return plural(Math.floor(months / MONTHS_PER_YEAR), "year");
}

/** 1 for today, easing to 0.65 from 180 days back. */
export function memoryFade(dayIso: string, todayIso: string): number {
  const days = Math.max(0, daysBetween(dayIso, todayIso));
  return 1 - (FADE_FLOOR * Math.min(days, FADE_DAYS)) / FADE_DAYS;
}

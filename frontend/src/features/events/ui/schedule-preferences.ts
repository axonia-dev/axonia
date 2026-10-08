import type { MonthPosition } from "@/features/events/domain/schedule-position";
import {
  formatMonthParam,
  parseMonthParam,
} from "@/features/events/domain/schedule-position";

export type ScheduleView = "timeline" | "calendar";

const VIEW_KEY = "axonia-schedule-view";
const MONTH_KEY = "axonia-schedule-month";

export function parseView(raw: string | null): ScheduleView | null {
  return raw === "timeline" || raw === "calendar" ? raw : null;
}

export function storedView(): ScheduleView | null {
  return parseView(localStorage.getItem(VIEW_KEY));
}

export function storeView(view: ScheduleView): void {
  localStorage.setItem(VIEW_KEY, view);
}

function monthKey(slug: string): string {
  return `${MONTH_KEY}:${slug}`;
}

export function storedMonth(slug: string): MonthPosition | null {
  return parseMonthParam(sessionStorage.getItem(monthKey(slug)));
}

export function storeMonth(slug: string, month: MonthPosition): void {
  sessionStorage.setItem(monthKey(slug), formatMonthParam(month));
}

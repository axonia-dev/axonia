import type { Page } from "@playwright/test";

import { community, shellOfCommunity, SLUG, stubReads } from "./community-home";

export const NOW = "2026-10-16T08:59:00Z";
export const PAUSED_AT = "2026-10-16T09:00:00Z";
export const STARTS_AT = "2026-10-16T09:30:00Z";
export const NEXT_TITLE = "Observability night";
const NEXT_SESSION_ID = 900;
const DAY_MS = 86_400_000;
const FIRST_PAGE_SIZE = 12;
const SECOND_PAGE_SIZE = 8;

export const memoryTitle = (id: number) => `Retro ${id}`;

function entry(sessionId: number, scheduledAt: string, title: string) {
  return {
    sessionId,
    seriesId: null,
    title,
    displayTitle: title,
    status: "HELD",
    occurrenceDate: null,
    scheduledAt,
    hasTime: true,
    durationMinutes: 60,
    seriesTimeZone: null,
    virtual: false,
    place: null,
    sections: [],
  };
}

const eveningOf = (daysBack: number) =>
  new Date(Date.parse("2026-10-14T18:00:00Z") - daysBack * DAY_MS);

function memory(id: number, at: Date) {
  return {
    entry: entry(id, at.toISOString(), memoryTitle(id)),
    traces: { loves: id % 3, recorded: id % 2 === 0, artifacts: 0 },
  };
}

const firstPage = Array.from({ length: FIRST_PAGE_SIZE }, (_, index) =>
  memory(100 + FIRST_PAGE_SIZE - index, eveningOf(index * 3)),
);
const secondPage = Array.from({ length: SECOND_PAGE_SIZE }, (_, index) =>
  memory(SECOND_PAGE_SIZE - index, eveningOf(36 + index * 7)),
);
export const oldest = secondPage.at(-1)!;
export const newest = firstPage[0];

const nextSession = {
  ...entry(NEXT_SESSION_ID, STARTS_AT, NEXT_TITLE),
  status: "SCHEDULED",
  durationMinutes: 60,
};

const laterSessions = Array.from({ length: 6 }, (_, index) => ({
  ...entry(
    NEXT_SESSION_ID + 1 + index,
    new Date(
      Date.parse("2026-10-23T17:00:00Z") + index * 7 * DAY_MS,
    ).toISOString(),
    `Later ${index + 1}`,
  ),
  status: "SCHEDULED",
}));

const ribbon = {
  firstSessionAt: oldest.entry.scheduledAt,
  ticks: [
    ...[...secondPage, ...firstPage]
      .map((item) => ({ at: item.entry.scheduledAt, kind: "MEMORY" }))
      .sort((a, b) => a.at.localeCompare(b.at)),
    ...[nextSession, ...laterSessions].map((upcoming) => ({
      at: upcoming.scheduledAt,
      kind: "UPCOMING",
    })),
  ],
};

export interface TimelineStubs {
  /** Resolves the second memories page once it is requested; defaults to answering at once. */
  holdSecondPage?: Promise<void>;
  memoryRequests: string[];
  /** A session every Friday for years ahead, answered for whatever range is asked. */
  weekly?: boolean;
}

const WEEKLY_FROM = Date.parse("2026-10-23T17:00:00Z");

/** The weekly sessions from the next Friday on, inside [from, to). */
function weeklyIn(fromIso: string, toIso: string) {
  const from = Date.parse(fromIso);
  const to = Date.parse(toIso);
  const sessions = [];
  for (let at = WEEKLY_FROM, id = 1; at < to; at += 7 * DAY_MS, id += 1) {
    if (at >= from) {
      sessions.push({
        ...entry(2000 + id, new Date(at).toISOString(), `Weekly ${id}`),
        status: "SCHEDULED",
      });
    }
  }
  return sessions;
}

/** Answers the schedule and the ribbon from the weekly sessions in the range each asks for. */
async function stubWeekly(page: Page): Promise<void> {
  const path = `/axonia/communities/${SLUG}/schedule`;
  await page.route(
    (url) => url.pathname === path || url.pathname === `${path}/ribbon`,
    (route) => {
      const url = new URL(route.request().url());
      const range = [
        url.searchParams.get("from") ?? "",
        url.searchParams.get("to") ?? "",
      ] as const;
      const sessions = weeklyIn(...range);
      const body = url.pathname.endsWith("/ribbon")
        ? {
            firstSessionAt: oldest.entry.scheduledAt,
            ticks: [
              ...ribbon.ticks.filter((tick) => tick.kind === "MEMORY"),
              ...sessions.map((session) => ({
                at: session.scheduledAt,
                kind: "UPCOMING",
              })),
            ].filter(
              (tick) =>
                tick.at >= new Date(range[0]).toISOString() &&
                tick.at < new Date(range[1]).toISOString(),
            ),
          }
        : sessions;
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(body),
      });
    },
  );
}

export async function stubTimeline(
  page: Page,
  stubs: TimelineStubs,
): Promise<void> {
  const base = `**/axonia/communities/${SLUG}/schedule`;
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}`]: community,
    [`/axonia/communities/${SLUG}/schedule`]: [nextSession, ...laterSessions],
    [`/axonia/communities/${SLUG}/schedule/ribbon`]: ribbon,
  });
  if (stubs.weekly) await stubWeekly(page);
  await page.route(`${base}/memories**`, async (route) => {
    const url = new URL(route.request().url());
    stubs.memoryRequests.push(url.search);
    const older = url.searchParams.has("before");
    if (older) await stubs.holdSecondPage;
    const body = older
      ? { items: secondPage, next: null }
      : {
          items: firstPage,
          next: { at: firstPage.at(-1)!.entry.scheduledAt, id: 1 },
        };
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  });
}

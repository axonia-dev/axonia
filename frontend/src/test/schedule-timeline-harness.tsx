import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";

import { useMemories } from "@/features/events/api/use-memories";
import { useRibbon } from "@/features/events/api/use-ribbon";
import { useSchedule } from "@/features/events/api/use-schedule";
import { useSeriesList } from "@/features/events/api/use-series";
import type { Memory } from "@/features/events/domain/memory";
import type { RibbonTick } from "@/features/events/domain/ribbon";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Series } from "@/features/events/domain/series";
import { ScheduleTimeline } from "@/features/events/ui/ScheduleTimeline";
import { useUsers } from "@/features/identity/api/use-users";

/**
 * Renders ScheduleTimeline over mocked queries. Each caller file must itself `vi.mock` use-schedule,
 * use-series, use-users, use-memories, use-ribbon and invalidate-memories: a mock is hoisted only in
 * the file that declares it.
 */
interface TimelineGiven {
  schedule?: { data?: ScheduleEntry[]; isLoading?: boolean; isError?: boolean };
  series?: { content: Series[] };
  users?: Record<string, { id: string; displayName: string }>;
  memories?: {
    pages?: Memory[][];
    hasNextPage?: boolean;
    fetchNextPage?: () => Promise<unknown>;
  };
  ribbon?: { firstSessionAt: string | null; ticks: RibbonTick[] };
  slug?: string;
  fromIso?: string;
  toIso?: string;
  canManage?: boolean;
  /** The instant the clock reads; defaults to fromIso, the start of the timeline's today. */
  now?: string;
  showMore?: boolean;
  /** Fakes timeouts too, so a test can advance the clock past a session's end. */
  fakeTimeouts?: boolean;
}

function memoriesQueryOf(given: TimelineGiven) {
  const pages = given.memories?.pages ?? [];
  return {
    data: {
      pages: pages.map((items) => ({ items, next: null })),
      pageParams: [],
    },
    isLoading: false,
    isError: false,
    hasNextPage: given.memories?.hasNextPage ?? false,
    isFetchingNextPage: false,
    fetchNextPage: given.memories?.fetchNextPage ?? vi.fn(),
  };
}

function wireQueries(given: TimelineGiven) {
  vi.mocked(useSchedule).mockReturnValue({
    data: given.schedule?.data,
    isLoading: given.schedule?.isLoading ?? false,
    isError: given.schedule?.isError ?? false,
  } as never);
  vi.mocked(useSeriesList).mockReturnValue({ data: given.series } as never);
  vi.mocked(useUsers).mockReturnValue({ data: given.users } as never);
  vi.mocked(useMemories).mockReturnValue(memoriesQueryOf(given) as never);
  vi.mocked(useRibbon).mockReturnValue({
    data: given.ribbon ?? { firstSessionAt: null, ticks: [] },
  } as never);
}

export function renderTimeline(given: TimelineGiven = {}) {
  const fromIso = given.fromIso ?? "2026-06-01T00:00:00.000Z";
  vi.useFakeTimers({
    toFake: given.fakeTimeouts
      ? ["Date", "setTimeout", "clearTimeout"]
      : ["Date"],
    now: new Date(given.now ?? fromIso),
  });
  wireQueries(given);
  const onShowMore = vi.fn();
  const view = (next: TimelineGiven) => (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ScheduleTimeline
          slug={next.slug ?? "x"}
          fromIso={fromIso}
          toIso={next.toIso ?? "2026-09-01T00:00:00.000Z"}
          {...(next.showMore === false ? {} : { onShowMore })}
          canManage={next.canManage ?? false}
        />
      </MemoryRouter>
    </QueryClientProvider>
  );
  const { rerender } = render(view(given));
  return {
    onShowMore,
    rerenderWith: (next: TimelineGiven) => {
      wireQueries(next);
      rerender(view(next));
    },
  };
}

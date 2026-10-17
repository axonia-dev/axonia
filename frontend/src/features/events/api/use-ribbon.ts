import { useQueries } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { scheduleKeys } from "@/features/events/api/query-keys";
import type { Ribbon, RibbonTick } from "@/features/events/domain/ribbon";
import { mergeTicks, yearRange } from "@/features/events/domain/ribbon";

const gateway = httpEventsGateway;

export interface RibbonYears {
  ticks: RibbonTick[];
  /** The community's first session, null with none, undefined until a year has answered. */
  firstSessionAt: string | null | undefined;
}

function combine(results: { data?: Ribbon | undefined }[]): RibbonYears {
  return {
    ticks: mergeTicks(results.map((result) => result.data?.ticks ?? [])),
    firstSessionAt: results.find((result) => result.data !== undefined)?.data
      ?.firstSessionAt,
  };
}

/**
 * The ribbon's ticks, one cached query per UTC year, so a rolling window reuses every year it
 * already loaded instead of asking again for each place it rolls to.
 */
export function useRibbon(slug: string, years: number[]): RibbonYears {
  return useQueries({
    queries: years.map((year) => {
      const { fromIso, toIso } = yearRange(year);
      return {
        queryKey: scheduleKeys.ribbon(slug, fromIso, toIso),
        queryFn: () => gateway.getRibbon(slug, fromIso, toIso),
      };
    }),
    combine,
  });
}

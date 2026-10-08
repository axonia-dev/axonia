import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  scheduleKeys,
  seriesKeys,
  sessionKeys,
} from "@/features/events/api/query-keys";
import type { SeriesInput } from "@/features/events/domain/events-gateway";

const gateway = httpEventsGateway;
const LIST_PARAMS = { page: 0, size: 50 } as const;

export function useSeriesList(slug: string) {
  return useQuery({
    queryKey: seriesKeys.community(slug),
    queryFn: () => gateway.listSeries(slug, LIST_PARAMS),
  });
}

export function useStartSeries(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SeriesInput) => gateway.startSeries(slug, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: seriesKeys.community(slug),
      });
      void queryClient.invalidateQueries({ queryKey: scheduleKeys.all });
    },
  });
}

/**
 * Ends a series: no occurrence after this one is produced, and the ones already on the calendar
 * stay where they are.
 *
 * Invalidates the schedule alongside the series list, because every ghost occurrence the calendar
 * projects past today comes from the cadence rather than from a row, and an ended series has to
 * stop drawing them.
 */
export function useEndSeries(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => gateway.endSeries(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: seriesKeys.community(slug),
      });
      void queryClient.invalidateQueries({ queryKey: scheduleKeys.all });
    },
  });
}

export function useUpdateSeries(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: SeriesInput }) =>
      gateway.updateSeries(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: seriesKeys.community(slug),
      });
      void queryClient.invalidateQueries({
        queryKey: sessionKeys.community(slug),
      });
      void queryClient.invalidateQueries({ queryKey: scheduleKeys.all });
    },
  });
}

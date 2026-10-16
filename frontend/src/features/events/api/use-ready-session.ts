import { useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";

import { sessionQuery } from "@/features/events/api/use-session";

export function useReadySession() {
  const queryClient = useQueryClient();
  return useMemo(
    () => ({
      ready: async (id: number) => {
        await queryClient.query({ ...sessionQuery(id), staleTime: "static" });
      },
      prefetch: (id: number) => {
        queryClient.query(sessionQuery(id)).catch(() => undefined);
      },
    }),
    [queryClient],
  );
}

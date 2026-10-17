import { useMutation, useQuery } from "@tanstack/react-query";

import { httpRecognitionGateway } from "@/features/recognition/adapters/http-recognition-gateway";
import { recognitionKeys } from "@/features/recognition/api/query-keys";
import type { MarkImpactSeen } from "@/features/recognition/domain/recognition";

const gateway = httpRecognitionGateway;

export function useNewLoves({
  communityId,
  enabled,
}: Readonly<{ communityId: number | undefined; enabled: boolean }>) {
  return useQuery({
    queryKey: recognitionKeys.newLoves(communityId ?? "all"),
    queryFn: () => gateway.getNewLoves(communityId),
    enabled,
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });
}

export function useMarkImpactSeen() {
  return useMutation({
    mutationFn: (input: MarkImpactSeen) => gateway.markImpactSeen(input),
  });
}

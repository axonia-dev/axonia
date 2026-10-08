import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpRecognitionGateway } from "@/features/recognition/adapters/http-recognition-gateway";
import { recognitionKeys } from "@/features/recognition/api/query-keys";
import type { ContributionKey } from "@/features/recognition/domain/recognition";

const gateway = httpRecognitionGateway;

function useInvalidateRecognition(key: ContributionKey) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({
      queryKey: recognitionKeys.summary(key),
    });
    void queryClient.invalidateQueries({
      queryKey: recognitionKeys.myContributions(),
    });
  };
}

export function useLoveSummary(key: ContributionKey) {
  return useQuery({
    queryKey: recognitionKeys.summary(key),
    queryFn: () => gateway.getSummary(key),
  });
}

export function useLove(key: ContributionKey) {
  const invalidate = useInvalidateRecognition(key);
  return useMutation({
    mutationFn: () => gateway.love(key),
    onSuccess: invalidate,
  });
}

export function useUnlove(key: ContributionKey) {
  const invalidate = useInvalidateRecognition(key);
  return useMutation({
    mutationFn: () => gateway.unlove(key),
    onSuccess: invalidate,
  });
}

export function useMyContributions() {
  return useQuery({
    queryKey: recognitionKeys.myContributions(),
    queryFn: () => gateway.listMyContributions(),
  });
}

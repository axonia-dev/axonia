import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import type { FocalPoint } from "@/features/community/domain/community";
import type { LookAndFeelAsset } from "@/features/community/domain/community-gateway";

const gateway = httpCommunityGateway;

function useRefresh(slug: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({
      queryKey: communityKeys.detail(slug),
    });
    void queryClient.invalidateQueries({ queryKey: communityKeys.lists() });
  };
}

export function useSetAccent(slug: string) {
  const refresh = useRefresh(slug);
  return useMutation({
    mutationFn: (accentColor: string | null) =>
      gateway.setAccent(slug, accentColor),
    onSuccess: refresh,
  });
}

export function useAddAsset(slug: string) {
  const refresh = useRefresh(slug);
  return useMutation({
    mutationFn: (vars: { asset: LookAndFeelAsset; file: File }) =>
      gateway.addAsset(slug, vars.asset, vars.file),
    onSuccess: refresh,
  });
}

export function useRemoveAsset(slug: string) {
  const refresh = useRefresh(slug);
  return useMutation({
    mutationFn: (asset: LookAndFeelAsset) => gateway.removeAsset(slug, asset),
    onSuccess: refresh,
  });
}

export function useSetFocalPoint(slug: string) {
  const refresh = useRefresh(slug);
  return useMutation({
    mutationFn: (vars: { asset: LookAndFeelAsset; focus: FocalPoint }) =>
      gateway.setFocalPoint(slug, vars.asset, vars.focus),
    onSuccess: refresh,
  });
}

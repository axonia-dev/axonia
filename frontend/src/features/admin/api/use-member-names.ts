import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpAdminGateway;

export function useMemberNamePatterns() {
  return useQuery({
    queryKey: adminKeys.memberNamePatterns(),
    queryFn: () => gateway.getMemberNamePatterns(),
  });
}

/** Never retried: a 400 is the matcher's verdict on the patterns, and asking again says the same. */
export function useMemberNamePreview(patterns: ReadonlyArray<string>) {
  return useQuery({
    queryKey: adminKeys.memberNamePreview(patterns),
    queryFn: () => gateway.previewMemberNames([...patterns]),
    placeholderData: keepPreviousData,
    retry: false,
  });
}

export function useSaveMemberNamePatterns() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (patterns: string[]) =>
      gateway.saveMemberNamePatterns(patterns),
    onSuccess: () => queryClient.invalidateQueries(),
  });
}

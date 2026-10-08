import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { accessKeys, adminKeys } from "@/features/admin/api/query-keys";
import type { BindGroupBody } from "@/features/admin/domain/admin-gateway";

const gateway = httpAdminGateway;

export function useBindGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: BindGroupBody) => gateway.bindGroup(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.idpGroups() });
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
      void queryClient.invalidateQueries({ queryKey: accessKeys.all });
    },
  });
}

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { accessKeys, adminKeys } from "@/features/admin/api/query-keys";
import type { GrantRoleBody } from "@/features/admin/domain/admin-gateway";

const gateway = httpAdminGateway;

export function useGrantRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: GrantRoleBody) => gateway.grantRole(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
      void queryClient.invalidateQueries({ queryKey: accessKeys.all });
    },
  });
}

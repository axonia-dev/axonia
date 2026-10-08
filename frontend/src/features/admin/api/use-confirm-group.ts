import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { accessKeys, adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpAdminGateway;

export function useConfirmGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => gateway.confirmGroupBinding(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.idpGroups() });
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
      void queryClient.invalidateQueries({ queryKey: accessKeys.all });
    },
  });
}

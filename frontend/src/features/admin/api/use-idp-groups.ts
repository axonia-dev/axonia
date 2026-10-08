import { useQuery } from "@tanstack/react-query";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpAdminGateway;

export function useIdpGroups() {
  return useQuery({
    queryKey: adminKeys.idpGroups(),
    queryFn: () => gateway.listIdpGroups(),
  });
}

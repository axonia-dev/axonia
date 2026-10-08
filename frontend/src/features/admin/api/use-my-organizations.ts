import { useQuery } from "@tanstack/react-query";

import { httpOrganizationsGateway } from "@/features/admin/adapters/http-organizations-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpOrganizationsGateway;

export function useMyOrganizations(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: adminKeys.myOrganizations(),
    queryFn: () => gateway.listMyOrganizations(),
    // Callers on public routes disable the query for anonymous visitors,
    // who have no token to authenticate it with.
    enabled: options?.enabled ?? true,
  });
}

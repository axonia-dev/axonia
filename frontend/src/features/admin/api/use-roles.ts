import { useQuery } from "@tanstack/react-query";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpAdminGateway;

export function useRoles() {
  return useQuery({
    queryKey: adminKeys.roles(),
    queryFn: () => gateway.listRoles(),
  });
}

export function useRoleAssignees(roleId: number) {
  return useQuery({
    queryKey: adminKeys.roleAssignees(roleId),
    queryFn: () => gateway.roleAssignees(roleId),
    enabled: roleId > 0,
  });
}

import { useQuery } from "@tanstack/react-query";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpAdminGateway;

interface ListUsersParams {
  q?: string;
  page?: number;
  size?: number;
}

export function useAdminUsers(params: ListUsersParams = {}) {
  return useQuery({
    queryKey: adminKeys.users(params),
    queryFn: () => gateway.listUsers(params),
  });
}

export function useAdminUser(userId: string) {
  return useQuery({
    queryKey: adminKeys.user(userId),
    queryFn: () => gateway.getUser(userId),
    enabled: !!userId,
  });
}

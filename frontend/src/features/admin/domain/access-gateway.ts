import type { AccessMe } from "@/features/admin/domain/access";

export interface AccessGateway {
  getMe(): Promise<AccessMe>;
}

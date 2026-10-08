import type { HealthSnapshot } from "@/features/health/domain/health";

export interface HealthGateway {
  getSnapshot(slug: string, window: string): Promise<HealthSnapshot>;
}

import type { ActivityTrace } from "@/features/health/domain/activity-trace";
import type { HealthSnapshot } from "@/features/health/domain/health";

export interface HealthGateway {
  getSnapshot(slug: string, window: string): Promise<HealthSnapshot>;
  activityTrace(slug: string): Promise<ActivityTrace>;
}

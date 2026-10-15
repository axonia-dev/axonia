import { activityTraceSchema } from "@/features/health/domain/activity-trace";
import { healthSnapshotSchema } from "@/features/health/domain/health";
import type { HealthGateway } from "@/features/health/domain/health-gateway";
import { apiFetch } from "@/lib/api-client";
import { buildQuery as query } from "@/lib/query";

export const httpHealthGateway: HealthGateway = {
  async getSnapshot(slug, window) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/health${query({ window })}`,
    );
    return healthSnapshotSchema.parse(data);
  },
  async activityTrace(slug) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/activity-trace`,
    );
    return activityTraceSchema.parse(data);
  },
};

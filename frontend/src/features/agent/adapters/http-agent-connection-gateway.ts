import { apiFetch, apiFetchVoid } from "@/lib/api-client";

import { agentConnectionSchema } from "../domain/agent-connection";
import type { AgentConnectionGateway } from "../domain/agent-connection-gateway";
import {
  authorizationOutcomeSchemaFor,
  type AuthorizationRequest,
} from "../domain/authorization-request";

const BASE = "/axonia/me/agent-connections";

async function post(
  path: string,
  request: AuthorizationRequest,
  body: unknown = request,
) {
  const data = await apiFetch<unknown>(`${BASE}${path}`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return authorizationOutcomeSchemaFor(request).parse(data);
}

export const httpAgentConnectionGateway: AgentConnectionGateway = {
  async listMine() {
    const data = await apiFetch<unknown>(BASE);
    return agentConnectionSchema.array().parse(data);
  },
  async disconnect(id: number) {
    await apiFetchVoid(`${BASE}/${id}`, { method: "DELETE" });
  },
  review: (request: AuthorizationRequest) =>
    post("/authorization-requests", request),
  approve: (
    request: AuthorizationRequest,
    scopes: string[],
    organizationId: string,
  ) =>
    post("/authorization-requests/approve", request, {
      request,
      scopes,
      organizationId,
    }),
  deny: (request: AuthorizationRequest) =>
    post("/authorization-requests/deny", request),
};

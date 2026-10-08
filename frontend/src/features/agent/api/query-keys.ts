import type { AuthorizationRequest } from "../domain/authorization-request";

export const agentKeys = {
  all: ["agent"] as const,
  tokens: () => [...agentKeys.all, "tokens"] as const,
  access: () => [...agentKeys.all, "access"] as const,
  connections: () => [...agentKeys.all, "connections"] as const,
  authorizationRequest: (request: AuthorizationRequest) =>
    [...agentKeys.all, "authorization-request", request] as const,
};

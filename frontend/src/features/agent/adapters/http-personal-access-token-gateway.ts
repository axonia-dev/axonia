import { apiFetch, apiFetchVoid } from "@/lib/api-client";

import { agentAccessSchema } from "../domain/agent-access";
import {
  type CreateTokenBody,
  createTokenBodySchema,
  createTokenResponseSchema,
  personalTokenSchema,
  type UpdateTokenBody,
  updateTokenBodySchema,
} from "../domain/personal-access-token";
import type { PersonalAccessTokenGateway } from "../domain/personal-access-token-gateway";

export const httpPersonalAccessTokenGateway: PersonalAccessTokenGateway = {
  async listMine() {
    const data = await apiFetch<unknown>("/axonia/me/tokens");
    return personalTokenSchema.array().parse(data);
  },
  async create(body: CreateTokenBody) {
    const validated = createTokenBodySchema.parse(body);
    const data = await apiFetch<unknown>("/axonia/me/tokens", {
      method: "POST",
      body: JSON.stringify(validated),
    });
    return createTokenResponseSchema.parse(data);
  },
  async revoke(id: number) {
    await apiFetchVoid(`/axonia/me/tokens/${id}`, { method: "DELETE" });
  },
  async update(id: number, body: UpdateTokenBody) {
    const validated = updateTokenBodySchema.parse(body);
    await apiFetchVoid(`/axonia/me/tokens/${id}`, {
      method: "PATCH",
      body: JSON.stringify(validated),
    });
  },
  async agentAccess() {
    const data = await apiFetch<unknown>("/axonia/me/agent-access");
    return agentAccessSchema.parse(data);
  },
};

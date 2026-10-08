import type { GenerateTokenBody } from "@/features/admin/domain/scim-token";
import {
  generateTokenBodySchema,
  generateTokenResponseSchema,
  scimTokenSchema,
} from "@/features/admin/domain/scim-token";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";

export const httpScimTokenGateway = {
  async generateToken(body: GenerateTokenBody) {
    const validated = generateTokenBodySchema.parse(body);
    const data = await apiFetch<unknown>("/axonia/scim-tokens", {
      method: "POST",
      body: JSON.stringify(validated),
    });
    return generateTokenResponseSchema.parse(data);
  },
  async revokeToken(tokenId: number) {
    await apiFetchVoid(`/axonia/scim-tokens/${tokenId}`, { method: "DELETE" });
  },
  async listTokens() {
    const data = await apiFetch<unknown>("/axonia/scim-tokens");
    return scimTokenSchema.array().parse(data);
  },
};

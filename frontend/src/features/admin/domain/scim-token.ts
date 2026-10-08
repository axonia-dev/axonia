import { z } from "zod";

import { futureExpirySchema } from "@/lib/future-expiry-schema";

export const scimTokenSchema = z.object({
  id: z.number(),
  label: z.string(),
  lastUsedAt: z.string().nullable(),
  expiresAt: z.string().nullable(),
  createdAt: z.string(),
  status: z.enum(["ACTIVE", "REVOKED", "EXPIRED"]),
});
export type ScimToken = z.infer<typeof scimTokenSchema>;
export type ScimTokenStatus = ScimToken["status"];

export const generateTokenResponseSchema = z.object({ token: z.string() });
export type GenerateTokenResponse = z.infer<typeof generateTokenResponseSchema>;

export const generateTokenBodySchema = z.object({
  label: z.string().min(1, "Label is required"),
  expiresAt: futureExpirySchema,
});
export type GenerateTokenBody = z.infer<typeof generateTokenBodySchema>;

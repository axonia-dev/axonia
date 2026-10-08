import { z } from "zod";

const agentConnectionStatusSchema = z.enum(["ACTIVE", "REVOKED", "ENDED"]);

export const agentConnectionSchema = z.object({
  id: z.number(),
  clientName: z.string(),
  clientHost: z.string(),
  scopes: z.array(z.string()),
  activatedAt: z.string(),
  lastUsedAt: z.string().nullish(),
  endsAt: z.string(),
  status: agentConnectionStatusSchema,
});
export type AgentConnection = z.infer<typeof agentConnectionSchema>;

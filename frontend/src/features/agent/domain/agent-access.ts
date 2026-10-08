import { z } from "zod";

/** Whether this organization may connect agents at all. A boolean and not a reason: the
 *  absence renders identically whether the cause is the organization's kind today or an
 *  unpaid plan later, and the rule itself lives in one place on the backend. */
export const agentAccessSchema = z.object({
  available: z.boolean(),
});

export type AgentAccess = z.infer<typeof agentAccessSchema>;

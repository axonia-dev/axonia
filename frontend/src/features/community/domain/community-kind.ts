import { z } from "zod";

export const communityKindSchema = z.object({
  id: z.number(),
  label: z.string(),
  communityCount: z.number(),
});

export type CommunityKind = z.infer<typeof communityKindSchema>;

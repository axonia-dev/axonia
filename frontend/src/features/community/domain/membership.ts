import { z } from "zod";

export const communityRoleSchema = z.enum(["ORGANIZER", "MEMBER"]);
export type CommunityRole = z.infer<typeof communityRoleSchema>;

export const membershipSchema = z.object({
  id: z.number(),
  communityId: z.number(),
  userId: z.string(),
  role: communityRoleSchema,
  joinedAt: z.string(),
  updatedAt: z.string(),
});

export type Membership = z.infer<typeof membershipSchema>;

/** Somebody in the organization a picker can credit: who, what they are called, and whether
 *  they are a member of the community. Crediting somebody makes them no member. */
export const creditablePersonSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  member: z.boolean(),
});

export type CreditablePerson = z.infer<typeof creditablePersonSchema>;

/** Members whose joinedAt is at or after the given ISO instant. */
export function joinedSince(members: Membership[], sinceIso: string): number {
  const boundary = Date.parse(sinceIso);
  return members.filter((member) => Date.parse(member.joinedAt) >= boundary)
    .length;
}

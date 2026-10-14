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

/** A member as a picker shows them: who, and what they are called. */
export const communityMemberSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
});

export type CommunityMember = z.infer<typeof communityMemberSchema>;

/** Members whose joinedAt is at or after the given ISO instant. */
export function joinedSince(members: Membership[], sinceIso: string): number {
  const boundary = Date.parse(sinceIso);
  return members.filter((member) => Date.parse(member.joinedAt) >= boundary)
    .length;
}

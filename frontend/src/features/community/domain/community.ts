import { z } from "zod";

import { communityRoleSchema } from "@/features/community/domain/membership";

const communityStatusSchema = z.enum(["ACTIVE", "ARCHIVED"]);

const focalPointSchema = z.object({ x: z.number(), y: z.number() });

export type FocalPoint = z.infer<typeof focalPointSchema>;

export const communitySchema = z.object({
  id: z.number(),
  slug: z.string(),
  name: z.string(),
  kindId: z.number(),
  kind: z.string(),
  description: z.string(),
  status: communityStatusSchema,
  memberCount: z.number(),
  badgeUrl: z.string().nullable().optional(),
  heroUrl: z.string().nullable().optional(),
  accentColor: z.string().nullable().optional(),
  heroFocus: focalPointSchema.nullable().optional(),
  badgeFocus: focalPointSchema.nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export type Community = z.infer<typeof communitySchema>;

export const communityListingSchema = communitySchema.extend({
  role: communityRoleSchema.nullable(),
});

export type CommunityListing = z.infer<typeof communityListingSchema>;

export function isArchived(community: Community): boolean {
  return community.status === "ARCHIVED";
}

export function canEdit(community: Community): boolean {
  return community.status === "ACTIVE";
}

/** What the banner did unconditionally before a focal point existed. */
export const CENTRE: FocalPoint = { x: 50, y: 50 };

/** In a hub the one community's name is the hub's public name, so a rename there is capped
 *  exactly as the hub's creation is. */
export const HUB_COMMUNITY_NAME_MAX_LENGTH = 80;

import { z } from "zod";

import { communityListingSchema } from "@/features/community/domain/community";

const lobbyShelfKeySchema = z.enum([
  "JOINED",
  "THIS_WEEK",
  "GROWING",
  "OTHERS",
  "EXPLORE",
]);

export type LobbyShelfKey = z.infer<typeof lobbyShelfKeySchema>;

const lobbySessionSchema = z.object({
  title: z.string().nullable(),
  scheduledAt: z.string(),
  hasTime: z.boolean(),
  zone: z.string(),
});

export const lobbyItemSchema = communityListingSchema.extend({
  nextSession: lobbySessionSchema.nullable(),
  nextSessionThisWeek: z.boolean(),
  latestSession: lobbySessionSchema.nullable(),
  joinedLast30Days: z.number(),
  faces: z.array(z.string()),
  stats: z.object({
    sessionsHeld: z.number(),
    artifactsShared: z.number(),
  }),
  lately: z.object({
    artifactTitle: z.string().nullable(),
    topicTitle: z.string().nullable(),
  }),
});

export type LobbyItem = z.infer<typeof lobbyItemSchema>;

const lobbyShelfSchema = z.object({
  key: lobbyShelfKeySchema,
  items: z.array(lobbyItemSchema),
  hasMore: z.boolean(),
});

export const lobbySchema = z.object({
  billboard: z.array(lobbyItemSchema),
  shelves: z.array(lobbyShelfSchema),
});

export type Lobby = z.infer<typeof lobbySchema>;

export const SHELF_PAGE_SIZE = 8;
export const GRID_PAGE_SIZE = 20;

const GRID_SHELVES: ReadonlySet<LobbyShelfKey> = new Set(["OTHERS", "EXPLORE"]);

/** The page size the server used for a shelf's first page, so later pages continue it. */
export function shelfPageSize(key: LobbyShelfKey): number {
  return GRID_SHELVES.has(key) ? GRID_PAGE_SIZE : SHELF_PAGE_SIZE;
}

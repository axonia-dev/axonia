import { z } from "zod";

import { sessionStatusSchema } from "@/features/events/domain/session";
import { artifactSchema } from "@/features/knowledge/domain/artifact";

export const saveStateSchema = z.object({
  viewerHasSaved: z.boolean(),
  viewerHelped: z.boolean().nullable(),
});
export type SaveState = z.infer<typeof saveStateSchema>;

type SaveableKind = "artifact" | "session";
export interface SaveableRef {
  kind: SaveableKind;
  id: number;
}

const savedCommunitySchema = z.object({
  id: z.number(),
  slug: z.string(),
  name: z.string(),
  accentColor: z.string().nullable(),
});

const savedSessionSummarySchema = z.object({
  id: z.number(),
  communityId: z.number(),
  title: z.string().nullable(),
  status: sessionStatusSchema,
  scheduledAt: z.string(),
  occurrenceDate: z.string().nullable(),
  facilitatorUserId: z.string().nullable(),
  slotCount: z.number(),
  recordingUrl: z.string().nullable(),
});

export const savedArtifactViewSchema = z.object({
  kind: z.literal("artifact"),
  artifact: artifactSchema,
  community: savedCommunitySchema.nullable(),
  savedAt: z.string(),
  helped: z.boolean().nullable(),
});
export type SavedArtifactView = z.infer<typeof savedArtifactViewSchema>;

const savedSessionViewSchema = z.object({
  kind: z.literal("session"),
  session: savedSessionSummarySchema,
  community: savedCommunitySchema.nullable(),
  savedAt: z.string(),
});
export type SavedSessionView = z.infer<typeof savedSessionViewSchema>;

export const savedItemSchema = z.discriminatedUnion("kind", [
  savedArtifactViewSchema,
  savedSessionViewSchema,
]);
export type SavedItem = z.infer<typeof savedItemSchema>;

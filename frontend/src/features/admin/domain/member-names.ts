import { z } from "zod";

export const MAX_NAME_PATTERNS = 10;

export const memberNamePatternsSchema = z.object({
  patterns: z.array(z.string()),
});

export const memberNamePreviewSchema = z.object({
  rows: z.array(
    z.object({
      received: z.string(),
      displayName: z.string(),
      givenName: z.string().nullable(),
      matched: z.number().int().nullable(),
    }),
  ),
});

export const memberNamesSavedSchema = z.object({
  changed: z.number().int(),
});

export type MemberNamePreviewRow = z.infer<
  typeof memberNamePreviewSchema
>["rows"][number];

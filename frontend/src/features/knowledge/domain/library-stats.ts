import { z } from "zod";

/**
 * Vitality figures for a community's library, computed server-side over the
 * artifact set. Drives the living header line.
 */
export const libraryStatsSchema = z.object({
  total: z.number(),
  sessionCount: z.number(),
  capturedThisMonth: z.number(),
});

export type LibraryStats = z.infer<typeof libraryStatsSchema>;

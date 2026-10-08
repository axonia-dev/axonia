import { z } from "zod";

/**
 * How many of a community's members read one clock.
 *
 * Counts only, never ids, and no candidate time: the product states the spread and grades nothing,
 * so there is no rule for the server to apply to an hour. That is what makes this answer the same
 * for every reader of a community, and what makes moving the hour in a form cost no request.
 *
 * Members with no clock are absent rather than grouped. The answer states the zones a community
 * spans, not its roster.
 */
const memberZoneSchema = z.object({
  zone: z.string(),
  count: z.number(),
});

export const memberZoneListSchema = z.array(memberZoneSchema);

export type MemberZone = z.infer<typeof memberZoneSchema>;

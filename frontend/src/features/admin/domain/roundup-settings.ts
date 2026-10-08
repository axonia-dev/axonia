import { z } from "zod";

/** An organization's one roundup control. There is no third state: off is the default,
 *  and an organization that has never touched the switch reads back `enabled: false`. */
export const roundupSettingsSchema = z.object({
  organizationId: z.string(),
  enabled: z.boolean(),
});
export type RoundupSettings = z.infer<typeof roundupSettingsSchema>;

/** What `GET /roundup/preview` answers with: the subject and HTML the most recently closed
 *  quarter's roundup would be sent as, rendered through the same path a send takes. */
export const roundupPreviewSchema = z.object({
  subject: z.string(),
  html: z.string(),
});
export type RoundupPreview = z.infer<typeof roundupPreviewSchema>;

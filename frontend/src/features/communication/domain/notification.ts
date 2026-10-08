import { z } from "zod";

export const notificationSchema = z.object({
  id: z.number(),
  sender: z.string().nullish(),
  headline: z.string(),
  line: z.string().nullish(),
  actionUrl: z.string().nullish(),
  sentAt: z.string(),
  read: z.boolean(),
});

export type Notification = z.infer<typeof notificationSchema>;

/** `nextBefore` and `nextBeforeId` are where the next request resumes from, and the server states them rather than
 *  leaving them to be derived from the last visible entry: a window whose every row was dropped as an unrecognised
 *  type has no visible last entry, and a client deriving a cursor from one would stop paging there and strand every
 *  older readable entry behind it. They are present exactly when `hasMore` is true. */
export interface NotificationPage {
  entries: Notification[];
  hasMore: boolean;
  nextBefore?: string | null;
  nextBeforeId?: number | null;
}

/** Where a page resumes from, which the next request pages back from. */
export interface NotificationCursor {
  before: string;
  beforeId: number;
}

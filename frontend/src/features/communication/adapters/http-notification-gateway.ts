import { z } from "zod";

import { apiFetch, apiFetchVoid } from "@/lib/api-client";

import type {
  NotificationCursor,
  NotificationPage,
} from "../domain/notification";
import { notificationSchema } from "../domain/notification";
import type { NotificationGateway } from "../domain/notification-gateway";

const PATH = "/axonia/me/notifications";
const PAGE_SIZE = 20;

const pageSchema = z.object({
  entries: z.array(z.unknown()),
  hasMore: z.boolean(),
  nextBefore: z.string().nullish(),
  nextBeforeId: z.number().nullish(),
});

const unreadCountSchema = z.object({ count: z.number() });

/** An entry this build cannot parse is dropped rather than throwing: one row written by a newer deploy must not be
 *  able to empty a member's whole panel, which is the worst direction this surface could fail in. */
function parsePage(data: unknown): NotificationPage {
  const page = pageSchema.parse(data);
  return {
    entries: page.entries.flatMap((row) => {
      const parsed = notificationSchema.safeParse(row);
      return parsed.success ? [parsed.data] : [];
    }),
    hasMore: page.hasMore,
    nextBefore: page.nextBefore ?? null,
    nextBeforeId: page.nextBeforeId ?? null,
  };
}

function pathFor(cursor: NotificationCursor | undefined): string {
  const params = new URLSearchParams({ size: String(PAGE_SIZE) });
  if (cursor !== undefined) {
    params.set("before", cursor.before);
    params.set("beforeId", String(cursor.beforeId));
  }
  return `${PATH}?${params.toString()}`;
}

export const httpNotificationGateway: NotificationGateway = {
  async page(cursor) {
    return parsePage(await apiFetch<unknown>(pathFor(cursor)));
  },
  async unreadCount() {
    return unreadCountSchema.parse(
      await apiFetch<unknown>(`${PATH}/unread-count`),
    ).count;
  },
  async markRead(id: number) {
    await apiFetchVoid(`${PATH}/${id}/read`, { method: "POST" });
  },
  async markAllRead() {
    await apiFetchVoid(`${PATH}/read-all`, { method: "POST" });
  },
};

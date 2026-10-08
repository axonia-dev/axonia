import { z } from "zod";

import { apiFetch, apiFetchVoid } from "@/lib/api-client";

import {
  type MessageType,
  messageTypeSchema,
  type NotificationPreference,
  notificationPreferenceSchema,
} from "../domain/notification-preference";
import type { NotificationPreferenceGateway } from "../domain/notification-preference-gateway";

const PATH = "/axonia/me/notification-preferences";

const rowTypeSchema = z.object({ type: z.unknown() });

/** Drops a row only when it HAS a `type` and that value isn't one this build recognises - a
 *  `MessageType` shipped after this build must not take down every toggle it does know
 *  about. Everything else throws rather than vanishing silently: `rowTypeSchema.parse` (not
 *  `safeParse`) rejects a row that isn't an object at all (`null`, a string, an array) and,
 *  under zod's "nonoptional" object semantics, a row whose `type` key is missing entirely -
 *  a renamed or dropped field is corruption, not a plausible future message type, and reading
 *  it as one would leave the member with no error shown and every switch painted back to
 *  "on" - the worst direction this page could fail in. A row that IS an object, with a
 *  `type` key present, but otherwise malformed (a recognised `type` with `enabled` sent as a
 *  string, say) also throws, via the final `.parse`. Only a `type` key that is present but
 *  holds a value outside the known enum reads as a genuinely new `MessageType` and is
 *  dropped - that is the one case the wire gives no way to tell apart from corruption, so it
 *  is treated as the more likely, more benign explanation. */
function parsePreferences(data: unknown): NotificationPreference[] {
  const rows = z.array(z.unknown()).parse(data);
  return rows.flatMap((rawRow) => {
    const row = rowTypeSchema.parse(rawRow);
    if (!messageTypeSchema.safeParse(row.type).success) return [];
    return [notificationPreferenceSchema.parse(rawRow)];
  });
}

export const httpNotificationPreferenceGateway: NotificationPreferenceGateway =
  {
    async getMine() {
      const data = await apiFetch<unknown>(PATH);
      return parsePreferences(data);
    },
    async setMine(type: MessageType, enabled: boolean) {
      await apiFetchVoid(PATH, {
        method: "PUT",
        body: JSON.stringify({ type, enabled }),
      });
    },
  };

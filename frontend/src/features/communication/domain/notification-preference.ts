import { z } from "zod";

export const messageTypeSchema = z.enum([
  "session_upcoming",
  "session_wrap_up",
  "session_awaiting_close",
  "organization_roundup",
]);
export type MessageType = z.infer<typeof messageTypeSchema>;

export const notificationPreferenceSchema = z.object({
  type: messageTypeSchema,
  enabled: z.boolean(),
});
export type NotificationPreference = z.infer<
  typeof notificationPreferenceSchema
>;

interface MessageTypeCopy {
  type: MessageType;
  label: string;
  description: string;
}

/**
 * Member-facing copy for each wire code, in the order the page renders them. A member never
 * sees `session_upcoming` or its siblings - only what the message is for and, for the
 * organizer-only nudge, who receives it. A plain array rather than a lookup keyed by
 * `MessageType`: the page renders every row from this list directly, with no dynamic index.
 */
export const MESSAGE_TYPES: ReadonlyArray<MessageTypeCopy> = [
  {
    type: "session_upcoming",
    label: "Before a session",
    description:
      "A reminder that a session is coming up, timed so it is not missed.",
  },
  {
    type: "session_wrap_up",
    label: "After a session",
    description:
      "What happened after a session: who presented, the recording and resources, and what's next.",
  },
  {
    type: "session_awaiting_close",
    label: "Confirm a session happened",
    description:
      "Organizers only. Asks for confirmation that a session took place, so its wrap-up can go out.",
  },
  {
    type: "organization_roundup",
    label: "Quarterly roundup",
    description:
      "Once a quarter, what the communities in the organization got done: sessions held, knowledge published, and what's picking up.",
  },
];

/** An absent row for a type means enabled - the backend's own default, mirrored here so a
 *  response missing a type (it never should, but the wire is not the type system) still
 *  renders that switch on rather than silently omitting it. */
export function preferenceFor(
  preferences: ReadonlyArray<NotificationPreference>,
  type: MessageType,
): boolean {
  return (
    preferences.find((preference) => preference.type === type)?.enabled ?? true
  );
}

/** Upserts a single type's preference, replacing any existing row for it in place - the row
 *  order is otherwise untouched, appending only for a type that was not present at all.
 *  Backs the optimistic cache write in `useSetNotificationPreference`: the switch a member
 *  just touched must show the new value immediately, not after the round trip to the
 *  server. */
export function withPreference(
  preferences: ReadonlyArray<NotificationPreference>,
  type: MessageType,
  enabled: boolean,
): NotificationPreference[] {
  const existingIndex = preferences.findIndex(
    (preference) => preference.type === type,
  );
  if (existingIndex === -1) {
    return [...preferences, { type, enabled }];
  }
  return preferences.map((preference, index) =>
    index === existingIndex ? { type, enabled } : preference,
  );
}

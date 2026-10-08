import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpNotificationPreferenceGateway } from "../adapters/http-notification-preference-gateway";
import type {
  MessageType,
  NotificationPreference,
} from "../domain/notification-preference";
import {
  preferenceFor,
  withPreference,
} from "../domain/notification-preference";
import { communicationKeys } from "./query-keys";

const gateway = httpNotificationPreferenceGateway;

export function useNotificationPreferences() {
  return useQuery({
    queryKey: communicationKeys.notificationPreferences(),
    queryFn: () => gateway.getMine(),
  });
}

/** Optimistic: the switch a member just clicked is driven straight off this cache (see
 *  `NotificationPreferencesPage`), so it must move the moment they click, not after a round
 *  trip to the server. A failed save rolls back to this type's own previous value.
 *
 *  Scoped per `type` (`notification-preference:${type}`) rather than to one shared scope for
 *  every switch: TanStack Query queues same-scope mutations behind one another, so a shared
 *  scope would let a hung PUT on one switch block the other two indefinitely. Per-type
 *  scoping still serializes repeated clicks on the SAME switch - last click wins on the
 *  server, not just in the cache - without coupling switches that have nothing to do with
 *  each other. `onMutate` fires synchronously regardless of the scope queue, so the switch
 *  still moves the instant it's clicked either way. */
export function useSetNotificationPreference(type: MessageType) {
  const queryClient = useQueryClient();
  const queryKey = communicationKeys.notificationPreferences();
  return useMutation({
    scope: { id: `notification-preference:${type}` },
    mutationFn: (enabled: boolean) => gateway.setMine(type, enabled),
    onMutate: async (enabled) => {
      await queryClient.cancelQueries({ queryKey });
      const current =
        queryClient.getQueryData<NotificationPreference[]>(queryKey) ?? [];
      const previousEnabled = preferenceFor(current, type);
      queryClient.setQueryData<NotificationPreference[]>(queryKey, (latest) =>
        withPreference(latest ?? [], type, enabled),
      );
      return { previousEnabled };
    },
    onError: (_error, _enabled, context) => {
      if (context === undefined) return;
      queryClient.setQueryData<NotificationPreference[]>(queryKey, (current) =>
        withPreference(current ?? [], type, context.previousEnabled),
      );
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey });
    },
  });
}

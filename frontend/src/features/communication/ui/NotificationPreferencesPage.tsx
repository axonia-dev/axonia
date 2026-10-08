import { LoadFailed } from "@/components/patterns/LoadFailed";
import { PageHeader } from "@/components/patterns/PageHeader";
import { Switch } from "@/components/ui/Switch";
import { notifyError } from "@/components/ui/toast";
import { failedToLoad, type RetryableRead } from "@/lib/failed-to-load";

import {
  useNotificationPreferences,
  useSetNotificationPreference,
} from "../api/use-notification-preferences";
import {
  MESSAGE_TYPES,
  type MessageType,
  type NotificationPreference,
  preferenceFor,
} from "../domain/notification-preference";

interface PreferenceRowProps {
  type: MessageType;
  label: string;
  description: string;
  isSelected: boolean;
}

/** Its own `useSetNotificationPreference` instance per row, not one shared by the whole
 *  list: that's what gives each switch its own mutation scope, so one switch's PUT can
 *  never queue behind - or get rolled back by - a different switch's. */
function PreferenceRow({
  type,
  label,
  description,
  isSelected,
}: Readonly<PreferenceRowProps>) {
  const setPreference = useSetNotificationPreference(type);

  function handleChange(enabled: boolean) {
    setPreference.mutate(enabled, {
      onError: () => notifyError("Could not save that change. Try again."),
    });
  }

  return (
    <div className="p-4">
      <Switch
        label={label}
        description={description}
        isSelected={isSelected}
        onChange={handleChange}
      />
    </div>
  );
}

interface PreferenceListProps {
  isLoading: boolean;
  failure: RetryableRead | undefined;
  data: ReadonlyArray<NotificationPreference>;
}

function PreferenceList({
  isLoading,
  failure,
  data,
}: Readonly<PreferenceListProps>) {
  if (failure !== undefined) {
    return (
      <LoadFailed
        inline
        message="Could not load notification preferences."
        query={failure}
      />
    );
  }
  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  return (
    <div className="border-border bg-surface divide-border flex flex-col divide-y rounded-md border">
      {MESSAGE_TYPES.map((messageType) => (
        <PreferenceRow
          key={messageType.type}
          type={messageType.type}
          label={messageType.label}
          description={messageType.description}
          isSelected={preferenceFor(data, messageType.type)}
        />
      ))}
    </div>
  );
}

/**
 * Turning a message type off is the other half of the on-by-default stance: the
 * default is only defensible because this page (and the unsubscribe link in every mail) make
 * opting out genuinely easy.
 *
 * The switches govern mail and nothing else, and that is carried by the `Email` section heading
 * rather than by a sentence explaining it. It takes the settings eyebrow every other section
 * heading on this surface uses: at the switch labels' own size it read as a fifth row rather
 * than as the name of the group above them, and rank here comes from the treatment, not the size.
 *
 * Each switch is driven straight off query data, and `useSetNotificationPreference` writes
 * that cache optimistically - so a click moves the switch immediately, and a failed save
 * snaps it back rather than leaving it lying about what the server actually holds.
 */
export function NotificationPreferencesPage() {
  const preferences = useNotificationPreferences();
  const { data, isLoading } = preferences;

  return (
    <section className="flex flex-col gap-6">
      <PageHeader title="Notifications" />
      <section className="flex flex-col gap-3">
        <h2 className="text-muted-foreground text-xs font-semibold tracking-[0.14em] uppercase opacity-70">
          Email
        </h2>
        <PreferenceList
          isLoading={isLoading}
          failure={failedToLoad(preferences) ? preferences : undefined}
          data={data ?? []}
        />
      </section>
    </section>
  );
}

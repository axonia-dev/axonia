import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Switch } from "@/components/ui/Switch";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useUpdateCommunicationsEnabled } from "@/features/admin/api/use-update-communications-enabled";
import { DisableCommunicationsDialog } from "@/features/admin/ui/DisableCommunicationsDialog";
import { communityErrorMessage } from "@/features/community/community-errors";

/**
 * The organization-wide kill switch (the widest governance layer):
 * `communication:write`-gated, it stops all outbound member messaging for the whole
 * tenant at once, and no community-level setting can override it. The description
 * names every one of `MessageType`'s four constants, and must keep doing so: the
 * gate checks this switch before it resolves a row's message type at all, so this
 * is the control an administrator reaches for expecting it to silence everything,
 * and a type missing from the list is the one they find out about from a recipient.
 * A fifth message type means a fifth item here. Turning it off asks for confirmation
 * first, through {@link DisableCommunicationsDialog}; turning it back on does not,
 * since restoring email is not the destructive direction.
 */
export function CommunicationsKillSwitch() {
  const { can } = usePermissions();
  const { data: organization } = useCurrentOrganization();
  const update = useUpdateCommunicationsEnabled();
  const [confirming, setConfirming] = useState(false);

  if (!can("communication:write") || organization === undefined) {
    return null;
  }

  const enabled = organization.communicationsEnabled ?? true;
  const requestDisable = () => setConfirming(true);
  const enable = () => update.mutate(true);

  return (
    <div className="border-border bg-surface flex flex-col gap-2 rounded-md border p-4">
      <Switch
        label="Email and chat notifications"
        description="Session reminders, wrap-ups, the nudges asking organizers to confirm a session happened, and the quarterly roundup. Off stops all of it going outward, by mail and to community chat rooms alike, for everyone in this organization, at once. The in-app notification panel is unaffected: members still see what was sent to them inside the product."
        isSelected={enabled}
        isDisabled={update.isPending}
        onChange={enabled ? requestDisable : enable}
      />
      {update.isError && (
        <FormError
          error={update.error}
          message={communityErrorMessage(update.error)}
        />
      )}
      <DisableCommunicationsDialog
        isOpen={confirming}
        onOpenChange={setConfirming}
        onConfirm={() => update.mutate(false)}
      />
    </div>
  );
}

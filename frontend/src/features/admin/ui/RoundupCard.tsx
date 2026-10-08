import { Eye } from "lucide-react";
import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { MessagePreviewDialog } from "@/components/patterns/MessagePreviewDialog";
import { Button } from "@/components/ui/Button";
import { Switch, SWITCH_CONTROL_INDENT } from "@/components/ui/Switch";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useRoundupPreview,
  useRoundupSettings,
} from "@/features/admin/api/use-roundup-settings";
import { useUpdateRoundupEnabled } from "@/features/admin/api/use-update-roundup-enabled";
import { failedToLoad } from "@/lib/failed-to-load";

/**
 * The quarterly roundup's one administrator control, `communication:write`-gated and shown
 * only for an enterprise: a Community Hub has no provisioned directory a roundup could be
 * addressed to, so the card is absent there rather than present and refused.
 *
 * Off by default, and previewable before it is ever switched on - the decision to mail an
 * entire organization should be made against the message rather than against a description
 * of it. Neither direction asks for confirmation, unlike the kill switch: switching a
 * quarterly message off is not destructive, and between quarters there is no mail in flight
 * to stop.
 *
 * A failed READ says so and offers a retry, rather than returning null the way a hub or an
 * unloaded query does. Silently disappearing would make a 500 or a dropped network
 * indistinguishable from "not applicable to this tenant", in the one control an
 * administrator would then assume they had never been given. The failure stays through the
 * retry, its "Try again" pending, rather than the card vanishing until the answer.
 */
export function RoundupCard() {
  const { can } = usePermissions();
  const { data: organization } = useCurrentOrganization();
  const update = useUpdateRoundupEnabled();
  const [previewing, setPreviewing] = useState(false);

  const applies =
    can("communication:write") && organization?.kind === "ENTERPRISE";
  const settingsQuery = useRoundupSettings(applies);
  const settings = settingsQuery.data;
  const preview = useRoundupPreview(previewing);

  if (!applies) {
    return null;
  }

  if (failedToLoad(settingsQuery)) {
    return (
      <div className="border-border bg-surface flex flex-col gap-2 rounded-md border p-4">
        <p className="text-foreground text-sm font-medium">Quarterly roundup</p>
        <LoadFailed
          inline
          message="Could not read whether the roundup is on."
          query={settingsQuery}
        />
      </div>
    );
  }

  if (settings === undefined) {
    return null;
  }

  return (
    <div className="border-border bg-surface flex flex-col gap-2 rounded-md border p-4">
      <Switch
        label="Quarterly roundup"
        description="Once a quarter, what the communities got done: sessions held, knowledge published, people who showed up. It goes to everyone in the organization, not only community members."
        isSelected={settings.enabled}
        isDisabled={update.isPending}
        onChange={(enabled) => update.mutate(enabled)}
      />
      {update.isError && <FormError error={update.error} />}
      <div className={SWITCH_CONTROL_INDENT}>
        <Button
          variant="secondary"
          size="sm"
          onPress={() => setPreviewing(true)}
        >
          <Eye size={14} aria-hidden />
          Preview
        </Button>
      </div>
      <MessagePreviewDialog
        isOpen={previewing}
        onOpenChange={setPreviewing}
        dialogTitle="Quarterly roundup preview"
        iframeTitle="Roundup preview"
        preview={preview}
        errorMessage="Could not render the roundup. Try again."
      />
    </div>
  );
}

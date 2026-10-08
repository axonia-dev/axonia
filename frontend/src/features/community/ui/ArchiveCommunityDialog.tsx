import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { TextInput } from "@/components/ui/TextInput";
import { useArchiveCommunity } from "@/features/community/api/use-communities";
import { communityErrorMessage } from "@/features/community/community-errors";

interface Props {
  slug: string;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}

/**
 * A destructive confirmation: archiving hides the community from members, so we
 * gate it behind retyping the slug — deliberate friction, GitHub-style. Lives in
 * the community feature because the confirmation phrase is the slug itself.
 */
export function ArchiveCommunityDialog({
  slug,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="Archive this community?"
      role="alertdialog"
    >
      {({ close }) => (
        <ArchiveForm slug={slug} onArchived={close} onCancel={close} />
      )}
    </Dialog>
  );
}

function ArchiveForm({
  slug,
  onArchived,
  onCancel,
}: Readonly<{ slug: string; onArchived: () => void; onCancel: () => void }>) {
  const [confirmation, setConfirmation] = useState("");
  const archive = useArchiveCommunity(slug);
  const matches = confirmation === slug;

  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        Archiving hides{" "}
        <span className="text-foreground font-medium">{slug}</span> from members
        and stops new activity. This can be reversed by an organizer.
      </p>
      <TextInput
        label={`Type "${slug}" to confirm`}
        value={confirmation}
        onChange={setConfirmation}
        placeholder={slug}
      />
      {archive.error != null && (
        <p role="alert" className="text-danger text-sm">
          {communityErrorMessage(archive.error)}
        </p>
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={onCancel}>
          Cancel
        </Button>
        <Button
          variant="danger"
          isDisabled={!matches || archive.isPending}
          onPress={() => archive.mutate(undefined, { onSuccess: onArchived })}
        >
          Archive
        </Button>
      </DialogFooter>
    </div>
  );
}

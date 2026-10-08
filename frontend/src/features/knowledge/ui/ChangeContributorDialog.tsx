import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import {
  MemberSelectField,
  type PickedMember,
} from "@/components/patterns/MemberSelectField";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useUsers } from "@/features/identity/api/use-users";
import { useChangeContributor } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { creditErrorMessage } from "@/features/knowledge/knowledge-errors";

interface Props {
  artifact: Artifact;
  slug: string;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

interface FormProps {
  artifact: Artifact;
  slug: string;
  onDone: () => void;
}

function ChangeContributorForm({
  artifact,
  slug,
  onDone,
}: Readonly<FormProps>) {
  const [picked, setPicked] = useState<PickedMember | null>(null);
  const change = useChangeContributor(slug);
  const users = useUsers([artifact.contributorUserId]).data;
  const current =
    users?.[artifact.contributorUserId]?.displayName ??
    "the current contributor";
  const unchanged =
    picked === null || picked.userId === artifact.contributorUserId;

  function submit() {
    if (picked === null) {
      return;
    }
    change.mutate(
      { id: artifact.id, contributor: picked.userId },
      { onSuccess: onDone },
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">Credited to {current}.</p>
      <MemberSelectField
        slug={slug}
        label="Contributor"
        placeholder="Search members"
        picked={picked}
        onPick={setPicked}
      />
      <FormError
        error={change.isError}
        message={creditErrorMessage(change.error)}
      />
      <DialogFooter>
        <Button type="button" variant="ghost" onPress={onDone}>
          Cancel
        </Button>
        <Button
          type="button"
          isDisabled={unchanged || change.isPending}
          onPress={submit}
        >
          Change
        </Button>
      </DialogFooter>
    </div>
  );
}

/**
 * Corrects whose work an artifact is. Its own act, never a field of Edit, so revising an
 * artifact cannot move its credit by accident.
 */
export function ChangeContributorDialog({
  artifact,
  slug,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="Change contributor"
    >
      {({ close }) => (
        <ChangeContributorForm artifact={artifact} slug={slug} onDone={close} />
      )}
    </Dialog>
  );
}

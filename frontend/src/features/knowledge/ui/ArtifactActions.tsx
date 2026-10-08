import { Pencil, Trash2, UserRoundPen } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useRemoveArtifact } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { ArtifactDialog } from "@/features/knowledge/ui/ArtifactDialog";
import { ChangeContributorDialog } from "@/features/knowledge/ui/ChangeContributorDialog";

interface Props {
  artifact: Artifact;
  slug: string;
  /** The contributor, or an organizer: may revise or delete the artifact. */
  canEdit: boolean;
  /** Holds the community's Credit contributions axis: may change whose work it is. */
  canCredit: boolean;
}

const iconButtonClass = "hover:border-primary hover:text-primary";

export function ArtifactActions({
  artifact,
  slug,
  canEdit,
  canCredit,
}: Readonly<Props>) {
  const [editOpen, setEditOpen] = useState(false);
  const [creditOpen, setCreditOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const remove = useRemoveArtifact(slug);

  return (
    <>
      <span className="ml-auto flex items-center gap-1.5 opacity-0 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100 motion-reduce:transition-none [@media(hover:none)]:opacity-100">
        {canEdit && (
          <Button
            variant="outline"
            size="sm"
            aria-label="Edit artifact"
            className={iconButtonClass}
            onPress={() => setEditOpen(true)}
          >
            <Pencil size={15} aria-hidden />
          </Button>
        )}
        {canCredit && (
          <Button
            variant="outline"
            size="sm"
            aria-label="Change contributor"
            className={iconButtonClass}
            onPress={() => setCreditOpen(true)}
          >
            <UserRoundPen size={15} aria-hidden />
          </Button>
        )}
        {canEdit && (
          <Button
            variant="outline"
            size="sm"
            aria-label="Delete artifact"
            className="hover:border-danger hover:text-danger"
            isDisabled={remove.isPending}
            onPress={() => setConfirmOpen(true)}
          >
            <Trash2 size={15} aria-hidden />
          </Button>
        )}
      </span>
      {canEdit && (
        <ArtifactDialog
          artifact={artifact}
          slug={slug}
          isOpen={editOpen}
          onOpenChange={setEditOpen}
        />
      )}
      {canCredit && (
        <ChangeContributorDialog
          artifact={artifact}
          slug={slug}
          isOpen={creditOpen}
          onOpenChange={setCreditOpen}
        />
      )}
      {canEdit && (
        <Dialog
          role="alertdialog"
          title="Delete artifact"
          isOpen={confirmOpen}
          onOpenChange={setConfirmOpen}
        >
          {({ close }) => (
            <div className="flex flex-col gap-4">
              <p className="text-muted-foreground text-sm">
                Deleting this artifact cannot be undone.
              </p>
              <DialogFooter>
                <Button type="button" variant="ghost" onPress={close}>
                  Cancel
                </Button>
                <Button
                  type="button"
                  variant="danger"
                  isDisabled={remove.isPending}
                  onPress={() => {
                    remove.mutate(artifact.id, { onSuccess: close });
                  }}
                >
                  Delete
                </Button>
              </DialogFooter>
            </div>
          )}
        </Dialog>
      )}
    </>
  );
}

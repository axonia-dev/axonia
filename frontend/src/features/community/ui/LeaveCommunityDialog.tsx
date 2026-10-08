import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useLeaveCommunity } from "@/features/community/api/use-leave-community";
import { communityErrorMessage } from "@/features/community/community-errors";

interface Props {
  slug: string;
  name: string;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}

/**
 * The one case where leaving needs explaining: in a self-serve hub the community
 * IS the workspace, so walking out of it walks out of the tenant and signs you
 * out. An organization member leaves with no dialog at all.
 */
export function LeaveCommunityDialog({
  slug,
  name,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="Leave this community?"
      role="alertdialog"
    >
      {({ close }) => (
        <LeaveForm slug={slug} name={name} onLeft={close} onCancel={close} />
      )}
    </Dialog>
  );
}

function LeaveForm({
  slug,
  name,
  onLeft,
  onCancel,
}: Readonly<{
  slug: string;
  name: string;
  onLeft: () => void;
  onCancel: () => void;
}>) {
  const leave = useLeaveCommunity(slug);

  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        Leaving <span className="text-foreground font-medium">{name}</span> also
        ends this membership of the workspace and signs the member out. A fresh
        invite is needed to come back.
      </p>
      {leave.error != null && (
        <p role="alert" className="text-danger text-sm">
          {communityErrorMessage(leave.error)}
        </p>
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={onCancel}>
          Cancel
        </Button>
        <Button
          variant="danger"
          isDisabled={leave.isPending}
          onPress={() => leave.leave(onLeft)}
        >
          Leave
        </Button>
      </DialogFooter>
    </div>
  );
}

import { ShieldCheck } from "lucide-react";
import { useId, useState } from "react";

import { Button } from "@/components/ui/Button";
import { useConfirmGroup } from "@/features/admin/api/use-confirm-group";
import type { Role, RoleBinding } from "@/features/admin/domain/admin-user";
import { groupReleaseReach } from "@/features/admin/domain/ceiling";
import { REACHES_BEYOND } from "@/features/admin/ui/ceiling-copy";
import { useCeiling } from "@/features/admin/ui/use-ceiling";
import { ApiError } from "@/lib/api-client";

interface HeldGroupNoticeProps {
  bindings: RoleBinding[];
  roles: Role[];
  canWrite: boolean;
}

interface ConfirmActionProps {
  canWrite: boolean;
  outOfReach: boolean;
  reasonApplies: boolean;
  isPending: boolean;
  reasonId: string;
  onConfirm: () => void;
}

/** The confirm control, present but refused when out of reach. Absent (not merely disabled)
 *  for a reader who cannot write at all, same as `BindingRow`'s Unbind. The reason renders only
 *  when `reasonApplies`, so an unresolved role never gets reported as a ceiling refusal. */
function ConfirmAction({
  canWrite,
  outOfReach,
  reasonApplies,
  isPending,
  reasonId,
  onConfirm,
}: Readonly<ConfirmActionProps>) {
  if (!canWrite) return null;
  return (
    <>
      <Button
        size="sm"
        isDisabled={isPending || outOfReach}
        {...(reasonApplies ? { "aria-describedby": reasonId } : {})}
        onPress={onConfirm}
      >
        <ShieldCheck size={16} aria-hidden />
        Confirm group
      </Button>
      {reasonApplies && (
        <p id={reasonId} className="text-muted-foreground text-sm">
          {REACHES_BEYOND}
        </p>
      )}
    </>
  );
}

/**
 * The release valve for a held group. Without it the hold is invisible: the bindings render
 * exactly as usual and grant nothing.
 *
 * Five states reach this notice, and every sentence has to be true of all of them: a SCIM create
 * onto a key a binding was already waiting on, a SCIM rename onto one, a provider switch rekeying
 * onto one, any of those whose waiting binding was later unbound, and any of those that a later
 * provider switch moved onto an unclaimed key - which stays held on purpose. The last two both
 * render with no bindings, and they differ in why, so the copy must not claim either cause. The
 * lead therefore states the rule rather than this group's history: what is true of every held
 * group is that the hold happened, not how it came about.
 *
 * Confirmation is per group key, not per binding: the server stamps the directory group and
 * guards the caller's ceiling over every role bound to that key, so any of the group's bindings
 * addresses the same confirmation and which one is sent carries no authority of its own. With no
 * binding there is no address, so the notice asks for one instead of offering a dead button.
 */
export function HeldGroupNotice({
  bindings,
  roles,
  canWrite,
}: Readonly<HeldGroupNoticeProps>) {
  const confirmGroup = useConfirmGroup();
  const ceiling = useCeiling();
  const [error, setError] = useState<string | null>(null);
  const address = bindings[0];
  const reach = groupReleaseReach(bindings, roles, ceiling);
  const reasonId = useId();

  function handleConfirm() {
    if (address === undefined) return;
    setError(null);
    confirmGroup.mutate(address.id, {
      onError: (err) => {
        setError(
          err instanceof ApiError ? err.message : "Failed to confirm group.",
        );
      },
    });
  }

  return (
    <section className="border-warning/40 bg-warning/10 flex flex-col gap-3 rounded-md border px-3 py-3">
      <p className="text-foreground text-sm">
        This group is held, so it confers nothing until an administrator
        confirms it is the group they meant. A group is held when the directory
        writes it onto a reconciliation key a role binding is already waiting
        on.{" "}
        {address === undefined
          ? "No role is bound to its current key, so nothing is waiting on this confirmation right now."
          : "The bindings below take effect only once it is confirmed."}
      </p>
      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
      {address === undefined ? (
        <p className="text-muted-foreground text-sm">
          Bind a role to this group to confirm it.
        </p>
      ) : (
        <ConfirmAction
          canWrite={canWrite}
          outOfReach={reach.outOfReach}
          reasonApplies={reach.outOfReach && reach.roleResolved}
          isPending={confirmGroup.isPending}
          reasonId={reasonId}
          onConfirm={handleConfirm}
        />
      )}
    </section>
  );
}

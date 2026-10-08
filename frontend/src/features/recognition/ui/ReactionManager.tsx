import { useEffect, useState } from "react";

import { ConfirmDeleteDialog } from "@/components/patterns/ConfirmDeleteDialog";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useUsers } from "@/features/identity/api/use-users";
import {
  DELETED_MEMBER,
  type MemberStatus,
  type User,
} from "@/features/identity/domain/user";
import {
  useAllReactionDefinitions,
  useApproveReaction,
  useReactionEligibility,
  useRemoveReaction,
} from "@/features/recognition/api/use-reactions";
import type { ReactionDefinition } from "@/features/recognition/domain/reaction";
import { recognitionErrorMessage } from "@/features/recognition/recognition-errors";
import {
  ApprovedRow,
  headroom,
  PendingRow,
  removalMessage,
} from "@/features/recognition/ui/reaction-rows";
import { failedToLoad } from "@/lib/failed-to-load";

function proposerIds(pending: ReadonlyArray<ReactionDefinition>): string[] {
  const named = pending.flatMap((definition) =>
    definition.proposedByUserId === null ? [] : [definition.proposedByUserId],
  );
  return [...new Set(named)];
}

/** The id is a Keycloak subject, which is nobody's name. It falls back to itself, under a
 *  PRESENT status, only when the directory has not answered yet, so the row is never blank.
 *  An erased proposer keeps the neutral label MemberLabel already renders muted and italic. */
function resolvedProposer(
  id: string | null,
  people: Record<string, User> | undefined,
): { name: string; status: MemberStatus } {
  if (id === null) {
    return { name: DELETED_MEMBER, status: "ERASED" };
  }
  const user = people?.[id];
  return {
    name: user?.displayName ?? id,
    status: user?.memberStatus ?? "PRESENT",
  };
}

/** The queue's two acts fail the same way and share one line. */
function queueErrorMessage(errors: ReadonlyArray<unknown>): string | null {
  const failure = errors.find((error) => error !== null && error !== undefined);
  return failure === undefined ? null : recognitionErrorMessage(failure);
}

/** An unread catalogue says so, rather than reading as no reaction approved. */
export function ReactionManager() {
  const definitions = useAllReactionDefinitions();
  if (failedToLoad(definitions)) {
    return (
      <LoadFailed
        inline
        message="Could not load the reactions."
        query={definitions}
      />
    );
  }
  return <ReactionCatalogue />;
}

function ReactionCatalogue() {
  const definitions = useAllReactionDefinitions();
  const eligibility = useReactionEligibility();
  const approve = useApproveReaction();
  // Declining and removing are the same call, on separate mutation instances: one failed decline
  // must not open the next removal dialog already carrying an error about another reaction.
  const decline = useRemoveReaction();
  const remove = useRemoveReaction();
  const { can } = usePermissions();
  const canWrite = can("reaction:write");

  const [removalTarget, setRemovalTarget] = useState<ReactionDefinition | null>(
    null,
  );

  const all = definitions.data ?? [];
  const pending = all.filter((definition) => definition.pending);
  const approvedList = all.filter((definition) => !definition.pending);
  const proposers = useUsers(proposerIds(pending));
  const queueError = queueErrorMessage([approve.error, decline.error]);

  // Neither an approval nor a decline has a dialog of its own to dismiss its error, so both
  // clear the moment the queue is re-rendered from fresh data - the same symmetry the removal
  // dialog already gets from resetting on open and on cancel.
  useEffect(() => {
    approve.reset();
    decline.reset();
    // oxlint-disable-next-line react/exhaustive-deps
  }, [definitions.data]);

  function openRemoval(definition: ReactionDefinition) {
    remove.reset();
    setRemovalTarget(definition);
  }

  function cancelRemoval() {
    remove.reset();
    setRemovalTarget(null);
  }

  function confirmRemoval() {
    if (removalTarget === null) return;
    remove.mutate(removalTarget.id, {
      onSuccess: () => setRemovalTarget(null),
    });
  }

  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-3">
        <h3 className="text-sm font-semibold">Waiting for approval</h3>
        {queueError !== null && (
          <p role="alert" className="text-danger text-sm">
            {queueError}
          </p>
        )}
        {pending.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            No proposal is waiting.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {pending.map((definition) => (
              <PendingRow
                key={definition.id}
                definition={definition}
                proposedBy={resolvedProposer(
                  definition.proposedByUserId,
                  proposers.data,
                )}
                canWrite={canWrite}
                onApprove={() => approve.mutate(definition.id)}
                onDecline={() => decline.mutate(definition.id)}
              />
            ))}
          </ul>
        )}
      </section>

      <section className="flex flex-col gap-3">
        <h3 className="text-sm font-semibold">
          In use{" "}
          <span className="text-muted-foreground font-normal">
            {headroom(approvedList.length, eligibility.data?.approvedLimit)}
          </span>
        </h3>
        {approvedList.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            No reaction is approved yet.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {approvedList.map((definition) => (
              <ApprovedRow
                key={definition.id}
                definition={definition}
                canWrite={canWrite}
                onRemove={() => openRemoval(definition)}
              />
            ))}
          </ul>
        )}
      </section>

      <ConfirmDeleteDialog
        isOpen={removalTarget !== null}
        title={`Remove ${removalTarget?.name ?? ""}?`}
        message={removalTarget === null ? "" : removalMessage(removalTarget)}
        confirmLabel="Remove"
        reassignLabel=""
        options={[]}
        needsReassign={false}
        reassignTo={null}
        onReassign={() => {
          // No reassignment: a reaction is a thing somebody said, and there is no
          // other word to move a placement to.
        }}
        error={remove.isError ? recognitionErrorMessage(remove.error) : null}
        isDeleting={remove.isPending}
        onCancel={cancelRemoval}
        onConfirm={confirmRemoval}
      />
    </div>
  );
}

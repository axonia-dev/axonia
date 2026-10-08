import { useState } from "react";

export interface ReassignDeleteArgs {
  id: number;
  reassignToId?: number;
}

interface Params {
  /** The persisted record's id, or null for an unsaved draft (delete is a no-op). */
  recordId: number | null;
  deleteRecord: (args: ReassignDeleteArgs) => Promise<void>;
  onDeleted: () => void;
  /** True when the failure means the record is still in use and reassignment is required. */
  isInUse: (error: unknown) => boolean;
  /** The message to surface for a failed delete, given the error and whether it was in use. */
  toErrorMessage: (error: unknown, inUse: boolean) => string;
  /** Seed the reassignment requirement up front (e.g. when the usage count is already > 0). */
  initialNeedsReassign?: boolean;
}

export interface ReassignableDelete {
  confirming: boolean;
  startConfirm: () => void;
  needsReassign: boolean;
  reassignTo: number | null;
  setReassignTo: (id: number) => void;
  deleteError: string | null;
  cancel: () => void;
  confirm: () => void;
}

/**
 * The shared "delete with reassignment" flow behind ConfirmDeleteDialog: a record that
 * still has dependents can't be deleted until they're moved to another record. Used by the
 * tenant-type editors across bounded contexts (section types, library types, community kinds).
 */
export function useReassignableDelete({
  recordId,
  deleteRecord,
  onDeleted,
  isInUse,
  toErrorMessage,
  initialNeedsReassign = false,
}: Params): ReassignableDelete {
  const [confirming, setConfirming] = useState(false);
  const [needsReassign, setNeedsReassign] = useState(initialNeedsReassign);
  const [reassignTo, setReassignTo] = useState<number | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function cancel() {
    setConfirming(false);
    setNeedsReassign(initialNeedsReassign);
    setReassignTo(null);
    setDeleteError(null);
  }

  async function run() {
    if (recordId === null) {
      return;
    }
    const args: ReassignDeleteArgs =
      reassignTo === null
        ? { id: recordId }
        : { id: recordId, reassignToId: reassignTo };
    try {
      await deleteRecord(args);
      onDeleted();
    } catch (error) {
      const inUse = isInUse(error);
      if (inUse) {
        setNeedsReassign(true);
      }
      setDeleteError(toErrorMessage(error, inUse));
    }
  }

  return {
    confirming,
    startConfirm: () => setConfirming(true),
    needsReassign,
    reassignTo,
    setReassignTo,
    deleteError,
    cancel,
    confirm: () => void run(),
  };
}

import { useState } from "react";

import type { MdSelection } from "@/components/patterns/MasterDetailManager";

interface Params<TInput> {
  /** Create a record, reporting the new id on success or the failure on error. */
  createRecord: (
    input: TInput,
    onCreated: (id: number) => void,
    onError: (error: unknown) => void,
  ) => void;
  /** Update an existing record, reporting any failure. */
  updateRecord: (
    id: number,
    input: TInput,
    onError: (error: unknown) => void,
  ) => void;
}

export interface TypeManager<TInput> {
  selection: MdSelection;
  /** The selected record's id, or null when nothing / a draft is selected. */
  selectedId: number | null;
  /** The last failed create/update, or null. */
  saveError: unknown;
  select: (next: MdSelection) => void;
  /** Create (currentId null) or update the record, clearing any prior save error first. */
  save: (currentId: number | null, input: TInput) => void;
}

/**
 * The shared master/detail editing state for a tenant-type manager: tracks the current
 * selection and the last save error, and routes save to create-or-update. The CRUD calls
 * are injected so each bounded context keeps its own gateway and payload shape.
 */
export function useTypeManager<TInput>({
  createRecord,
  updateRecord,
}: Params<TInput>): TypeManager<TInput> {
  const [selection, setSelection] = useState<MdSelection>(null);
  const [saveError, setSaveError] = useState<unknown>(null);
  const selectedId = selection?.kind === "item" ? selection.id : null;

  function select(next: MdSelection) {
    setSaveError(null);
    setSelection(next);
  }

  function save(currentId: number | null, input: TInput) {
    setSaveError(null);
    if (currentId === null) {
      createRecord(
        input,
        (id) => setSelection({ kind: "item", id }),
        (error) => setSaveError(error),
      );
      return;
    }
    updateRecord(currentId, input, (error) => setSaveError(error));
  }

  return { selection, selectedId, saveError, select, save };
}

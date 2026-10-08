import { Users } from "lucide-react";
import { useState } from "react";

import { TypeEditorChrome } from "@/components/patterns/TypeEditorChrome";
import { UsageStat } from "@/components/patterns/UsageStat";
import {
  type ReassignDeleteArgs,
  useReassignableDelete,
} from "@/components/patterns/useReassignableDelete";
import { TextInput } from "@/components/ui/TextInput";
import { communityErrorMessage } from "@/features/community/community-errors";
import type { CommunityKind } from "@/features/community/domain/community-kind";
import { ApiError } from "@/lib/api-client";

interface KindEditorProps {
  /** The id of the persisted kind, or null for an unsaved draft. */
  kindId: number | null;
  initialLabel: string;
  /** How many communities currently use this kind (0 for a draft). */
  communityCount: number;
  /** The other kinds — candidate reassignment targets when deleting. */
  others: ReadonlyArray<CommunityKind>;
  onSave: (label: string) => void;
  isSaving: boolean;
  saveError: unknown;
  deleteKind: (args: ReassignDeleteArgs) => Promise<void>;
  isDeleting: boolean;
  onDeleted: () => void;
}

const IN_USE_CODE = "KIND_IN_USE";

function isInUse(error: unknown): boolean {
  return error instanceof ApiError && error.code === IN_USE_CODE;
}

/** The detail pane: rename a kind, or delete it (reassigning its communities). */
export function KindEditor({
  kindId,
  initialLabel,
  communityCount,
  others,
  onSave,
  isSaving,
  saveError,
  deleteKind,
  isDeleting,
  onDeleted,
}: Readonly<KindEditorProps>) {
  const [label, setLabel] = useState(initialLabel);
  const isDraft = kindId === null;

  const del = useReassignableDelete({
    recordId: kindId,
    deleteRecord: deleteKind,
    onDeleted,
    isInUse,
    toErrorMessage: (error) => communityErrorMessage(error),
    // Reassignment is required up front whenever the kind already classifies communities.
    initialNeedsReassign: communityCount > 0,
  });

  function revert() {
    setLabel(initialLabel);
  }

  return (
    <TypeEditorChrome
      onSave={() => onSave(label.trim())}
      onRevert={revert}
      isSaving={isSaving}
      canSave={!isSaving && label.trim() !== ""}
      saveError={
        saveError === null || saveError === undefined
          ? null
          : communityErrorMessage(saveError)
      }
      canDelete={!isDraft}
      isDeleting={isDeleting}
      del={del}
      deleteTitle="Delete this kind?"
      reassignLabel="Move communities to…"
      reassignOptions={others.map((o) => ({ id: o.id, label: o.label }))}
    >
      {!isDraft && (
        <UsageStat
          count={communityCount}
          singular="community"
          plural="communities"
          icon={<Users size={14} />}
        />
      )}

      <TextInput label="Label" value={label} onChange={setLabel} />
    </TypeEditorChrome>
  );
}

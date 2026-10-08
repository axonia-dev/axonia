import { Library } from "lucide-react";
import { useState } from "react";

import { TypeEditorChrome } from "@/components/patterns/TypeEditorChrome";
import { UsageStat } from "@/components/patterns/UsageStat";
import {
  type ReassignDeleteArgs,
  useReassignableDelete,
} from "@/components/patterns/useReassignableDelete";
import { AccentControl } from "@/components/ui/AccentControl";
import { IconPicker } from "@/components/ui/IconPicker";
import { TextInput } from "@/components/ui/TextInput";
import type { ArtifactType } from "@/features/knowledge/domain/artifact-type";
import type { ArtifactTypeInput } from "@/features/knowledge/domain/knowledge-gateway";
import { ApiError } from "@/lib/api-client";

interface LibraryTypeEditorProps {
  /** The id of the persisted type, or null for an unsaved draft. */
  typeId: number | null;
  initialLabel: string;
  initialIcon: string | null;
  initialAccent: string | null;
  /** How many artifacts currently use this type (0 for a draft). */
  artifactCount: number;
  /** The other types — candidate reassignment targets when deleting. */
  others: ReadonlyArray<ArtifactType>;
  onSave: (input: ArtifactTypeInput) => void;
  isSaving: boolean;
  /** A failed create/update mutation, surfaced in the save bar. */
  saveError: unknown;
  deleteType: (args: ReassignDeleteArgs) => Promise<void>;
  isDeleting: boolean;
  onDeleted: () => void;
}

const IN_USE_CODE = "ARTIFACT_TYPE_IN_USE";
const EXISTS_CODE = "ARTIFACT_TYPE_EXISTS";

function errorCode(error: unknown): string | undefined {
  if (error instanceof ApiError) {
    return error.code;
  }
  if (error !== null && typeof error === "object" && "code" in error) {
    const { code } = error as { code?: unknown };
    return typeof code === "string" ? code : undefined;
  }
  return undefined;
}

function saveErrorMessage(error: unknown): string {
  if (errorCode(error) === EXISTS_CODE) {
    return "A type with that label already exists.";
  }
  return error instanceof Error ? error.message : "Could not save this type.";
}

/** The detail pane: edit a type's label/icon/accent, or delete (with reassignment). */
export function LibraryTypeEditor({
  typeId,
  initialLabel,
  initialIcon,
  initialAccent,
  artifactCount,
  others,
  onSave,
  isSaving,
  saveError,
  deleteType,
  isDeleting,
  onDeleted,
}: Readonly<LibraryTypeEditorProps>) {
  const [label, setLabel] = useState(initialLabel);
  const [icon, setIcon] = useState<string | null>(initialIcon);
  const [accent, setAccent] = useState<string | null>(initialAccent);

  const del = useReassignableDelete({
    recordId: typeId,
    deleteRecord: deleteType,
    onDeleted,
    isInUse: (error) => errorCode(error) === IN_USE_CODE,
    toErrorMessage: (error, inUse) => {
      if (inUse) {
        return "This type still classifies artifacts. Choose where to move them.";
      }
      return error instanceof Error
        ? error.message
        : "Could not delete this type.";
    },
  });

  function revert() {
    setLabel(initialLabel);
    setIcon(initialIcon);
    setAccent(initialAccent);
  }

  function save() {
    onSave({ label: label.trim(), icon, accentColor: accent });
  }

  return (
    <TypeEditorChrome
      onSave={save}
      onRevert={revert}
      isSaving={isSaving}
      canSave={!isSaving && label.trim() !== ""}
      saveError={
        saveError === null || saveError === undefined
          ? null
          : saveErrorMessage(saveError)
      }
      canDelete={typeId !== null}
      isDeleting={isDeleting}
      del={del}
      deleteTitle="Delete this type?"
      reassignLabel="Move artifacts to…"
      reassignOptions={others.map((o) => ({ id: o.id, label: o.label }))}
    >
      {typeId !== null && (
        <UsageStat
          count={artifactCount}
          singular="artifact"
          plural="artifacts"
          icon={<Library size={14} />}
        />
      )}

      <TextInput label="Label" value={label} onChange={setLabel} />

      <div className="flex flex-col gap-2">
        <span className="text-foreground text-sm font-medium">Icon</span>
        <IconPicker value={icon} onChange={setIcon} />
      </div>

      <div className="flex flex-col gap-2">
        <span className="text-foreground text-sm font-medium">Accent</span>
        <AccentControl value={accent} onChange={setAccent} />
      </div>
    </TypeEditorChrome>
  );
}

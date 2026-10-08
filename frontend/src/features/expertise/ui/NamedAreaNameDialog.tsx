import { type SyntheticEvent, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { TextInput } from "@/components/ui/TextInput";
import { MAX_NAMED_AREA_NAME_LENGTH } from "@/features/expertise/domain/named-area";

export interface NameDraft {
  title: string;
  confirmLabel: string;
  name: string;
  /** Tags the named area starts with, shown so the leader sees what the name is for. */
  tags: string[];
}

interface Props {
  /** `null` closes the dialog. */
  draft: NameDraft | null;
  onCancel: () => void;
  onConfirm: (name: string) => void;
}

const TAG_LIST = new Intl.ListFormat("en-GB", { type: "conjunction" });

function NameForm({
  draft,
  onCancel,
  onConfirm,
}: Readonly<Props & { draft: NameDraft }>) {
  const [value, setValue] = useState(draft.name);
  const formRef = useRef<HTMLFormElement>(null);
  const name = value.trim();
  const tooLong = name.length > MAX_NAMED_AREA_NAME_LENGTH;
  const canSave = name !== "" && !tooLong && name !== draft.name;

  // The name is the only thing to fill in, so the dialog opens on it rather than on its own frame.
  useEffect(() => {
    formRef.current?.querySelector("input")?.focus();
  }, []);

  function submit(event: SyntheticEvent) {
    event.preventDefault();
    onConfirm(name);
  }

  return (
    <form ref={formRef} onSubmit={submit} className="flex flex-col gap-4">
      <TextInput
        label="Name"
        value={value}
        onChange={setValue}
        {...(tooLong
          ? {
              errorMessage: `A name can be at most ${MAX_NAMED_AREA_NAME_LENGTH} characters.`,
            }
          : {})}
      />
      {draft.tags.length > 0 && (
        <p className="text-muted-foreground text-sm">{`Holds ${TAG_LIST.format(draft.tags)}.`}</p>
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={onCancel}>
          Cancel
        </Button>
        <Button type="submit" variant="secondary" isDisabled={!canSave}>
          {draft.confirmLabel}
        </Button>
      </DialogFooter>
    </form>
  );
}

/** Names a new named area, or renames one. */
export function NamedAreaNameDialog({
  draft,
  onCancel,
  onConfirm,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={draft !== null}
      onOpenChange={(open) => {
        if (!open) onCancel();
      }}
      title={draft?.title ?? ""}
    >
      {draft !== null && (
        <NameForm draft={draft} onCancel={onCancel} onConfirm={onConfirm} />
      )}
    </Dialog>
  );
}

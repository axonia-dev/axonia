import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { TextInput } from "@/components/ui/TextInput";
import { MAX_TAG_LENGTH, normalizeTag } from "@/lib/normalize-tag";

interface Props {
  /** The tag being renamed. `null` closes the dialog. */
  tag: string | null;
  /** The community's canonical tags, so a rename onto one of them can be called a fold. */
  existingTags: string[];
  onCancel: () => void;
  onConfirm: (to: string) => void;
}

const REACH =
  "This rewrites the tag on every artifact, topic and talk slot in the community, and in members’ declarations and areas to learn. It cannot be undone.";

function canRename(normalized: string, current: string): boolean {
  if (normalized === "") return false;
  if (normalized.length > MAX_TAG_LENGTH) return false;
  return normalized !== current;
}

interface PreviewProps {
  current: string;
  normalized: string;
  folds: boolean;
}

/**
 * What the typed name will really become. The fold warning is the point of the dialog: renaming
 * onto a name the community already uses does not merge the two reversibly, it collapses them.
 */
function RenamePreview({ current, normalized, folds }: Readonly<PreviewProps>) {
  if (folds) {
    return (
      <p role="alert" className="text-danger text-sm">
        {`Renaming folds ${current} into ${normalized}, permanently. This cannot be undone.`}
      </p>
    );
  }
  if (normalized === "" || normalized === current) {
    return <p className="text-muted-foreground text-sm">{REACH}</p>;
  }
  return (
    <p className="text-muted-foreground text-sm">
      Will be stored as{" "}
      <code className="text-foreground font-mono">{normalized}</code>. {REACH}
    </p>
  );
}

/** Renames a tag across the community's content, after showing what the name will become. */
export function RenameTagDialog({
  tag,
  existingTags,
  onCancel,
  onConfirm,
}: Readonly<Props>) {
  const current = tag ?? "";
  const [value, setValue] = useState(current);
  // Each tag the curator opens the dialog on starts from its own name, not the last one's.
  const [opened, setOpened] = useState(tag);
  if (tag !== opened) {
    setOpened(tag);
    setValue(current);
  }

  const normalized = normalizeTag(value);
  const tooLong = normalized.length > MAX_TAG_LENGTH;
  const folds = normalized !== current && existingTags.includes(normalized);

  return (
    <Dialog
      isOpen={tag !== null}
      onOpenChange={(open) => {
        if (!open) onCancel();
      }}
      title={`Rename ${current}`}
      role="alertdialog"
    >
      <div className="flex flex-col gap-4">
        <TextInput
          label="New name"
          value={value}
          onChange={setValue}
          {...(tooLong
            ? {
                errorMessage: `A tag can be at most ${MAX_TAG_LENGTH} characters.`,
              }
            : {})}
        />
        <RenamePreview
          current={current}
          normalized={normalized}
          folds={folds}
        />
        <DialogFooter>
          <Button variant="ghost" onPress={onCancel}>
            Cancel
          </Button>
          <Button
            onPress={() => onConfirm(normalized)}
            isDisabled={!canRename(normalized, current)}
          >
            Rename
          </Button>
        </DialogFooter>
      </div>
    </Dialog>
  );
}

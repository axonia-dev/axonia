import { Upload } from "lucide-react";
import { useState } from "react";
import { Button as AriaButton, DropZone } from "react-aria-components";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import {
  firstDroppedImage,
  imageDropOperation,
  PickImageFile,
} from "@/components/ui/image-drop";
import {
  useClearAvatar,
  useSetAvatar,
} from "@/features/identity/api/use-avatar";
import { serverMessage } from "@/lib/server-message";
import { useObjectUrl } from "@/lib/use-object-url";

const MAX_AVATAR_BYTES = 2 * 1024 * 1024;
const SIZE_HINT = "PNG, JPEG or WebP, up to 2 MB";
const BROWSE_HINT = "Drag an image here, or browse";
const SIZE_ERROR = "Picture is over the 2 MB limit.";
const SAVE_ERROR = "Could not save the picture. Try again.";
const REMOVE_ERROR = "Could not remove the picture. Try again.";

interface AvatarUploadDialogProps {
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
  hasOverride: boolean;
  /** The picture in force right now, shown as the field's starting state. Null only when
   *  there is no picture at all, which is the one case the empty panel belongs in. */
  currentUrl: string | null;
}

export function AvatarUploadDialog({
  isOpen,
  onOpenChange,
  hasOverride,
  currentUrl,
}: Readonly<AvatarUploadDialogProps>) {
  return (
    <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title="Profile picture">
      {({ close }) => (
        <AvatarUploadForm
          hasOverride={hasOverride}
          currentUrl={currentUrl}
          close={close}
        />
      )}
    </Dialog>
  );
}

/** Hover and drop-target are the same gesture at two ranges, so they light the panel the same
 *  way, the drop target more strongly: whichever of the two wins the cascade, the panel reads
 *  as live under the pointer. */
const panelClass =
  "border-border-strong bg-surface-sunken data-[hovered]:border-primary/60 data-[hovered]:bg-primary/5 data-[drop-target]:border-primary data-[drop-target]:bg-primary/15 rounded-xl border-2 border-dashed transition-colors duration-150";

const pressableClass =
  "data-[focus-visible]:ring-primary flex w-full cursor-pointer items-center gap-4 rounded-xl px-5 py-5 text-left outline-none data-[disabled]:cursor-default data-[disabled]:opacity-60 data-[focus-visible]:ring-2 data-[focus-visible]:ring-inset";

interface AvatarPickerProps {
  /** Object URL of a picture picked in this dialog, before it is saved. */
  previewUrl: string | null;
  /** Name of that picked file, which labels the panel in place of the drag copy. */
  fileName: string | null;
  /** The picture already in force, shown until a new one is picked. */
  currentUrl: string | null;
  isDisabled: boolean;
  onSelect: (file: File) => void;
}

/**
 * The picture field: a dashed panel that is itself the file picker. A bare `input type=file`
 * renders as unstyled grey text, which reads as a caption rather than as something to press -
 * so the whole panel is pressable, and takes a dragged image as well.
 *
 * The empty panel is reserved for having no picture at all: with one already in force the panel
 * opens on it, so the field states what is set today rather than reading as though nothing is.
 */
function AvatarPicker({
  previewUrl,
  fileName,
  currentUrl,
  isDisabled,
  onSelect,
}: Readonly<AvatarPickerProps>) {
  const shownUrl = previewUrl ?? currentUrl;

  return (
    <DropZone
      aria-label="Profile picture drop zone"
      isDisabled={isDisabled}
      getDropOperation={imageDropOperation}
      onDrop={(event) => {
        const dropped = firstDroppedImage(event.items);
        if (dropped) {
          void dropped.getFile().then(onSelect);
        }
      }}
      className={panelClass}
    >
      <PickImageFile onSelect={onSelect}>
        <AriaButton isDisabled={isDisabled} className={pressableClass}>
          {shownUrl === null ? (
            <span className="flex w-full flex-col items-center gap-2 py-1 text-center">
              <Upload aria-hidden size={22} className="text-primary" />
              <span className="text-foreground text-sm font-medium">
                {BROWSE_HINT}
              </span>
              <span className="text-muted-foreground text-xs">{SIZE_HINT}</span>
            </span>
          ) : (
            <>
              <img
                src={shownUrl}
                alt={previewUrl === null ? "Current picture" : "Preview"}
                className="border-border size-16 shrink-0 rounded-full border object-cover"
              />
              <span className="flex min-w-0 flex-col gap-1">
                <span className="text-foreground truncate text-sm font-medium">
                  {fileName ?? BROWSE_HINT}
                </span>
                <span className="text-muted-foreground text-xs">
                  {SIZE_HINT}
                </span>
              </span>
            </>
          )}
        </AriaButton>
      </PickImageFile>
    </DropZone>
  );
}

interface AvatarUploadFormProps {
  hasOverride: boolean;
  currentUrl: string | null;
  close: () => void;
}

function AvatarUploadForm({
  hasOverride,
  currentUrl,
  close,
}: Readonly<AvatarUploadFormProps>) {
  const setAvatar = useSetAvatar();
  const clearAvatar = useClearAvatar();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const previewUrl = useObjectUrl(file);

  function handleSelect(chosen: File) {
    if (chosen.size > MAX_AVATAR_BYTES) {
      setFile(null);
      setError(SIZE_ERROR);
      return;
    }
    setError(null);
    setFile(chosen);
  }

  function handleSave() {
    if (file === null) return;
    setError(null);
    setAvatar.mutate(file, {
      onSuccess: close,
      onError: (mutationError) =>
        setError(serverMessage(mutationError) ?? SAVE_ERROR),
    });
  }

  function handleRemove() {
    setError(null);
    clearAvatar.mutate(undefined, {
      onSuccess: close,
      onError: (mutationError) =>
        setError(serverMessage(mutationError) ?? REMOVE_ERROR),
    });
  }

  const isBusy = setAvatar.isPending || clearAvatar.isPending;

  return (
    <div className="flex flex-col gap-4">
      <AvatarPicker
        previewUrl={previewUrl}
        fileName={file?.name ?? null}
        currentUrl={currentUrl}
        isDisabled={isBusy}
        onSelect={handleSelect}
      />

      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}

      <DialogFooter>
        <Button variant="ghost" onPress={close}>
          Cancel
        </Button>
        {hasOverride && (
          <Button variant="danger" onPress={handleRemove} isDisabled={isBusy}>
            Remove
          </Button>
        )}
        <Button onPress={handleSave} isDisabled={file === null || isBusy}>
          Save
        </Button>
      </DialogFooter>
    </div>
  );
}

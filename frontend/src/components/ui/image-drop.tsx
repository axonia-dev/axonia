import type { ReactNode } from "react";
import type {
  DragTypes,
  DropItem,
  DropOperation,
  FileDropItem,
} from "react-aria-components";
import { FileTrigger } from "react-aria-components";

export const ACCEPTED_IMAGE_TYPES = ["image/png", "image/jpeg", "image/webp"];

/** Only a drag carrying an accepted image is offered a drop: anything else keeps the
 *  "no drop" cursor, rather than accepting the release and then doing nothing. */
export function imageDropOperation(types: DragTypes): DropOperation {
  return ACCEPTED_IMAGE_TYPES.some((type) => types.has(type))
    ? "copy"
    : "cancel";
}

/** First dropped item that is an image file of an accepted type, if any. */
export function firstDroppedImage(items: DropItem[]) {
  return items.find(
    (item): item is FileDropItem =>
      item.kind === "file" && ACCEPTED_IMAGE_TYPES.includes(item.type),
  );
}

/** Turns any pressable child into a picker for a single image file: the child keeps the
 *  affordance, this owns the hidden input and the accepted types. */
export function PickImageFile({
  onSelect,
  children,
}: Readonly<{ onSelect: (file: File) => void; children: ReactNode }>) {
  return (
    <FileTrigger
      acceptedFileTypes={ACCEPTED_IMAGE_TYPES}
      onSelect={(files) => {
        const file = files?.[0];
        if (file) {
          onSelect(file);
        }
      }}
    >
      {children}
    </FileTrigger>
  );
}

import { Image, Plus, Upload, X } from "lucide-react";
import type { ReactNode } from "react";
import { Button, DropZone } from "react-aria-components";

import {
  firstDroppedImage,
  imageDropOperation,
  PickImageFile,
} from "@/components/ui/image-drop";
import { cn } from "@/lib/cn";

export { AccentControl } from "@/components/ui/AccentControl";

function AddPill({
  label,
  busy,
  icon,
  onAdd,
}: Readonly<{
  label: string;
  busy: boolean;
  icon: ReactNode;
  onAdd: (file: File) => void;
}>) {
  return (
    <PickImageFile onSelect={onAdd}>
      <Button
        isDisabled={busy}
        className="bg-surface/90 text-secondary border-border-strong inline-flex cursor-pointer items-center gap-2 rounded-full border px-3.5 py-2 text-sm font-semibold shadow-sm backdrop-blur transition-transform duration-150 hover:scale-105 hover:shadow-md active:scale-95 disabled:cursor-default disabled:opacity-60"
      >
        {icon}
        Add {label}
      </Button>
    </PickImageFile>
  );
}

interface EditZoneProps {
  hasImage: boolean;
  busy: boolean;
  onAdd: (file: File) => void;
  onRemove: () => void;
  reposition?: ReactNode;
}

/**
 * Wraps an image affordance in a drop zone: dragging an accepted image file
 * over `children` reveals a dashed highlight, and dropping it calls `onAdd`.
 * Click-to-upload and keyboard access live in `children`.
 */
function ImageDropZone({
  label,
  busy,
  onAdd,
  className,
  highlightClassName,
  highlight,
  reposition,
  children,
}: Readonly<{
  label: string;
  busy: boolean;
  onAdd: (file: File) => void;
  className: string;
  highlightClassName: string;
  highlight: ReactNode;
  reposition?: ReactNode;
  children: ReactNode;
}>) {
  return (
    <DropZone
      aria-label={label}
      isDisabled={busy}
      getDropOperation={imageDropOperation}
      onDrop={(event) => {
        const file = firstDroppedImage(event.items);
        if (file) {
          void file.getFile().then(onAdd);
        }
      }}
      className={cn("absolute inset-0 outline-none", className)}
    >
      {({ isDropTarget }) => (
        <>
          {reposition}
          <div
            aria-hidden
            className={cn(
              "pointer-events-none absolute inset-0 z-20 flex items-center justify-center border-2 border-dashed opacity-0 transition-opacity duration-150",
              highlightClassName,
              isDropTarget && "opacity-100",
            )}
          >
            {highlight}
          </div>
          {/* While dragging, show only the drop highlight - hide the idle
              affordances. The wrapper is a static, zero-height box, so the
              absolutely-positioned children keep their place while opacity
              fades the whole subtree. */}
          <div
            className={cn(
              "transition-opacity duration-150",
              isDropTarget && "opacity-0",
            )}
          >
            {children}
          </div>
        </>
      )}
    </DropZone>
  );
}

/**
 * Banner affordance: a centered "Add banner image" when empty, or a quiet
 * top-right "Remove banner" that fades in on hover/focus when one is set.
 * The whole area is a drop zone - dragging an image file over it highlights
 * the banner and dropping sets it.
 */
export function BannerEditZone({
  hasImage,
  busy,
  onAdd,
  onRemove,
  reposition,
}: Readonly<EditZoneProps>) {
  return (
    <ImageDropZone
      label="Banner image drop zone"
      busy={busy}
      onAdd={onAdd}
      className="data-[focus-visible]:ring-primary rounded-md data-[focus-visible]:ring-2 data-[focus-visible]:ring-inset"
      highlightClassName="border-primary bg-primary/15 rounded-md"
      reposition={reposition}
      highlight={
        <span className="bg-surface/95 text-primary inline-flex items-center gap-2 rounded-full px-3.5 py-2 text-sm font-semibold shadow-sm backdrop-blur">
          <Upload size={15} />
          Drop image to set banner
        </span>
      }
    >
      {!hasImage && (
        <div className="absolute inset-0 flex items-center justify-center">
          <AddPill
            label="banner image"
            busy={busy}
            icon={<Image size={15} />}
            onAdd={onAdd}
          />
        </div>
      )}
      {hasImage && (
        <div className="absolute top-3 right-3 opacity-0 transition-opacity group-focus-within/banner:opacity-100 group-hover/banner:opacity-100">
          <Button
            isDisabled={busy}
            onPress={onRemove}
            className="bg-surface/90 text-danger border-danger/40 inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-semibold shadow-sm backdrop-blur transition-transform duration-150 hover:scale-105 hover:shadow-md active:scale-95 disabled:cursor-default disabled:opacity-60"
          >
            <X size={13} />
            Remove banner
          </Button>
        </div>
      )}
    </ImageDropZone>
  );
}

/**
 * Logo affordance occupying the badge slot: a dashed square with a plus when
 * empty, or a small circular remove handle on the badge corner when set.
 * Like the banner, the slot is a drop zone - dragging an image over it sets
 * (or replaces) the logo.
 */
export function BadgeEditZone({
  hasImage,
  busy,
  onAdd,
  onRemove,
  reposition,
}: Readonly<EditZoneProps>) {
  return (
    <ImageDropZone
      label="Logo image drop zone"
      busy={busy}
      onAdd={onAdd}
      className="data-[focus-visible]:ring-secondary rounded-2xl data-[focus-visible]:ring-2 data-[focus-visible]:ring-inset"
      highlightClassName="border-secondary bg-secondary/20 rounded-2xl"
      reposition={reposition}
      highlight={<Upload size={18} className="text-secondary" />}
    >
      {!hasImage && (
        <PickImageFile onSelect={onAdd}>
          <Button
            aria-label="Add logo"
            isDisabled={busy}
            className="border-secondary/40 bg-surface-sunken text-secondary hover:border-secondary hover:bg-secondary/5 absolute inset-0 flex cursor-pointer items-center justify-center rounded-2xl border-4 border-dashed transition-all duration-150 hover:scale-105 active:scale-95 disabled:cursor-default disabled:opacity-60"
          >
            <Plus size={22} />
          </Button>
        </PickImageFile>
      )}
      {hasImage && (
        <Button
          aria-label="Remove logo"
          isDisabled={busy}
          onPress={onRemove}
          className="bg-surface text-danger border-danger/40 absolute -top-2 -right-2 z-30 flex size-6 cursor-pointer items-center justify-center rounded-full border opacity-0 shadow-md transition-all duration-150 group-focus-within/badge:opacity-100 group-hover/badge:opacity-100 hover:scale-110 active:scale-95 disabled:cursor-default"
        >
          <X size={12} />
        </Button>
      )}
    </ImageDropZone>
  );
}

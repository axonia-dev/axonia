import type { ReactNode } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

export type MdSelection =
  { kind: "item"; id: number } | { kind: "draft" } | null;

interface MasterDetailManagerProps<T> {
  items: ReadonlyArray<T>;
  getId: (item: T) => number;
  /** Singular noun, used in the "+ New {noun}" button and placeholder copy. */
  noun: string;
  canWrite: boolean;
  selection: MdSelection;
  onSelect: (selection: MdSelection) => void;
  /** Inner content of a row button; the wrapper and selection styling live here. */
  renderRowContent: (item: T, selected: boolean) => ReactNode;
  /** The detail pane for the current item (or a draft when `current` is null). */
  renderDetail: (current: T | null, isDraft: boolean) => ReactNode;
  /** Heading for the detail — the desktop pane title and the mobile sheet title. */
  detailTitle: (current: T | null, isDraft: boolean) => string;
}

function rowClass(selected: boolean): string {
  return cn(
    "flex w-full cursor-pointer items-center gap-3 rounded-md border px-3 py-2 text-left text-sm transition-colors",
    selected
      ? "border-primary bg-surface-sunken text-primary"
      : "border-border hover:border-border-strong hover:bg-surface-sunken",
  );
}

function placeholder(canWrite: boolean, noun: string): ReactNode {
  return (
    <p className="text-muted-foreground text-sm">
      {canWrite
        ? `Select a ${noun} to edit it, or create a new one.`
        : `Select a ${noun} to view it.`}
    </p>
  );
}

interface RowListProps<T> {
  items: ReadonlyArray<T>;
  getId: (item: T) => number;
  noun: string;
  canWrite: boolean;
  selectedId: number | null;
  onSelect: (selection: MdSelection) => void;
  renderRowContent: (item: T, selected: boolean) => ReactNode;
}

function RowList<T>({
  items,
  getId,
  noun,
  canWrite,
  selectedId,
  onSelect,
  renderRowContent,
}: Readonly<RowListProps<T>>) {
  return (
    <div className="border-border flex flex-col gap-3 border-b p-4 md:border-r md:border-b-0">
      <ul className="flex flex-col gap-2">
        {items.map((item) => {
          const id = getId(item);
          const selected = id === selectedId;
          return (
            <li key={id}>
              <button
                type="button"
                onClick={() => onSelect({ kind: "item", id })}
                aria-pressed={selected}
                className={rowClass(selected)}
              >
                {renderRowContent(item, selected)}
              </button>
            </li>
          );
        })}
      </ul>
      {canWrite && (
        <Button
          variant="ghost"
          size="sm"
          onPress={() => onSelect({ kind: "draft" })}
        >
          + New {noun}
        </Button>
      )}
    </div>
  );
}

interface DetailRegionProps {
  isDesktop: boolean;
  showHeading: boolean;
  isOpen: boolean;
  title: string;
  onClose: () => void;
  /** Resolved detail/placeholder for the inline desktop pane. */
  desktopBody: ReactNode;
  /** The detail for the mobile sheet (only shown while `isOpen`). */
  sheetBody: ReactNode;
}

function DetailRegion({
  isDesktop,
  showHeading,
  isOpen,
  title,
  onClose,
  desktopBody,
  sheetBody,
}: Readonly<DetailRegionProps>) {
  if (isDesktop) {
    return (
      <div className="flex flex-col gap-4 p-5">
        {showHeading && (
          <h3 className="font-display text-foreground text-lg font-semibold">
            {title}
          </h3>
        )}
        {desktopBody}
      </div>
    );
  }
  return (
    <Dialog
      isOpen={isOpen}
      // Escape or a dismiss request clears the selection and closes the sheet.
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={title}
    >
      {sheetBody}
    </Dialog>
  );
}

/**
 * A generic master-detail manager: a selectable list on the left and a detail
 * pane on the right (desktop) or a bottom-sheet dialog (mobile). The owner keeps
 * the data, mutations, and `selection` state; this shell owns the layout, the
 * row styling, and the responsive desktop/mobile fork.
 */
export function MasterDetailManager<T>({
  items,
  getId,
  noun,
  canWrite,
  selection,
  onSelect,
  renderRowContent,
  renderDetail,
  detailTitle,
}: Readonly<MasterDetailManagerProps<T>>) {
  const isDesktop = useMediaQuery("(min-width: 48rem)");

  const selectedId = selection?.kind === "item" ? selection.id : null;
  const current = items.find((item) => getId(item) === selectedId) ?? null;
  const isDraft = selection?.kind === "draft";
  const editing = canWrite && selection !== null;
  const detail = editing
    ? renderDetail(current, isDraft)
    : placeholder(canWrite, noun);

  return (
    <div className="border-border bg-surface grid grid-cols-1 gap-0 rounded-xl border shadow-sm md:grid-cols-[16rem_minmax(0,1fr)]">
      <RowList
        items={items}
        getId={getId}
        noun={noun}
        canWrite={canWrite}
        selectedId={selectedId}
        onSelect={onSelect}
        renderRowContent={renderRowContent}
      />
      <DetailRegion
        isDesktop={isDesktop}
        showHeading={editing}
        isOpen={editing}
        title={detailTitle(current, isDraft)}
        onClose={() => onSelect(null)}
        desktopBody={detail}
        sheetBody={renderDetail(current, isDraft)}
      />
    </div>
  );
}

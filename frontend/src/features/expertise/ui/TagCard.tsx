import { EllipsisVertical } from "lucide-react";

import { MenuButton, MenuItem, MenuNote } from "@/components/ui/Menu";
import { cn } from "@/lib/cn";

/** Direction marker shown while this card is part of a batch-merge selection:
 *  the survivor "keeps name", every other selected tag folds "into" it. */
export type MergeHint = { kind: "keep" } | { kind: "into"; target: string };

interface Props {
  canonicalTag: string;
  variants: string[];
  onSplit: (variant: string) => void;
  selected?: boolean;
  onToggleSelect?: (tag: string) => void;
  hint?: MergeHint | undefined;
  /** An alias that just merged in here; it lands with a one-shot highlight. */
  landedVariant?: string | undefined;
  /** Rewrites the tag across the community's content. Absent on a card with no actions menu. */
  onRename?: ((tag: string) => void) | undefined;
  /** Strips the tag from the community's content. Absent on a card with no actions menu. */
  onDelete?: ((tag: string) => void) | undefined;
}

interface MenuProps {
  canonicalTag: string;
  /** A merged tag wears other names; rename and delete have no right answer for them. */
  isMerged: boolean;
  onRename: (tag: string) => void;
  onDelete: (tag: string) => void;
}

/** The two verbs that end a tag's life. Both are barred on a merged tag, as the backend bars them. */
function TagActionsMenu({
  canonicalTag,
  isMerged,
  onRename,
  onDelete,
}: Readonly<MenuProps>) {
  return (
    <MenuButton
      label={`More actions for ${canonicalTag}`}
      trigger={<EllipsisVertical aria-hidden className="size-4" />}
      onAction={(key) => {
        if (key === "rename") onRename(canonicalTag);
        if (key === "delete") onDelete(canonicalTag);
      }}
      className="text-muted-foreground hover:text-foreground flex size-[18px] items-center justify-center"
    >
      <MenuItem id="rename" isDisabled={isMerged}>
        Rename
      </MenuItem>
      <MenuItem id="delete" isDisabled={isMerged} tone="danger">
        Delete
      </MenuItem>
      {isMerged ? <MenuNote>Split its variants first.</MenuNote> : null}
    </MenuButton>
  );
}

interface ActionsProps {
  canonicalTag: string;
  isMerged: boolean;
  selected: boolean;
  onToggleSelect?: ((tag: string) => void) | undefined;
  onRename?: ((tag: string) => void) | undefined;
  onDelete?: ((tag: string) => void) | undefined;
}

/** The card's top-right controls: the actions menu, then the batch-merge checkbox. */
function CardActions({
  canonicalTag,
  isMerged,
  selected,
  onToggleSelect,
  onRename,
  onDelete,
}: Readonly<ActionsProps>) {
  return (
    <div className="absolute top-3 right-3 flex items-center gap-1.5">
      {onRename && onDelete ? (
        <TagActionsMenu
          canonicalTag={canonicalTag}
          isMerged={isMerged}
          onRename={onRename}
          onDelete={onDelete}
        />
      ) : null}
      {onToggleSelect ? (
        <button
          type="button"
          role="checkbox"
          aria-checked={selected}
          aria-label={`Select ${canonicalTag}`}
          onClick={() => onToggleSelect(canonicalTag)}
          className={cn(
            "flex size-[18px] cursor-pointer items-center justify-center rounded border text-xs leading-none",
            selected
              ? "border-primary bg-primary text-primary-foreground"
              : "border-border-strong bg-surface",
          )}
        >
          {selected ? "✓" : ""}
        </button>
      ) : null}
    </div>
  );
}

/** The batch-merge direction marker: this card keeps its name, or folds into the survivor. */
function MergeHintPill({ hint }: Readonly<{ hint: MergeHint }>) {
  const keeps = hint.kind === "keep";
  return (
    <span
      className={cn(
        "mt-2 inline-block rounded-full px-2 py-0.5 text-[11px] font-semibold",
        keeps
          ? "text-primary-strong bg-[color-mix(in_srgb,var(--primary)_14%,var(--surface))]"
          : "bg-surface-sunken text-muted-foreground",
      )}
    >
      {keeps ? "★ keeps name" : `→ ${hint.target}`}
    </span>
  );
}

interface ChipsProps {
  canonicalTag: string;
  aliases: string[];
  landedVariant: string | undefined;
  onSplit: (variant: string) => void;
}

/** The aliases merged into this tag, each removable: removing one splits it back into its own tag. */
function AliasChips({
  canonicalTag,
  aliases,
  landedVariant,
  onSplit,
}: Readonly<ChipsProps>) {
  return (
    <div className="mt-2 flex flex-wrap gap-1.5">
      {aliases.map((variant) => (
        <span
          key={variant}
          className={cn(
            "bg-surface-sunken text-muted-foreground inline-flex items-center gap-1 rounded-full py-0.5 pr-1 pl-2.5 text-xs",
            variant === landedVariant && "animate-chip-land",
          )}
        >
          {variant}
          <button
            type="button"
            aria-label={`Split ${variant} out of ${canonicalTag}`}
            onClick={() => onSplit(variant)}
            className="hover:text-primary-strong cursor-pointer rounded-full px-1 leading-none transition-colors hover:bg-[color-mix(in_srgb,var(--primary)_12%,transparent)]"
          >
            ×
          </button>
        </span>
      ))}
    </div>
  );
}

/**
 * One tag in the curation inventory: the canonical name plus its aliases as
 * removable chips. No usage count: on the curation page the only thing that
 * matters is the vocabulary, not who knows it.
 */
export function TagCard({
  canonicalTag,
  variants,
  onSplit,
  selected,
  onToggleSelect,
  hint,
  landedVariant,
  onRename,
  onDelete,
}: Readonly<Props>) {
  // The backend always folds a card's own tag into its variant list. It's
  // redundant with the title and can't be split off (splitting the name is a
  // no-op), so the chips show only the genuine aliases merged into this area.
  const aliases = variants.filter((variant) => variant !== canonicalTag);
  const hasMenu = onRename !== undefined && onDelete !== undefined;
  const hasActions = hasMenu || onToggleSelect !== undefined;
  return (
    <div
      className={cn(
        "border-border bg-surface relative rounded-xl border p-3.5 shadow-sm transition-shadow hover:shadow-md",
        selected && "border-primary shadow-[0_0_0_1px_var(--primary)]",
      )}
    >
      {hasActions ? (
        <CardActions
          canonicalTag={canonicalTag}
          isMerged={aliases.length > 0}
          selected={selected === true}
          onToggleSelect={onToggleSelect}
          onRename={onRename}
          onDelete={onDelete}
        />
      ) : null}
      <p
        className={cn(
          "font-display text-foreground font-semibold",
          hasActions && "pr-14",
        )}
      >
        {canonicalTag}
      </p>
      {hint ? <MergeHintPill hint={hint} /> : null}
      {aliases.length === 0 ? (
        <p className="text-muted-foreground mt-2 text-xs">no aliases</p>
      ) : (
        <AliasChips
          canonicalTag={canonicalTag}
          aliases={aliases}
          landedVariant={landedVariant}
          onSplit={onSplit}
        />
      )}
    </div>
  );
}

import type { ReactNode } from "react";
import { ListBox, ListBoxItem } from "react-aria-components";

import { cn } from "@/lib/cn";

export interface ComboBoxOption {
  id: string;
  label: string;
  hint?: string;
  /** Optional leading glyph, shown in the list. `textValue` keeps it out of the option's
   *  accessible name and out of the typeahead. */
  icon?: ReactNode;
}

export type HintStyle = "text" | "code";

const itemClass =
  "flex cursor-pointer items-center justify-between gap-2 rounded-sm px-3 py-2 text-sm text-foreground outline-none transition-colors data-[hovered]:bg-surface-sunken data-[focused]:bg-surface-sunken";

function Option({
  option,
  hintStyle,
}: Readonly<{ option: ComboBoxOption; hintStyle: HintStyle | undefined }>) {
  return (
    <ListBoxItem id={option.id} textValue={option.label} className={itemClass}>
      <span className="flex min-w-0 items-center gap-2">
        {option.icon !== undefined && (
          <span aria-hidden className="shrink-0">
            {option.icon}
          </span>
        )}
        <span className="truncate">{option.label}</span>
      </span>
      {option.hint !== undefined && (
        <span
          className={cn(
            "text-muted-foreground shrink-0 text-xs",
            hintStyle === "code" && "font-mono",
          )}
        >
          {option.hint}
        </span>
      )}
    </ListBoxItem>
  );
}

/** The popover's body: the suggestions themselves, or the status line that stands in for them.
 *  The sentinel sits below the list inside the popover, which is the scrolling element, so it
 *  comes into view only once the list is scrolled to its foot. */
export function ComboBoxSuggestions({
  options,
  statusMessage,
  hintStyle,
  hasMore,
  sentinelRef,
}: Readonly<{
  options: readonly ComboBoxOption[];
  statusMessage: string | undefined;
  hintStyle: HintStyle | undefined;
  hasMore: boolean;
  sentinelRef: (node: HTMLDivElement | null) => void;
}>): ReactNode {
  if (options.length === 0) {
    return (
      <p role="status" className="text-muted-foreground px-3 py-2 text-sm">
        {statusMessage}
      </p>
    );
  }
  return (
    <>
      <ListBox items={options} className="outline-none">
        {(option: ComboBoxOption) => (
          <Option option={option} hintStyle={hintStyle} />
        )}
      </ListBox>
      {hasMore && <div ref={sentinelRef} aria-hidden className="h-px" />}
    </>
  );
}

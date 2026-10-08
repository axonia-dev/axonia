import { ChevronDown } from "lucide-react";
import { type ReactNode, useEffect, useRef } from "react";
import {
  Button as AriaButton,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Select as AriaSelect,
  SelectValue,
  Text,
} from "react-aria-components";

import { cn } from "@/lib/cn";
import { focusIfDropped } from "@/lib/use-answered-after-failing";

// Exported for callers that build the option array outside this component (e.g. the
// role pickers, which mark an out-of-reach role disabled with a reason).
export interface SelectOption<T extends string | number> {
  id: T;
  label: string;
  /** Optional leading glyph for this option, shown in the list and on the trigger when selected. */
  icon?: ReactNode;
  /** Offered but not choosable. The option stays listed so a reader can tell an act they may
   *  not perform from one the product does not offer. */
  isDisabled?: boolean;
  /** Why it cannot be chosen, read out with the option. */
  description?: string;
}

interface SelectProps<T extends string | number> {
  label: string;
  value: T | null;
  options: ReadonlyArray<SelectOption<T>>;
  onChange: (value: T) => void;
  placeholder?: string;
  /** Visually hide the label (still exposed to assistive tech) for compact rows. */
  hideLabel?: boolean;
  /** Optional decorative glyph rendered ahead of the selected value. */
  leadingIcon?: ReactNode;
  /** The options are still loading: the trigger says so and cannot be pressed, where an empty
   *  list would ignore the press without a word. */
  isLoading?: boolean;
  /** Moves the focus to the trigger once it shows, as a field that replaces a failure message
   *  after its retry must: the message's Try again held the focus, and it is gone. Only a focus
   *  that message held moves (`focusIfDropped`). */
  takesFocus?: boolean;
  className?: string;
}

const triggerClass =
  "flex w-full items-center justify-between gap-2 rounded-md border border-border bg-surface px-3 py-2 text-sm text-foreground outline-none transition-colors hover:border-border-strong focus-visible:ring-2 focus-visible:ring-focus-ring data-[disabled]:cursor-default data-[disabled]:opacity-50 data-[disabled]:hover:border-border";

const popoverClass =
  "min-w-[var(--trigger-width)] rounded-md border border-border-strong bg-surface p-1 shadow-lg outline-none transition data-[entering]:opacity-0 data-[entering]:-translate-y-1 data-[exiting]:opacity-0 data-[exiting]:-translate-y-1 motion-reduce:transition-none";

const itemClass =
  "flex cursor-pointer items-center justify-between gap-2 rounded-sm px-3 py-2 text-sm text-foreground outline-none transition-colors data-[hovered]:bg-surface-sunken data-[focused]:bg-surface-sunken data-[selected]:font-semibold data-[selected]:text-primary";

/**
 * A labelled single-choice dropdown over a fixed set of string- or number-keyed
 * options. Replaces ChoiceSelect (string) and CommunityForm's KindSelect (number)
 * with one styled component. The selected id is round-tripped by matching on
 * String(id), so the caller always gets back its own typed id (e.g. a number).
 */
export function Select<T extends string | number>({
  label,
  value,
  options,
  onChange,
  placeholder,
  hideLabel,
  leadingIcon,
  isLoading = false,
  takesFocus = false,
  className,
}: Readonly<SelectProps<T>>) {
  const trigger = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (takesFocus) focusIfDropped(trigger.current);
  }, [takesFocus]);
  const selected = options.find((o) => String(o.id) === String(value));
  const triggerIcon = selected?.icon ?? leadingIcon;
  const disabledIds = options
    .filter((o) => o.isDisabled === true)
    .map((o) => o.id);
  return (
    <AriaSelect
      // Controlled via react-aria-components' value/onChange (the current RAC API in this repo).
      value={value}
      onChange={(key) => {
        if (key == null) {
          return;
        }
        const picked = options.find((o) => String(o.id) === String(key));
        if (picked) {
          onChange(picked.id);
        }
      }}
      {...(disabledIds.length > 0 ? { disabledKeys: disabledIds } : {})}
      isDisabled={isLoading}
      className={cn("flex flex-col gap-1", className)}
    >
      <Label
        className={cn(
          "text-foreground text-sm font-medium",
          hideLabel && "sr-only",
        )}
      >
        {label}
      </Label>
      <AriaButton ref={trigger} className={triggerClass}>
        <span className="flex min-w-0 items-center gap-2">
          {triggerIcon !== undefined && (
            // Held to the text's line box, so an icon on a tinted badge cannot make a chosen value
            // taller than the placeholder.
            <span
              aria-hidden
              className="text-muted-foreground flex h-5 shrink-0 items-center"
            >
              {triggerIcon}
            </span>
          )}
          <SelectValue className="truncate">
            {({ selectedText, isPlaceholder }) =>
              isLoading || isPlaceholder ? (
                <span className="text-muted-foreground">
                  {isLoading ? "Loading…" : (placeholder ?? "Select…")}
                </span>
              ) : (
                selectedText
              )
            }
          </SelectValue>
        </span>
        <ChevronDown
          size={16}
          aria-hidden
          className="text-muted-foreground shrink-0"
        />
      </AriaButton>
      <Popover className={popoverClass}>
        <ListBox className="outline-none">
          {options.map((option) => (
            <ListBoxItem
              key={String(option.id)}
              id={option.id}
              textValue={option.label}
              className={itemClass}
            >
              <span className="flex min-w-0 flex-col">
                <span className="flex min-w-0 items-center gap-2">
                  {option.icon !== undefined && (
                    <span aria-hidden className="shrink-0">
                      {option.icon}
                    </span>
                  )}
                  <Text slot="label" className="truncate">
                    {option.label}
                  </Text>
                </span>
                {option.description !== undefined && (
                  <Text
                    slot="description"
                    className="text-muted-foreground block text-xs"
                  >
                    {option.description}
                  </Text>
                )}
              </span>
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </AriaSelect>
  );
}

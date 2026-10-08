import type { ReactNode } from "react";
import {
  Button as AriaButton,
  Header,
  type Key,
  Menu as AriaMenu,
  MenuItem as AriaMenuItem,
  MenuSection as AriaMenuSection,
  MenuTrigger,
  Popover,
  Separator,
} from "react-aria-components";

import { cn } from "@/lib/cn";

/** A menu's items are either independent commands (the default, `selectionMode`
 *  omitted) or the mutually exclusive answers to one question - `selectionMode="single"`
 *  with `selectedKeys` gives those items native `menuitemradio`/`aria-checked` semantics,
 *  so the current answer is conveyed to assistive tech without any hand-rolled text. */
interface MenuSelectionProps {
  selectionMode?: "single" | "multiple";
  selectedKeys?: Iterable<Key>;
}

interface MenuButtonProps extends MenuSelectionProps {
  /** Accessible name for the trigger. */
  label: string;
  /** Optional custom trigger content; defaults to the label text. */
  trigger?: ReactNode;
  onAction?: (key: Key) => void;
  className?: string;
  children: ReactNode;
}

export const menuPopoverClass =
  "min-w-[12rem] rounded-md border border-border-strong bg-surface p-1 shadow-lg outline-none transition data-[entering]:opacity-0 data-[entering]:-translate-y-1 data-[exiting]:opacity-0 data-[exiting]:-translate-y-1 motion-reduce:transition-none";

// `group` so an item's own content can style itself off the item's semantic state - the checkmark
// beside a picked answer is drawn from `aria-checked` rather than from a second boolean a caller
// has to keep in step with the selection.
const itemClass =
  "group flex cursor-pointer items-center gap-2 rounded-sm px-3 py-2 text-sm text-foreground outline-none transition-colors data-[hovered]:bg-surface-sunken data-[focused]:bg-surface-sunken";

function MenuPopover({
  onAction,
  selectionMode,
  selectedKeys,
  children,
}: Readonly<
  MenuSelectionProps & {
    onAction?: (key: Key) => void;
    children: ReactNode;
  }
>) {
  return (
    <Popover className={menuPopoverClass}>
      <AriaMenu
        {...(onAction !== undefined ? { onAction } : {})}
        {...(selectionMode !== undefined ? { selectionMode } : {})}
        {...(selectedKeys !== undefined ? { selectedKeys } : {})}
        className="outline-none"
      >
        {children}
      </AriaMenu>
    </Popover>
  );
}

export function MenuButton({
  label,
  trigger,
  onAction,
  selectionMode,
  selectedKeys,
  className,
  children,
}: Readonly<MenuButtonProps>) {
  return (
    <MenuTrigger>
      <AriaButton
        aria-label={label}
        className={cn(
          "focus-visible:ring-focus-ring cursor-pointer rounded-md outline-none focus-visible:ring-2",
          className,
        )}
      >
        {trigger ?? label}
      </AriaButton>
      <MenuPopover
        {...(onAction !== undefined ? { onAction } : {})}
        {...(selectionMode !== undefined ? { selectionMode } : {})}
        {...(selectedKeys !== undefined ? { selectedKeys } : {})}
      >
        {children}
      </MenuPopover>
    </MenuTrigger>
  );
}

interface MenuItemProps {
  id: Key;
  isDisabled?: boolean;
  /** Renders the item as a same-tab link, for a destination that is a real route rather
   *  than an in-place action. */
  href?: string;
  /** Runs when this item is chosen, besides the menu's own `onAction`, for an item an edition
   *  contributes and whose action the menu does not know. */
  onAction?: () => void;
  /** `danger` marks an item whose action is destructive, colouring its label
   *  and its glyph together. Destructiveness is a property of the item, not a
   *  colour the caller picks. */
  tone?: "default" | "danger";
  children: ReactNode;
}

export function MenuItem({
  id,
  isDisabled,
  href,
  onAction,
  tone = "default",
  children,
}: Readonly<MenuItemProps>) {
  return (
    <AriaMenuItem
      id={id}
      {...(href !== undefined ? { href } : {})}
      {...(onAction !== undefined ? { onAction } : {})}
      isDisabled={isDisabled ?? false}
      className={cn(
        itemClass,
        "data-[disabled]:opacity-50",
        tone === "danger" && "text-danger",
      )}
    >
      {children}
    </AriaMenuItem>
  );
}

/** A rule between groups of menu items. */
export function MenuSeparator() {
  return <Separator className="border-border my-1 border-t" />;
}

/**
 * The mutually exclusive answers to one question, grouped so the selection stops at their edge.
 *
 * The alternative - `selectionMode="single"` on the whole menu - reaches every sibling too, so a
 * plain command sitting beside the answers becomes a `menuitemradio` announcing itself as an
 * unpicked answer to a question it has nothing to do with.
 */
export function MenuAnswerSection({
  label,
  selectedKey,
  children,
}: Readonly<{ label: string; selectedKey: Key; children: ReactNode }>) {
  return (
    <AriaMenuSection selectionMode="single" selectedKeys={[selectedKey]}>
      <Header className="text-muted-foreground px-3 py-1.5 text-xs">
        {label}
      </Header>
      {children}
    </AriaMenuSection>
  );
}

/**
 * The fact a menu's items answer to, stated above them.
 *
 * A `Header` rather than a disabled item on purpose: an item would join the collection, take
 * arrow-key focus, and read to assistive tech as one more answer to a question that has three.
 */
export function MenuHeading({
  statement,
  subline,
}: Readonly<{ statement: string; subline: string }>) {
  // `max-w-72` rather than content-sized: a menu's width is driven by its widest child, so a long
  // statement would stretch the whole popover into one line wider than a phone.
  return (
    <Header className="border-border mb-1 max-w-72 border-b px-3 pt-1 pb-2.5">
      <span className="text-foreground block text-sm font-medium text-pretty">
        {statement}
      </span>
      <span className="text-muted-foreground mt-0.5 block text-xs">
        {subline}
      </span>
    </Header>
  );
}

/** A quiet line inside the popover, for saying why the items above it are disabled. */
export function MenuNote({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <Header className="text-muted-foreground px-3 py-1.5 text-xs">
      {children}
    </Header>
  );
}

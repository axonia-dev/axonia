import type { ReactNode } from "react";
import {
  Header,
  type Key,
  Menu as AriaMenu,
  MenuSection,
} from "react-aria-components";

import { MenuItem, MenuNote, MenuSeparator } from "@/components/ui/Menu";

/** The one glyph size every schedule menu item uses. Both menus open in the same popover, so a
 *  right-click on a day and one on an occurrence have to read as one list rather than two. */
export const menuIconClass = "h-3.5 w-3.5 shrink-0";

/** One command in a schedule menu. `tone` belongs to the action - a destructive one colours its
 *  label and its glyph together - and is never a colour a caller picks. */
export interface Action {
  id: string;
  label: string;
  icon?: ReactNode;
  tone?: "danger";
  /** For an action that is real but out of reach right now. Disabled and stated is the only
   *  honest pair: an item that stays live and then does nothing is worse than one that is out. */
  isDisabled?: boolean;
}

/** A run of commands that all reach equally far. The label names that reach, and is omitted
 *  for a menu whose items are all one reach and need no heading to say so. */
export interface ActionGroup {
  label?: string;
  actions: Action[];
}

/** What the menu was opened on, so a popover covering its own target still says what it acts on. */
export interface MenuTitle {
  primary: string;
  secondary?: string;
}

/**
 * What the menu answers to when it is asked its name.
 *
 * The heading is a non-focusable collection node, which the focus ring steps straight over: a
 * reader arriving on the first command hears the menu's own name and nothing else. Naming the
 * menu after what it was opened on is what closes, for that reader, the very ambiguity the
 * heading closes for everyone looking at it.
 */
function menuName(title: MenuTitle | undefined): string {
  if (title === undefined) return "Schedule actions";
  return title.secondary === undefined
    ? title.primary
    : `${title.primary}, ${title.secondary}`;
}

const groupLabelClass =
  "text-muted-foreground px-3 pt-1.5 pb-0.5 text-[10px] font-bold tracking-wider uppercase";

function MenuHeading({ title }: Readonly<{ title: MenuTitle }>) {
  return (
    <Header className="px-3 py-1.5 text-xs leading-tight">
      <span className="text-foreground block truncate font-semibold">
        {title.primary}
      </span>
      {title.secondary !== undefined && (
        <span className="text-muted-foreground mt-0.5 block">
          {title.secondary}
        </span>
      )}
    </Header>
  );
}

function actionItem(action: Action) {
  return (
    <MenuItem
      key={action.id}
      id={action.id}
      isDisabled={action.isDisabled ?? false}
      {...(action.tone === undefined ? {} : { tone: action.tone })}
    >
      {action.icon}
      {action.label}
    </MenuItem>
  );
}

/** One flat array of collection children: react-aria builds its collection from what the menu
 *  renders, so a group contributes its rule and its items to that one list rather than nesting. */
function groupNodes(groups: ActionGroup[], hasTitle: boolean): ReactNode[] {
  return groups.flatMap((group, index) => [
    ...(index > 0 || hasTitle ? [<MenuSeparator key={`rule-${index}`} />] : []),
    ...(group.label === undefined
      ? group.actions.map(actionItem)
      : [
          <MenuSection key={`section-${index}`}>
            <Header className={groupLabelClass}>{group.label}</Header>
            {group.actions.map(actionItem)}
          </MenuSection>,
        ]),
  ]);
}

/**
 * The commands a right-click offers, in the same menu the session page opens.
 *
 * It carries no trigger of its own, because this menu opens at a pointer coordinate rather than
 * under a button, and that is the one thing `MenuTrigger` cannot do. Everything else about it -
 * the roles, the keyboard, the shape and spacing of an item - is the shared menu's, so a reader
 * who has used one has used the other.
 *
 * An empty list is an answer rather than an omission: a right-click that offers nothing says so
 * inside the menu, as a note, rather than opening an empty box or a stray line of prose.
 *
 * A labelled group renders as a real `role="group"` section, so a command's reach travels to
 * assistive tech through the group's accessible name rather than through a word repeated in every
 * one of its labels. An empty group drops out entirely, heading and rule with it.
 */
export function ActionList({
  title,
  groups,
  note,
  onAction,
}: Readonly<{
  title?: MenuTitle;
  groups: ActionGroup[];
  /** Why the items above read as they do, when the list alone cannot say. */
  note?: string;
  onAction: (key: Key) => void;
}>) {
  const filled = groups.filter((group) => group.actions.length > 0);
  return (
    <AriaMenu
      aria-label={menuName(title)}
      onAction={onAction}
      className="outline-none"
    >
      {title !== undefined && <MenuHeading title={title} />}
      {groupNodes(filled, title !== undefined)}
      {note !== undefined && <MenuNote>{note}</MenuNote>}
      {filled.length === 0 && note === undefined && (
        <MenuNote>No actions</MenuNote>
      )}
    </AriaMenu>
  );
}

import type { ReactNode } from "react";
import { useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";

import type { NavGroup } from "@/components/patterns/nav-types";
import {
  groupHeaderClass,
  isItemActive,
  sheetLinkClass,
  tabClass,
} from "@/components/patterns/shell-nav";
import { Dialog } from "@/components/ui/Dialog";
import { cn } from "@/lib/cn";

export type ShellTab =
  | { kind: "link"; to: string; icon: ReactNode; label: string; end?: boolean }
  | {
      kind: "menu";
      key: string;
      icon: ReactNode;
      label: string;
      isActive: boolean;
      sheetTitle: string;
      groups: ReadonlyArray<NavGroup>;
    };

type MenuTab = Extract<ShellTab, { kind: "menu" }>;

/** Inner content of a menu sheet — extracted to keep nesting ≤ 4 levels. */
function MenuSheetContent({
  menu,
  close,
}: Readonly<{ menu: MenuTab; close: () => void }>) {
  const { pathname } = useLocation();
  return (
    <nav aria-label={menu.sheetTitle} className="flex flex-col gap-4">
      {menu.groups.map((group) => (
        <div key={group.label} className="flex flex-col gap-1">
          {menu.groups.length > 1 && (
            <p className={groupHeaderClass}>{group.label}</p>
          )}
          {group.items.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              aria-current={isItemActive(item, pathname) ? "page" : undefined}
              onClick={close}
              className={sheetLinkClass({
                isActive: isItemActive(item, pathname),
              })}
            >
              {item.icon}
              <span>{item.label}</span>
            </Link>
          ))}
        </div>
      ))}
    </nav>
  );
}

/** Fixed bottom tab bar (mobile). Link tabs navigate; menu tabs open a sheet
 *  listing their groups' sections. Group headers show only when a sheet has
 *  more than one group (matches the main shell's "Admin" sheet). */
export function ShellTabBar({
  ariaLabel,
  testId,
  tabs,
}: Readonly<{
  ariaLabel: string;
  testId: string;
  tabs: ReadonlyArray<ShellTab>;
}>) {
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const menus = tabs.filter((tab): tab is MenuTab => tab.kind === "menu");

  return (
    <>
      <nav
        aria-label={ariaLabel}
        data-testid={testId}
        className="border-border bg-surface fixed inset-x-0 bottom-0 z-10 flex items-center justify-around border-t px-2 py-2 md:hidden"
      >
        {tabs.map((tab) =>
          tab.kind === "link" ? (
            <NavLink
              key={tab.to}
              to={tab.to}
              // Bottom-bar destinations default to exact-match active (unlike
              // sheet links below, which prefix-match) so a parent tab isn't
              // lit up while you're on a deeper child route.
              end={tab.end ?? true}
              className={tabClass}
            >
              {tab.icon}
              <span>{tab.label}</span>
            </NavLink>
          ) : (
            <button
              key={tab.key}
              type="button"
              onClick={() => setOpenMenu(tab.key)}
              aria-haspopup="dialog"
              aria-expanded={openMenu === tab.key}
              className={cn(
                tabClass({ isActive: tab.isActive }),
                "cursor-pointer",
              )}
            >
              {tab.icon}
              <span>{tab.label}</span>
            </button>
          ),
        )}
      </nav>

      {menus.map((menu) => (
        <Dialog
          key={menu.key}
          isOpen={openMenu === menu.key}
          onOpenChange={(open) => setOpenMenu(open ? menu.key : null)}
          title={menu.sheetTitle}
        >
          {({ close }) => <MenuSheetContent menu={menu} close={close} />}
        </Dialog>
      ))}
    </>
  );
}

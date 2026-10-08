import { CircleQuestionMark } from "lucide-react";
import { NavLink } from "react-router-dom";

import { cn } from "@/lib/cn";

import type { DocPage } from "../domain/doc-page";
import { DOC_PAGES } from "../domain/doc-registry";

function groupPages(): { group: string; pages: DocPage[] }[] {
  const groups: { group: string; pages: DocPage[] }[] = [];
  for (const page of DOC_PAGES) {
    const existing = groups.find((entry) => entry.group === page.group);
    if (existing) {
      existing.pages.push(page);
    } else {
      groups.push({ group: page.group, pages: [page] });
    }
  }
  return groups;
}

/**
 * `onGetHelp` is a callback rather than a dialog owned here: the dialog and its open state belong
 * to `DocsShell`, which already owns the layout and both navs, so a nav component staying a plain
 * link list (rather than owning a modal) is the right seam.
 */
export function DocsNav({ onGetHelp }: Readonly<{ onGetHelp?: () => void }>) {
  return (
    <nav className="flex flex-col gap-1">
      {groupPages().map(({ group, pages }) => (
        <div key={group}>
          <p className="text-muted-foreground px-2 pt-3 pb-1 text-xs font-bold tracking-wide uppercase">
            {group}
          </p>
          {pages.map((page) => (
            <NavLink
              key={page.slug}
              to={`/docs/${page.slug}`}
              className={({ isActive }) =>
                cn(
                  "block rounded-md px-3 py-2 text-sm",
                  isActive
                    ? "text-primary font-semibold"
                    : "text-foreground hover:bg-surface-sunken",
                )
              }
            >
              {page.title}
            </NavLink>
          ))}
        </div>
      ))}
      {onGetHelp !== undefined && (
        <button
          type="button"
          onClick={onGetHelp}
          className="text-muted-foreground hover:bg-surface-sunken hover:text-foreground mt-2 flex items-center gap-2 rounded-md px-3 py-2 text-left text-xs"
        >
          <CircleQuestionMark aria-hidden className="size-3.5 shrink-0" />
          Get help
        </button>
      )}
    </nav>
  );
}

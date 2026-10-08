import { ArrowLeft } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link, Outlet, useParams } from "react-router-dom";

import {
  BACK_LABEL,
  useAppReturnPath,
} from "@/components/patterns/settings-return";
import { useEdition } from "@/edition-context";
import { AccountMenu } from "@/features/identity/ui/AccountMenu";

import { DocsMobileNav } from "./DocsMobileNav";
import { DocsNav } from "./DocsNav";
import { OnThisPage } from "./OnThisPage";

/**
 * Docs is a shell-replacing surface, not a page nested inside GlobalShell or
 * CommunityShell: it does not reuse AppShell, which requires a `renderSwitcher` and pulls
 * in the organization and community queries - exactly the coupling that would block the
 * public unauthenticated documentation surface, deferred for now.
 *
 * Owns the open state of the edition's help entry, when it supplies one: the desktop `DocsNav`
 * renders the link at the foot of its page menu via a callback prop, but the dialog itself belongs
 * up here, beside the layout both navs share, not inside a single nav component.
 */
export function DocsShell() {
  const { slug = "" } = useParams();
  const contentRef = useRef<HTMLDivElement>(null);
  const [gettingHelp, setGettingHelp] = useState(false);
  const { HelpEntry } = useEdition();
  const backTo = useAppReturnPath("/");

  // This div is the scroll container now, in place of the host shell's <main> - which
  // reset its own scroll on navigation (see AppShell). An internal scroll container does
  // not do that on its own, so without this a reader landing mid-page on the previous
  // article would stay scrolled to that same offset on the new one.
  const scrollRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = 0;
  }, [slug]);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="border-border flex shrink-0 items-center justify-between gap-4 border-b px-4 py-3 md:px-6">
        <Link
          to={backTo}
          className="text-muted-foreground hover:text-foreground flex items-center gap-2 text-sm font-semibold"
        >
          <ArrowLeft size={16} aria-hidden />
          {BACK_LABEL}
        </Link>
        <AccountMenu />
      </header>
      <div
        ref={scrollRef}
        data-testid="docs-scroll"
        // `relative` for the same reason AppShell's <main> carries it: a scroll container
        // that is not a containing block lets absolutely positioned content escape its
        // clip and extend the document's scroll area.
        className="relative min-h-0 flex-1 overflow-y-auto"
      >
        {/* min-h-full: the grid sizes to its content, so on a short page the nav aside's
            border-r stopped where the content did, hanging in the whitespace below. Filling
            the scroll container runs the divider the whole height without making anything
            scroll that did not before: the container itself is what scrolls. */}
        <div className="mx-auto grid min-h-full w-full max-w-6xl grid-cols-1 gap-0 md:grid-cols-[220px_minmax(0,1fr)_200px]">
          <aside className="border-border hidden py-6 pr-4 md:block md:border-r">
            {HelpEntry === null ? (
              <DocsNav />
            ) : (
              <DocsNav onGetHelp={() => setGettingHelp(true)} />
            )}
          </aside>
          {/* min-w-0: without it this grid cell keeps its content's min-width, so a
              wide <pre> code block would stretch the whole page on mobile instead of
              scrolling inside its own box. */}
          <div ref={contentRef} className="min-w-0 px-4 py-8 md:px-10">
            <DocsMobileNav />
            <Outlet />
          </div>
          <aside className="hidden py-8 pl-4 md:block">
            <OnThisPage containerRef={contentRef} slug={slug} />
          </aside>
        </div>
      </div>
      {HelpEntry !== null && (
        <HelpEntry isOpen={gettingHelp} onOpenChange={setGettingHelp} />
      )}
    </div>
  );
}

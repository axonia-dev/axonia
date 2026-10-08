import { ChevronDown } from "lucide-react";
import type { ReactNode } from "react";
import { useContext } from "react";
import {
  Button as AriaButton,
  Disclosure,
  DisclosurePanel,
  DisclosureStateContext,
  Heading,
} from "react-aria-components";

import { cn } from "@/lib/cn";

/** Read from the context rather than from a render prop: `Button` reports its own state (pressed,
 *  focused) and knows nothing about the disclosure it triggers. */
function TriggerBody({
  title,
  summary,
}: Readonly<{ title: string; summary: string }>) {
  const isExpanded = useContext(DisclosureStateContext)?.isExpanded ?? false;
  return (
    <>
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-foreground text-sm font-semibold">{title}</span>
        {!isExpanded && (
          <span className="text-muted-foreground text-xs">{summary}</span>
        )}
      </span>
      <ChevronDown
        aria-hidden
        className={cn(
          "text-muted-foreground h-4 w-4 shrink-0 transition-transform motion-reduce:transition-none",
          isExpanded && "rotate-180",
        )}
      />
    </>
  );
}

interface Props {
  title: string;
  /**
   * What the section holds, for deciding whether to open it without opening it.
   *
   * Shown only while it is shut. Open, the fields it summarises are directly below it, and a line
   * describing them is a caption on something already in plain sight.
   */
  summary: string;
  /** Open on mount. For a section that already holds an answer, where shut would hide something
   *  the reader put there. */
  defaultExpanded?: boolean;
  children: ReactNode;
}

/**
 * An optional part of a form, shut until it is wanted.
 *
 * A form whose every field is always on screen makes the required and the optional look alike, and
 * pushes its own submit past the fold: the series form opened at eight fields plus a structure
 * editor, with Start below the bottom of the panel, when four of those fields answer questions
 * most series never have. Shut, each of these is one line stating what it holds.
 *
 * Built on `Disclosure` rather than a bare `<details>` so the trigger carries `aria-expanded` and
 * `aria-controls` against a panel that is out of the accessibility tree while it is shut, and so
 * the heading level is ours to place rather than the browser's to invent. The panel itself stays
 * in the DOM and is hidden, which is what lets find-in-page reach a shut section.
 */
export function DisclosureSection({
  title,
  summary,
  defaultExpanded = false,
  children,
}: Readonly<Props>) {
  return (
    <Disclosure
      defaultExpanded={defaultExpanded}
      className="border-border rounded-md border"
    >
      <Heading level={3}>
        <AriaButton
          slot="trigger"
          className="focus-visible:ring-focus-ring flex w-full cursor-pointer items-center justify-between gap-3 rounded-md px-3.5 py-3 text-left outline-none focus-visible:ring-2"
        >
          <TriggerBody title={title} summary={summary} />
        </AriaButton>
      </Heading>
      {/* The padding sits inside the panel: a shut one is `hidden="until-found"`, which keeps its
          own padding on screen. */}
      <DisclosurePanel>
        <div className="flex flex-col gap-4 px-3.5 pt-1 pb-4">{children}</div>
      </DisclosurePanel>
    </Disclosure>
  );
}

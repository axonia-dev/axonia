import { ChevronRight } from "lucide-react";
import { useState } from "react";
import {
  Button as AriaButton,
  Disclosure,
  DisclosurePanel,
  Heading,
} from "react-aria-components";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";
import { failedToLoad } from "@/lib/failed-to-load";
import { useMailServerChanges } from "@/selfhosted/features/mail-server/api/use-mail-server";
import type {
  MailServerChange,
  MailServerView,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  actorLabel,
  changedAt,
  changeLines,
  lastChangeSummary,
} from "@/selfhosted/features/mail-server/domain/mail-server-copy";

function ChangeRow({ change }: Readonly<{ change: MailServerChange }>) {
  return (
    <li className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-0.5 text-xs">
      <span className="text-foreground min-w-0 font-medium wrap-anywhere">
        {actorLabel(change.actorName)}
      </span>
      <span className="text-muted-foreground">
        {changedAt(change.occurredAt)}
      </span>
      {changeLines(change.changes).map((line) => (
        <span
          key={line}
          className="text-muted-foreground col-span-2 min-w-0 wrap-anywhere"
        >
          {line}
        </span>
      ))}
    </li>
  );
}

/** Mounted only while the history is open, so a shut history costs no read. */
function ChangeList() {
  const changes = useMailServerChanges();
  if (failedToLoad(changes)) {
    return (
      <LoadFailed
        inline
        message="Could not load the history."
        query={changes}
      />
    );
  }
  if (changes.data === undefined) {
    return <p className="text-muted-foreground text-xs">Loading…</p>;
  }
  const rows = changes.data.pages.flatMap((page) => page.content);
  return (
    <div className="flex flex-col items-start gap-3">
      <ol className="flex w-full flex-col gap-3">
        {rows.map((row) => (
          <ChangeRow key={row.id} change={row} />
        ))}
      </ol>
      {changes.hasNextPage && (
        <Button
          variant="ghost"
          size="sm"
          onPress={() => void changes.fetchNextPage()}
          isPending={changes.isFetchingNextPage}
        >
          Show older
        </Button>
      )}
    </div>
  );
}

type Props = Readonly<{
  lastChange: NonNullable<MailServerView["lastChange"]>;
}>;

/** One line while shut, "History, last changed by Ana Ruiz on 2 Oct 2026"; open, every change. */
export function MailServerHistory({ lastChange }: Props) {
  const [isOpen, setOpen] = useState(false);
  return (
    <Disclosure isExpanded={isOpen} onExpandedChange={setOpen}>
      {/* A flex box, so the line is the trigger's own height and not the heading's inherited
          line box, which left a taller gap under the history than above the card's header. */}
      <Heading level={3} className="flex">
        <AriaButton
          slot="trigger"
          className="text-muted-foreground hover:text-foreground focus-visible:ring-focus-ring inline-flex cursor-pointer items-center gap-1 rounded-sm text-left text-xs outline-none focus-visible:ring-2"
        >
          <ChevronRight
            aria-hidden
            className={cn(
              "size-3.5 shrink-0 transition-transform motion-reduce:transition-none",
              isOpen && "rotate-90",
            )}
          />
          {lastChangeSummary(lastChange)}
        </AriaButton>
      </Heading>
      {/* The spacing sits inside the open panel: a shut one is `hidden="until-found"`, which keeps
          its own padding on screen. */}
      <DisclosurePanel>
        {isOpen && (
          <div className="pt-3">
            <ChangeList />
          </div>
        )}
      </DisclosurePanel>
    </Disclosure>
  );
}

import { ExternalLink } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import { cn } from "@/lib/cn";
import { relativeTime } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";

import type { Notification } from "../domain/notification";
import type { NotificationDestination } from "./notification-destination";
import { destinationOf } from "./notification-destination";

interface Props {
  entry: Notification;
  onOpen: (entry: Notification) => void;
}

const ROW = "flex w-full items-start gap-3 px-4 py-3 text-left";
const OPENABLE =
  "hover:bg-surface-sunken focus-visible:ring-focus-ring transition-colors outline-none focus-visible:ring-2 focus-visible:ring-inset";

/** Absent, blank and whitespace-only all mean the same thing here - nothing to show - and the
 *  three fields that can be absent go through this one check rather than each carrying its own
 *  nullish chain. */
function nonEmpty(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed === undefined || trimmed === "" ? undefined : trimmed;
}

function RowBody({ entry }: Readonly<{ entry: Notification }>) {
  const sender = nonEmpty(entry.sender);
  const line = nonEmpty(entry.line);
  const now = useNow();
  return (
    <>
      <span
        aria-hidden
        className={cn(
          "mt-1.5 size-2 shrink-0 rounded-full",
          entry.read ? "bg-transparent" : "bg-primary",
        )}
      />
      <span className="min-w-0 flex-1">
        <span className="text-foreground block truncate text-sm font-medium">
          {entry.headline}
        </span>
        {line !== undefined && (
          <span className="text-muted-foreground block truncate text-sm">
            {line}
          </span>
        )}
        <span className="text-muted-foreground mt-1 flex items-center gap-1.5 text-xs">
          {sender !== undefined && (
            <>
              <span className="min-w-0 truncate">{sender}</span>
              <span aria-hidden>·</span>
            </>
          )}
          <time dateTime={entry.sentAt} className="shrink-0">
            {relativeTime(entry.sentAt, now)}
          </time>
        </span>
      </span>
    </>
  );
}

/** A row is a link only when it has somewhere to go. An entry whose message carried no usable
 *  destination still belongs in the record, so it renders as plain text: a control that leads
 *  nowhere is worse than no control. */
function RowShell({
  destination,
  onOpen,
  children,
}: Readonly<{
  destination: NotificationDestination;
  onOpen: () => void;
  children: ReactNode;
}>) {
  if (destination.kind === "internal") {
    return (
      <Link to={destination.to} onClick={onOpen} className={cn(ROW, OPENABLE)}>
        {children}
      </Link>
    );
  }
  if (destination.kind === "external") {
    return (
      <a
        href={destination.href}
        target="_blank"
        rel="noreferrer"
        onClick={onOpen}
        className={cn(ROW, OPENABLE)}
      >
        {children}
        <ExternalLink
          aria-hidden
          size={13}
          className="text-muted-foreground mt-1 shrink-0"
        />
      </a>
    );
  }
  return <div className={ROW}>{children}</div>;
}

export function NotificationRow({ entry, onOpen }: Readonly<Props>) {
  const destination = destinationOf(entry.actionUrl, window.location.origin);
  return (
    <li className="border-border/60 border-b last:border-b-0">
      <RowShell
        destination={destination}
        onOpen={() => {
          onOpen(entry);
        }}
      >
        <RowBody entry={entry} />
      </RowShell>
    </li>
  );
}

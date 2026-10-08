import type { InfiniteData } from "@tanstack/react-query";
import {
  CheckCheck,
  Inbox,
  MoreHorizontal,
  SlidersHorizontal,
  TriangleAlert,
} from "lucide-react";
import type { ReactNode } from "react";
import { useCallback, useContext } from "react";
import {
  Dialog,
  Heading,
  OverlayTriggerStateContext,
} from "react-aria-components";
import { Link } from "react-router-dom";

import { ACCOUNT_NOTIFICATION_SETTINGS } from "@/components/patterns/settings-nav.account";
import { useLoadMoreOnView } from "@/components/patterns/use-load-more-on-view";
import { iconButtonClass } from "@/components/ui/IconButton";
import { MenuButton, MenuItem } from "@/components/ui/Menu";
import { Skeleton } from "@/components/ui/Skeleton";

import {
  useMarkAllRead,
  useMarkRead,
  useNotifications,
} from "../api/use-notifications";
import type { Notification, NotificationPage } from "../domain/notification";
import { NotificationRow } from "./NotificationRow";

const MARK_ALL_READ = "mark-all-read";

/** The nullish chain lives here, with an early return, rather than inline in a component where
 *  every `?.` and `??` counts against the complexity cap. */
function entriesOf(
  data: InfiniteData<NotificationPage> | undefined,
): Notification[] {
  if (data === undefined) return [];
  return data.pages.flatMap((page) => page.entries);
}

function PanelPlaceholder() {
  return (
    <div className="space-y-3 px-4 py-4">
      <Skeleton className="h-4 w-2/3" />
      <Skeleton className="h-4 w-1/2" />
      <Skeleton className="h-4 w-3/5" />
    </div>
  );
}

/** Not the shared EmptyState: that one is a dashed card sized for a page body, and a card inside a
 *  popover only draws a second border around the border already there. */
function PanelNotice({
  icon,
  title,
  detail,
}: Readonly<{ icon: ReactNode; title: string; detail: string }>) {
  return (
    <div className="flex flex-col items-center gap-1 px-6 py-10 text-center">
      <span aria-hidden className="mb-1">
        {icon}
      </span>
      <p className="font-display text-foreground text-sm font-semibold">
        {title}
      </p>
      <p className="text-muted-foreground text-xs">{detail}</p>
    </div>
  );
}

function PanelBody({
  entries,
  isSuccess,
  isError,
  hasNextPage,
  onOpen,
}: Readonly<{
  entries: Notification[];
  isSuccess: boolean;
  isError: boolean;
  hasNextPage: boolean;
  onOpen: (entry: Notification) => void;
}>) {
  if (entries.length > 0) {
    return (
      <ul>
        {entries.map((entry) => (
          <NotificationRow key={entry.id} entry={entry} onOpen={onOpen} />
        ))}
      </ul>
    );
  }
  // A failure is checked before the placeholder, which is otherwise permanent: `isSuccess` never
  // turns true for a query that ended in an error, so three loading bars would pulse forever with
  // nothing ever saying why. Closing the panel unmounts this query, so opening it again is a fresh
  // mount of a stale query - a real retry, not a promise this cannot keep.
  if (isError) {
    return (
      <PanelNotice
        icon={<TriangleAlert size={20} className="text-danger/70" />}
        title="Could not load the notifications"
        detail="Reopening the panel tries again."
      />
    );
  }
  // "Nothing yet" is a statement about the record, so it waits until there is a record to make it
  // about: an unresolved first page shows a placeholder instead, and so does a resolved one that
  // rendered nothing while pages still remain behind it. That second case is a window whose every
  // row carried a message type this deploy cannot read: the panel is paging straight past it, and
  // saying there is nothing would be a verdict on a record still arriving.
  if (!isSuccess || hasNextPage) return <PanelPlaceholder />;
  return (
    <PanelNotice
      icon={<Inbox size={20} className="text-muted-foreground/60" />}
      title="Nothing yet"
      detail="Messages from communities land here."
    />
  );
}

/**
 * A member's own record of every message the spine ever delivered to them, newest first, twenty at
 * a time. No filter of any kind: every entry names the community it came from, and narrowing to
 * "this community" would be meaningless on every page that is not a community page.
 *
 * Renders the popover's dialog itself, so the surface owns its own title and accessible name, and
 * {@link NotificationBell} stays the trigger and nothing more. Opening an entry closes the panel
 * through the overlay state the popover provides - absent when the panel is rendered on its own,
 * which is why the call is optional rather than required.
 */
export function NotificationPanel() {
  const query = useNotifications();
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();
  const overlay = useContext(OverlayTriggerStateContext);
  const entries = entriesOf(query.data);

  const { fetchNextPage } = query;
  const loadMore = useCallback(() => {
    void fetchNextPage();
  }, [fetchNextPage]);
  const { rootRef, sentinelRef } = useLoadMoreOnView(
    query.hasNextPage && !query.isFetchingNextPage,
    loadMore,
  );

  function openEntry(entry: Notification) {
    // An entry already read costs a request that would change nothing.
    if (!entry.read) markRead.mutate(entry.id);
    overlay?.close();
  }

  return (
    <Dialog className="flex min-h-0 flex-col outline-none">
      <div className="border-border flex items-center justify-between gap-2 border-b px-4 py-3">
        <Heading
          slot="title"
          className="font-display text-foreground text-sm font-semibold"
        >
          Notifications
        </Heading>
        <MenuButton
          label="More"
          trigger={<MoreHorizontal size={16} aria-hidden />}
          className={iconButtonClass()}
          onAction={(key) => {
            if (key === MARK_ALL_READ) markAllRead.mutate();
          }}
        >
          <MenuItem id={MARK_ALL_READ}>
            <CheckCheck aria-hidden className="h-3.5 w-3.5 shrink-0" />
            Mark all as read
          </MenuItem>
        </MenuButton>
      </div>

      {/* The popover's `maxHeight` caps the box but clips nothing on its own: this is the element
          that actually scrolls inside it. */}
      <div
        ref={rootRef}
        className="relative min-h-0 flex-1 overflow-y-auto overscroll-contain"
      >
        <PanelBody
          entries={entries}
          isSuccess={query.isSuccess}
          isError={query.isError}
          hasNextPage={query.hasNextPage}
          onOpen={openEntry}
        />
        {query.hasNextPage && (
          <div ref={sentinelRef} aria-hidden className="h-4" />
        )}
      </div>

      <div className="border-border border-t px-4 py-2.5">
        <Link
          to={`/${ACCOUNT_NOTIFICATION_SETTINGS}`}
          onClick={() => overlay?.close()}
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1.5 text-xs font-medium transition-colors"
        >
          <SlidersHorizontal aria-hidden size={13} />
          Edit preferences
        </Link>
      </div>
    </Dialog>
  );
}

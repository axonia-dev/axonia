import { Hash, Mail, MoreHorizontal, Pencil, Send, Trash2 } from "lucide-react";
import type { Key } from "react-aria-components";

import { Button } from "@/components/ui/Button";
import { MenuButton, MenuItem } from "@/components/ui/Menu";
import { StatusChip } from "@/components/ui/StatusChip";
import type { ChatDestination } from "@/features/community/domain/chat-destination";
import {
  destinationDetail,
  formatChatTimestamp,
} from "@/features/community/domain/chat-destination";

const ICON_CLASS =
  "bg-surface-sunken text-muted-foreground flex size-7 flex-none items-center justify-center rounded-md";

const ROW_CLASS = "flex items-center gap-3 px-4 py-3";

/**
 * A broken row has the most to say - the full date, the full reason, and
 * sometimes a test-post error too - and the least room to say it in, sitting
 * beside the same chip/button/menu cluster a healthy row fits on one line.
 * `flex-wrap` plus a full-width controls cluster (see `controlsClass`) gives
 * the text its own line rather than shrinking it to fit; `items-start`
 * anchors the icon and the cluster to the top of that taller block instead
 * of floating them against its vertical centre.
 */
const BROKEN_ROW_CLASS = "flex flex-wrap items-start gap-3 px-4 py-3";

/** The channel every community always has. Fixed, and deliberately actionless. */
export function EmailRow() {
  return (
    <li className={ROW_CLASS}>
      <span className={ICON_CLASS}>
        <Mail size={14} aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="text-foreground block text-sm font-semibold">
          Email
        </span>
        <span className="text-muted-foreground block text-xs">
          Every member, unless they opted out
        </span>
      </span>
      <StatusChip tone="success" label="Always on" />
    </li>
  );
}

/** What went wrong, and when: the gap between a webhook rotated an hour ago and
 *  one that has been silently dead for a quarter. Degrades to the reason alone
 *  if the failure time is somehow missing. */
function brokenDetail(destination: ChatDestination): string {
  const reason =
    destination.lastError ?? "axonia has stopped posting to this destination";
  if (destination.lastFailureAt == null) {
    return reason;
  }
  return `Stopped posting on ${formatChatTimestamp(destination.lastFailureAt)}: ${reason}`;
}

/** Truncating a healthy row's short platform/timestamp line is fine; truncating
 *  a broken row's date-and-reason sentence is how the date we added becomes
 *  unreadable mid-token. Only the healthy line is a single-line truncation. */
function detailClass(broken: boolean): string {
  return broken
    ? "text-danger block text-xs"
    : "text-muted-foreground block truncate text-xs";
}

/** On a healthy row the cluster sits inline, right of the text, same as always.
 *  On a broken row it claims the full row width, which - inside a `flex-wrap`
 *  parent - is what forces it onto its own line below the text rather than
 *  squeezing that text's column down to a sliver. `pl-10` lines its start up
 *  with the text column above it (the icon's `size-7` plus the row's `gap-3`). */
function controlsClass(broken: boolean): string {
  return broken
    ? "flex w-full items-center justify-end gap-2 pl-10"
    : "flex items-center gap-2";
}

interface DestinationRowProps {
  destination: ChatDestination;
  isTesting: boolean;
  justPosted: boolean;
  testError: string | null;
  onTest: (destination: ChatDestination) => void;
  onEdit: (destination: ChatDestination) => void;
  onRemove: (destination: ChatDestination) => void;
}

export function DestinationRow({
  destination,
  isTesting,
  justPosted,
  testError,
  onTest,
  onEdit,
  onRemove,
}: Readonly<DestinationRowProps>) {
  const broken = destination.broken;
  const detail = broken
    ? brokenDetail(destination)
    : destinationDetail(destination);

  function onAction(key: Key) {
    if (key === "test") {
      onTest(destination);
    } else if (key === "edit") {
      onEdit(destination);
    } else if (key === "remove") {
      onRemove(destination);
    }
  }

  return (
    <li
      aria-label={destination.name}
      className={broken ? BROKEN_ROW_CLASS : ROW_CLASS}
    >
      <span className={ICON_CLASS}>
        <Hash size={14} aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="text-foreground block truncate text-sm font-semibold">
          {destination.name}
        </span>
        <span className={detailClass(broken)}>{detail}</span>
        {testError !== null && (
          <p role="alert" className="text-danger text-xs">
            {testError}
          </p>
        )}
      </span>
      <div className={controlsClass(broken)}>
        <StatusChip
          tone={broken ? "danger" : "success"}
          label={broken ? "Broken" : "Connected"}
        />
        <span role="status" className="text-success text-xs">
          {justPosted ? "Posted, check the channel" : ""}
        </span>
        {broken && (
          <Button
            variant="outline"
            size="sm"
            onPress={() => onTest(destination)}
            isDisabled={isTesting}
            aria-label={`Test sending ${destination.name}`}
          >
            <Send size={13} aria-hidden />
            Test sending
          </Button>
        )}
        <MenuButton
          label={`Actions for ${destination.name}`}
          trigger={<MoreHorizontal size={16} aria-hidden />}
          className="text-muted-foreground hover:text-foreground p-1"
          onAction={onAction}
        >
          {!broken && (
            <MenuItem id="test" isDisabled={isTesting}>
              <Send size={14} aria-hidden />
              Test sending
            </MenuItem>
          )}
          <MenuItem id="edit">
            <Pencil size={14} aria-hidden />
            Edit
          </MenuItem>
          <MenuItem id="remove" tone="danger">
            <Trash2 size={14} aria-hidden />
            Remove
          </MenuItem>
        </MenuButton>
      </div>
    </li>
  );
}

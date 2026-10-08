import { CountBadge } from "@/components/patterns/CountBadge";
import { Button } from "@/components/ui/Button";
import type { MemberStatus } from "@/features/identity/domain/user";
import { MemberLabel } from "@/features/identity/ui/MemberLabel";
import type { ReactionDefinition } from "@/features/recognition/domain/reaction";

function placementsWord(count: number) {
  return count === 1 ? "placement" : "placements";
}

/** The body of the removal confirmation: a reaction goes down with everything placed with it. */
export function removalMessage(definition: ReactionDefinition) {
  if (definition.usageCount === 0) {
    return "This reaction has no placements. Removing it cannot be undone.";
  }
  return `${definition.usageCount} ${placementsWord(definition.usageCount)} carry this reaction. Removing it takes those placements down too, and cannot be undone.`;
}

/** "n of limit", falling back to the bare count until the ceiling has arrived. Shared so the
 *  administration grid and a member's own queue read the same way. */
export function headroom(count: number, limit: number | undefined): string {
  return limit === undefined ? `${count}` : `${count} of ${limit}`;
}

interface ThumbnailProps {
  imageUrl: string;
  name: string;
}

function Thumbnail({ imageUrl, name }: Readonly<ThumbnailProps>) {
  return (
    <img
      src={imageUrl}
      alt=""
      title={name}
      width={20}
      height={20}
      className="size-5 shrink-0 rounded-full object-cover"
    />
  );
}

interface Proposer {
  name: string;
  status: MemberStatus;
}

interface PendingRowProps {
  definition: ReactionDefinition;
  /** The proposer as identity resolves them, resolved by the manager. */
  proposedBy: Proposer;
  canWrite: boolean;
  onApprove: () => void;
  onDecline: () => void;
}

export function PendingRow({
  definition,
  proposedBy,
  canWrite,
  onApprove,
  onDecline,
}: Readonly<PendingRowProps>) {
  return (
    <li className="border-border flex items-center gap-3 rounded-md border px-3 py-2">
      <Thumbnail imageUrl={definition.imageUrl} name={definition.name} />
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="truncate font-medium">{definition.name}</span>
        <span className="text-muted-foreground text-xs">
          Proposed by{" "}
          <MemberLabel name={proposedBy.name} status={proposedBy.status} />
        </span>
      </div>
      {canWrite && (
        // The name sits two columns to the left, so the button carries the verb and lends the
        // name to its accessible label, where a reader jumping between buttons still needs it.
        <div className="flex shrink-0 gap-2">
          <Button
            variant="secondary"
            size="sm"
            aria-label={`Approve ${definition.name}`}
            onPress={onApprove}
          >
            Approve
          </Button>
          <Button
            variant="ghost"
            size="sm"
            aria-label={`Decline ${definition.name}`}
            onPress={onDecline}
          >
            Decline
          </Button>
        </div>
      )}
    </li>
  );
}

interface OwnProposalRowProps {
  definition: ReactionDefinition;
  isWithdrawing: boolean;
  onWithdraw: () => void;
}

/**
 * A proposal as its own proposer sees it: no proposer line, because the reader is the proposer.
 * Withdraw is the same call the queue's Decline makes and gets the same treatment - no
 * confirmation, since nothing is placed with a reaction that was never approved, and proposing
 * it again is the whole of the undo.
 */
export function OwnProposalRow({
  definition,
  isWithdrawing,
  onWithdraw,
}: Readonly<OwnProposalRowProps>) {
  return (
    <li className="border-border flex items-center gap-3 rounded-md border px-3 py-2">
      <Thumbnail imageUrl={definition.imageUrl} name={definition.name} />
      <span className="min-w-0 flex-1 truncate text-sm font-medium">
        {definition.name}
      </span>
      <Button
        variant="ghost"
        size="sm"
        className="shrink-0"
        aria-label={`Withdraw ${definition.name}`}
        isDisabled={isWithdrawing}
        onPress={onWithdraw}
      >
        Withdraw
      </Button>
    </li>
  );
}

interface ApprovedRowProps {
  definition: ReactionDefinition;
  canWrite: boolean;
  onRemove: () => void;
}

export function ApprovedRow({
  definition,
  canWrite,
  onRemove,
}: Readonly<ApprovedRowProps>) {
  return (
    <li className="border-border flex items-center gap-3 rounded-md border px-3 py-2">
      <Thumbnail imageUrl={definition.imageUrl} name={definition.name} />
      <span className="min-w-0 flex-1 truncate font-medium">
        {definition.name}
      </span>
      <CountBadge
        value={definition.usageCount}
        label={placementsWord(definition.usageCount)}
        className="shrink-0"
      />
      {canWrite && (
        <Button
          variant="ghost"
          size="sm"
          className="shrink-0"
          aria-label={`Remove ${definition.name}`}
          onPress={onRemove}
        >
          Remove
        </Button>
      )}
    </li>
  );
}

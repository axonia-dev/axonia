import { useEffect, useState } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import { SettingsCard } from "@/components/patterns/SettingsCard";
import { Button } from "@/components/ui/Button";
import {
  useChatDestinationsQuery,
  useTestChatDestination,
} from "@/features/community/api/use-chat-destinations";
import { communityErrorMessage } from "@/features/community/community-errors";
import type { ChatDestination } from "@/features/community/domain/chat-destination";
import { DestinationDialog } from "@/features/community/ui/DestinationDialog";
import {
  DestinationRow,
  EmailRow,
} from "@/features/community/ui/DestinationRow";
import { RemoveDestinationDialog } from "@/features/community/ui/RemoveDestinationDialog";

const POSTED_CONFIRMATION_MS = 3000;

type DialogPayload =
  { mode: "add" } | { mode: "edit"; destination: ChatDestination };

/** The transient confirmation belongs to one row, so it is keyed by that row's id. */
function usePostedConfirmation() {
  const [postedId, setPostedId] = useState<number | null>(null);

  useEffect(() => {
    if (postedId === null) return;
    const timer = setTimeout(() => setPostedId(null), POSTED_CONFIRMATION_MS);
    return () => clearTimeout(timer);
  }, [postedId]);

  return { postedId, setPostedId };
}

interface TestOutcome {
  id: number;
  error: unknown;
}

/**
 * A test send is one shared mutation, but its outcome belongs to whichever row
 * triggered it. `testingId` and `failure` are both keyed by that row's id, the
 * same shape `postedId` already uses, so testing one destination never
 * disables or misattributes another.
 *
 * `clear` exists for the same reason `RemoveDestinationDialog` needs a
 * `resetKey`: this hook lives in `DestinationsCard`, which never unmounts, so
 * a test failure recorded for a destination survives even after that
 * destination is edited and saved with a fix. The caller invokes it once a
 * save actually succeeds.
 */
function useDestinationTest(slug: string) {
  const test = useTestChatDestination(slug);
  const { postedId, setPostedId } = usePostedConfirmation();
  const [testingId, setTestingId] = useState<number | null>(null);
  const [failure, setFailure] = useState<TestOutcome | null>(null);

  function run(destination: ChatDestination) {
    setPostedId(null);
    setFailure(null);
    setTestingId(destination.id);
    test.mutate(destination.id, {
      onSuccess: () => setPostedId(destination.id),
      onError: (error) => setFailure({ id: destination.id, error }),
      onSettled: () => setTestingId(null),
    });
  }

  function clear(id: number) {
    setPostedId((current) => (current === id ? null : current));
    setFailure((current) => (current?.id === id ? null : current));
  }

  return { run, testingId, postedId, failure, clear };
}

interface DestinationListProps {
  isPending: boolean;
  isError: boolean;
  destinations: ChatDestination[];
  testingId: number | null;
  postedId: number | null;
  failure: TestOutcome | null;
  onTest: (destination: ChatDestination) => void;
  onEdit: (destination: ChatDestination) => void;
  onRemove: (destination: ChatDestination) => void;
}

/** The card body below the header: Email always, then whatever the query
 *  resolved to. Split out of `DestinationsCard` so the query's three states
 *  (loading, error, resolved) don't push that component's own branch count
 *  toward the eslint ceiling. */
function DestinationList({
  isPending,
  isError,
  destinations,
  testingId,
  postedId,
  failure,
  onTest,
  onEdit,
  onRemove,
}: Readonly<DestinationListProps>) {
  return (
    <>
      <ul className="divide-border divide-y">
        <EmailRow />
        {destinations.map((destination) => (
          <DestinationRow
            key={destination.id}
            destination={destination}
            isTesting={testingId === destination.id}
            justPosted={postedId === destination.id}
            testError={
              failure?.id === destination.id
                ? communityErrorMessage(failure.error)
                : null
            }
            onTest={onTest}
            onEdit={onEdit}
            onRemove={onRemove}
          />
        ))}
      </ul>
      {isPending && (
        <p className="text-muted-foreground px-4 py-3 text-sm">Loading…</p>
      )}
      {isError && (
        <p role="alert" className="text-danger px-4 py-3 text-sm">
          Could not load chat destinations.
        </p>
      )}
      {!isPending && !isError && destinations.length === 0 && (
        <EmptyState
          title="No chat destinations"
          description="Post the same messages into a Slack or Teams channel."
        />
      )}
    </>
  );
}

interface DestinationsCardProps {
  slug: string;
}

export function DestinationsCard({ slug }: Readonly<DestinationsCardProps>) {
  const query = useChatDestinationsQuery(slug);
  const { run, testingId, postedId, failure, clear } = useDestinationTest(slug);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogPayload, setDialogPayload] = useState<DialogPayload>({
    mode: "add",
  });
  // Bumped on every explicit open (not on close): forces the dialog's form to
  // start from fresh field values each time it opens, including reopening the
  // same destination, without tying that reset to `isOpen` itself - see
  // `DestinationDialog`'s `resetKey` for why `isOpen` can't drive it directly.
  const [dialogGeneration, setDialogGeneration] = useState(0);
  const [removeOpen, setRemoveOpen] = useState(false);
  const [removeTarget, setRemoveTarget] = useState<ChatDestination | null>(
    null,
  );
  // Same shape as `dialogGeneration`, for `RemoveDestinationDialog`'s own
  // `resetKey` - see that component for why.
  const [removeGeneration, setRemoveGeneration] = useState(0);

  const destinations = query.data ?? [];

  function openAdd() {
    setDialogPayload({ mode: "add" });
    setDialogGeneration((generation) => generation + 1);
    setDialogOpen(true);
  }

  function openEdit(destination: ChatDestination) {
    setDialogPayload({ mode: "edit", destination });
    setDialogGeneration((generation) => generation + 1);
    setDialogOpen(true);
  }

  function openRemove(destination: ChatDestination) {
    setRemoveTarget(destination);
    setRemoveGeneration((generation) => generation + 1);
    setRemoveOpen(true);
  }

  function onDestinationSaved() {
    if (dialogPayload.mode === "edit") {
      clear(dialogPayload.destination.id);
    }
  }

  return (
    <>
      <SettingsCard
        title="Destinations"
        action={
          <Button variant="outline" onPress={openAdd}>
            Add destination
          </Button>
        }
      >
        <DestinationList
          isPending={query.isPending}
          isError={query.isError}
          destinations={destinations}
          testingId={testingId}
          postedId={postedId}
          failure={failure}
          onTest={run}
          onEdit={openEdit}
          onRemove={openRemove}
        />
      </SettingsCard>
      <DestinationDialog
        slug={slug}
        destination={
          dialogPayload.mode === "edit" ? dialogPayload.destination : null
        }
        isOpen={dialogOpen}
        resetKey={dialogGeneration}
        onOpenChange={setDialogOpen}
        onSaved={onDestinationSaved}
      />
      <RemoveDestinationDialog
        slug={slug}
        destination={removeTarget}
        isOpen={removeOpen}
        resetKey={removeGeneration}
        onOpenChange={setRemoveOpen}
      />
    </>
  );
}

import { useCallback, useState } from "react";
import { useParams } from "react-router-dom";

import { useIsDelegated } from "@/auth/delegated-session";
import { ConfirmActionDialog } from "@/components/patterns/ConfirmActionDialog";
import { Button } from "@/components/ui/Button";
import { StatusChip } from "@/components/ui/StatusChip";
import {
  useEndPairing,
  useJournal,
} from "@/features/mentoring/api/use-mentoring";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import {
  mentoringErrorMessage,
  OWN_SIGN_IN_REFUSAL,
} from "@/features/mentoring/mentoring-errors";
import { TopicBoard } from "@/features/mentoring/ui/board/TopicBoard";
import { Finale } from "@/features/mentoring/ui/finale/Finale";
import { Postcard } from "@/features/mentoring/ui/finale/Postcard";
import { partnerRole } from "@/features/mentoring/ui/journal-people";
import { Logbook } from "@/features/mentoring/ui/logbook/Logbook";
import { pairingDates } from "@/features/mentoring/ui/mentoring-dates";
import { PartnerFace, personName } from "@/features/mentoring/ui/PartnerFace";
import { ShowingAsk } from "@/features/mentoring/ui/ShowingAsk";
import { JournalTrail } from "@/features/mentoring/ui/trail/JournalTrail";
import { TrailStats } from "@/features/mentoring/ui/trail/TrailStats";
import { ApiError } from "@/lib/api-client";

interface Ending {
  journal: Journal;
  onEnded: () => void;
}

function EndMentoring({ journal, onEnded }: Readonly<Ending>) {
  const { pairing } = journal;
  const end = useEndPairing(pairing.id, pairing.communitySlug);
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button variant="outline" size="sm" onPress={() => setOpen(true)}>
        End mentoring
      </Button>
      <ConfirmActionDialog
        isOpen={open}
        title="End this mentoring?"
        body="The journal stays readable to both people and takes no new topics or notes. Starting again needs a new proposal."
        error={end.isError ? mentoringErrorMessage(end.error) : null}
        confirmLabel="End mentoring"
        isPending={end.isPending}
        onCancel={() => {
          end.reset();
          setOpen(false);
        }}
        onConfirm={() =>
          end.mutate(undefined, {
            onSuccess: () => {
              setOpen(false);
              onEnded();
            },
          })
        }
      />
    </>
  );
}

function Header({
  journal,
  canEnd,
  onEnded,
}: Readonly<Ending & { canEnd: boolean }>) {
  const { pairing } = journal;
  return (
    <header className="flex flex-wrap items-center gap-3">
      <PartnerFace
        userId={pairing.partnerId}
        name={pairing.partnerName}
        avatarUrl={pairing.partnerAvatarUrl}
        className="size-12 text-sm"
      />
      <div className="min-w-0 flex-1">
        <h1 className="text-foreground font-display text-xl font-semibold wrap-anywhere">
          {personName(pairing.partnerName, pairing.partnerId)}
        </h1>
        <p className="text-muted-foreground text-sm wrap-anywhere">
          {partnerRole(pairing)} · {pairing.communityName} ·{" "}
          {pairingDates(pairing.startedAt, pairing.endedAt)}
        </p>
      </div>
      {pairing.endedAt !== null && <StatusChip tone="neutral" label="Ended" />}
      {canEnd && <EndMentoring journal={journal} onEnded={onEnded} />}
    </header>
  );
}

function Trail({
  journal,
  onOpenTopic,
}: Readonly<{ journal: Journal; onOpenTopic: (topicId: number) => void }>) {
  return (
    <section
      aria-label="Trail of the pairing"
      className="border-border bg-surface flex flex-col gap-5 rounded-3xl border p-4 sm:p-6"
    >
      <JournalTrail journal={journal} onOpenTopic={onOpenTopic} />
      <TrailStats journal={journal} />
    </section>
  );
}

const ONLY_THE_TWO = "Only the two people in this pairing see this journal.";
const IN_THE_EXPORT = "It is part of the organization’s data export.";

function privacyLine(journal: Journal): string {
  const shown =
    journal.showing.visibility === "SHOWN" && journal.pairing.endedAt === null;
  return shown
    ? `${ONLY_THE_TWO} Members of the community see only that the pairing exists. ${IN_THE_EXPORT}`
    : `${ONLY_THE_TWO} ${IN_THE_EXPORT}`;
}

function Body({ journal }: Readonly<{ journal: Journal }>) {
  const writable = journal.writable;
  const [focusTopicId, setFocusTopicId] = useState<number | null>(null);
  const focusHandled = useCallback(() => setFocusTopicId(null), []);
  const [postcard, setPostcard] = useState(false);
  const mentee = journal.pairing.myRole === "MENTEE";
  return (
    <>
      <Header
        journal={journal}
        canEnd={writable && journal.pairing.endedAt === null}
        onEnded={() => setPostcard(mentee)}
      />
      <p className="text-muted-foreground text-sm">{privacyLine(journal)}</p>
      <ShowingAsk journal={journal} />
      <Trail journal={journal} onOpenTopic={setFocusTopicId} />
      <TopicBoard
        journal={journal}
        focusTopicId={focusTopicId}
        onFocusHandled={focusHandled}
      />
      <Logbook journal={journal} />
      <Finale journal={journal} onPostcard={() => setPostcard(true)} />
      {mentee && (
        <Postcard
          journal={journal}
          isOpen={postcard}
          onClose={() => setPostcard(false)}
        />
      )}
    </>
  );
}

function Note({ children }: Readonly<{ children: string }>) {
  return <p className="text-muted-foreground text-sm">{children}</p>;
}

const NOT_FOUND = "This journal was not found.";

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

/** One pairing's shared journal. A journal that is not the reader's reads as not found, calmly. */
export function JournalPage() {
  const { pairingId } = useParams();
  const id = Number(pairingId);
  const delegated = useIsDelegated();
  const journal = useJournal(id);
  if (delegated) return <Note>{OWN_SIGN_IN_REFUSAL}</Note>;
  if (!Number.isInteger(id) || isNotFound(journal.error)) {
    return <Note>{NOT_FOUND}</Note>;
  }
  return (
    <section className="flex flex-col gap-6">
      {journal.isLoading && <Note>Loading…</Note>}
      {!journal.isLoading && journal.data === undefined && (
        <p role="alert" className="text-danger text-sm">
          Could not load this journal.
        </p>
      )}
      {journal.data !== undefined && <Body journal={journal.data} />}
    </section>
  );
}

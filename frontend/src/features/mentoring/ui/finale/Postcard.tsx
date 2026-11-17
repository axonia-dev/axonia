import { Flower2 } from "lucide-react";
import { useState } from "react";
import { TextArea as AriaTextArea, TextField } from "react-aria-components";

import { ConfirmPopover } from "@/components/patterns/ConfirmPopover";
import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useWords } from "@/features/mentoring/api/use-mentoring";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { mentoringErrorMessage } from "@/features/mentoring/mentoring-errors";
import { HAND } from "@/features/mentoring/ui/handwriting";
import { onFailure } from "@/features/mentoring/ui/notify-failure";
import { personName } from "@/features/mentoring/ui/PartnerFace";
import { cn } from "@/lib/cn";

const MAX_WORDS_LENGTH = 1000;

interface Props {
  journal: Journal;
  isOpen: boolean;
  onClose: () => void;
}

/** The mentor the postcard goes to, in full and by given name when one is known. */
export function addresseeOf(journal: Journal): { name: string; first: string } {
  const { pairing } = journal;
  const name = personName(pairing.partnerName, pairing.partnerId);
  return { name, first: pairing.partnerGivenName ?? name };
}

const CARD =
  "bg-paper text-ink-sepia grid grid-cols-1 gap-4 rounded-lg p-4 shadow-[0_18px_40px_rgba(0,0,0,.25)] -rotate-1 sm:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] sm:p-5";
const LINES =
  "bg-[repeating-linear-gradient(transparent_0_27px,var(--paper-edge)_27px_28px)] text-lg leading-7";

function Address({ journal }: Readonly<{ journal: Journal }>) {
  const { name, first } = addresseeOf(journal);
  return (
    <div className="flex flex-col justify-between gap-4">
      <span
        aria-hidden
        className="grid h-[76px] w-16 place-items-center self-end rounded border-[3px] border-dotted border-[#d4a373] bg-[#fde7c8] text-[#c2703d]"
      >
        <Flower2 className="size-8" />
      </span>
      <div>
        <p
          className={cn(
            "border-paper-edge border-b-[1.5px] pb-1 text-lg",
            HAND,
          )}
        >
          To {name}
        </p>
        <p className="text-ink-sepia-soft mt-1 text-[11px]">
          Only {first} reads this. Editable for a year.
        </p>
      </div>
    </div>
  );
}

function Writing({
  journal,
  onClose,
}: Readonly<{ journal: Journal; onClose: () => void }>) {
  const { write } = useWords(journal.pairing.id);
  const [body, setBody] = useState(journal.myWords ?? "");
  const { first } = addresseeOf(journal);
  return (
    <>
      <div className={CARD}>
        <TextField
          aria-label={`Words for ${first}`}
          value={body}
          onChange={(next) => {
            write.reset();
            setBody(next);
          }}
          maxLength={MAX_WORDS_LENGTH}
          className="border-paper-edge sm:border-r-[1.5px] sm:border-dashed sm:pr-4"
        >
          <AriaTextArea
            rows={6}
            className={cn(
              "text-ink-sepia placeholder:text-ink-sepia-soft min-h-[168px] w-full resize-none border-0 outline-none",
              LINES,
              HAND,
            )}
            placeholder={`Dear ${first},`}
          />
        </TextField>
        <Address journal={journal} />
      </div>
      <FormError
        error={write.error}
        message={mentoringErrorMessage(write.error)}
      />
      <DialogFooter>
        <Button variant="ghost" onPress={onClose} isDisabled={write.isPending}>
          Later
        </Button>
        <Button
          isDisabled={body.trim() === "" || write.isPending}
          onPress={() => write.mutate(body.trim(), { onSuccess: onClose })}
        >
          Send to {first}
        </Button>
      </DialogFooter>
    </>
  );
}

function TakeBack({ journal }: Readonly<{ journal: Journal }>) {
  const { takeBack } = useWords(journal.pairing.id);
  const [confirming, setConfirming] = useState(false);
  const { first } = addresseeOf(journal);
  return (
    <>
      <Button variant="ghost" onPress={() => setConfirming(true)}>
        Take back
      </Button>
      <ConfirmPopover
        label="Confirm taking the postcard back"
        isOpen={confirming}
        confirmLabel="Take back"
        isPending={takeBack.isPending}
        onCancel={() => setConfirming(false)}
        onConfirm={() =>
          takeBack.mutate(undefined, {
            onSuccess: () => setConfirming(false),
            onError: onFailure,
          })
        }
      >
        Take this postcard back? {first} no longer sees it.
      </ConfirmPopover>
    </>
  );
}

function Sent({
  journal,
  words,
  onChange,
  onClose,
}: Readonly<{
  journal: Journal;
  words: string;
  onChange: () => void;
  onClose: () => void;
}>) {
  return (
    <>
      <div className={CARD}>
        <p
          className={cn(
            "border-paper-edge min-h-[168px] wrap-anywhere whitespace-pre-line sm:border-r-[1.5px] sm:border-dashed sm:pr-4",
            LINES,
            HAND,
          )}
        >
          {words}
        </p>
        <Address journal={journal} />
      </div>
      <DialogFooter>
        <TakeBack journal={journal} />
        {journal.canWriteWords && (
          <Button variant="secondary" onPress={onChange}>
            Change
          </Button>
        )}
        <Button onPress={onClose}>Close</Button>
      </DialogFooter>
    </>
  );
}

/** The mentee's words to their mentor, written as a postcard once the pairing ends. */
export function Postcard({ journal, isOpen, onClose }: Readonly<Props>) {
  const [changing, setChanging] = useState(false);
  const words = journal.myWords;
  const close = () => {
    setChanging(false);
    onClose();
  };
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={(open) => !open && close()}
      title={`To ${addresseeOf(journal).name}`}
      size="2xl"
    >
      {words === null || changing ? (
        <Writing journal={journal} onClose={close} />
      ) : (
        <Sent
          journal={journal}
          words={words}
          onChange={() => setChanging(true)}
          onClose={close}
        />
      )}
    </Dialog>
  );
}

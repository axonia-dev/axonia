import { type RefObject, useEffect, useId, useRef } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import type { MailServerView } from "@/selfhosted/features/mail-server/domain/mail-server";
import { MailServerClear } from "@/selfhosted/features/mail-server/ui/MailServerClear";
import { MailServerFields } from "@/selfhosted/features/mail-server/ui/MailServerFields";
import { MailServerTestRow } from "@/selfhosted/features/mail-server/ui/MailServerTestRow";
import { useMailServerForm } from "@/selfhosted/features/mail-server/ui/use-mail-server-form";

type Props = Readonly<{ view: MailServerView; onClose: () => void }>;

/** The first field the reader can type in, or the first button when the deployment sets them all. */
const FIRST_CONTROL = "input:not([type='hidden']), button";

/** Focus moves into the form as it opens, so a keyboard reader starts where the form does. */
function useFocusOnOpen() {
  const root = useRef<HTMLDivElement>(null);
  useEffect(() => {
    root.current?.querySelector<HTMLElement>(FIRST_CONTROL)?.focus();
  }, []);
  return root;
}

/** A test held back by a problem, or a value the installation refused, takes focus to the first
 *  input marked invalid, unless the reader moved focus while the call was in flight. Returns what
 *  a test or a save calls as it is sent, to note where focus was. */
function useFocusOnProblem(
  root: RefObject<HTMLDivElement | null>,
  problemsRaised: number,
) {
  const sentFrom = useRef<Element | null>(null);
  useEffect(() => {
    if (problemsRaised === 0) return;
    const focused = document.activeElement;
    if (focused !== sentFrom.current && focused !== document.body) return;
    root.current
      ?.querySelector<HTMLElement>("input[aria-invalid='true']")
      ?.focus();
  }, [root, problemsRaised]);
  return () => {
    sentFrom.current = document.activeElement;
  };
}

/** The form, inside the card. Not a `<form>`: Enter in a field must never save. */
export function MailServerForm({ view, onClose }: Props) {
  const form = useMailServerForm(view, onClose);
  const root = useFocusOnOpen();
  const noteFocus = useFocusOnProblem(root, form.problemsRaised);
  const sendTest = () => {
    noteFocus();
    form.sendTest();
  };
  const save = () => {
    noteFocus();
    form.save();
  };
  const resultId = useId();
  const { error, message } = form.saveNotice;
  return (
    <div ref={root} className="flex flex-col gap-5">
      <MailServerFields
        view={view}
        draft={form.draft}
        problems={form.problems}
        onChange={form.change}
      />
      <MailServerTestRow form={{ ...form, sendTest }} resultId={resultId} />
      <FormError
        error={error}
        {...(message === undefined ? {} : { message })}
      />
      <div className="flex flex-wrap items-center gap-2">
        {view.state !== "not_configured" && (
          <MailServerClear
            baseRevision={form.baseRevision}
            onStale={form.markStale}
            onCleared={onClose}
          />
        )}
        <div className="ml-auto flex gap-2">
          <Button variant="outline" onPress={onClose}>
            Cancel
          </Button>
          <Button
            onPress={save}
            isDisabled={!form.canSave}
            isPending={form.isSaving}
            aria-describedby={resultId}
          >
            Save
          </Button>
        </div>
      </div>
    </div>
  );
}

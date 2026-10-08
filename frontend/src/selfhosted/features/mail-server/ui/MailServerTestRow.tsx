import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { TextInput } from "@/components/ui/TextInput";
import type { TestOutcome } from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  acceptedLine,
  REFUSAL_LABELS,
} from "@/selfhosted/features/mail-server/domain/mail-server-copy";
import type { MailServerFormState } from "@/selfhosted/features/mail-server/ui/use-mail-server-form";

type ResultProps = Readonly<{ id: string; outcome: TestOutcome | null }>;

/** One live region from the start, whose words change: a region that appears already filled is
 *  not announced by every screen reader. It also says why Save is disabled, and Save points here. */
function TestStatus({ id, outcome }: ResultProps) {
  const accepted = outcome?.accepted === true;
  return (
    <p
      id={id}
      role="status"
      className={
        accepted ? "text-success text-sm" : "text-muted-foreground text-xs"
      }
    >
      {accepted
        ? acceptedLine(outcome)
        : "Save is available once a test mail with these values is accepted."}
    </p>
  );
}

function TestRefusal({ outcome }: Readonly<{ outcome: TestOutcome | null }>) {
  if (outcome === null || outcome.accepted) return null;
  return (
    <div role="alert" className="flex flex-col gap-1">
      <p className="text-danger text-sm font-medium">
        {REFUSAL_LABELS[outcome.reason]}.
      </p>
      {outcome.reply !== null && (
        <p className="text-muted-foreground font-mono text-xs wrap-anywhere">
          {outcome.reply}
        </p>
      )}
    </div>
  );
}

type Props = Readonly<{ form: MailServerFormState; resultId: string }>;

/** The recipient and "Send test mail", then what the server answered for exactly these values.
 *  The button sits beside the input itself, below its label and above any error line. */
export function MailServerTestRow({ form, resultId }: Props) {
  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-1 items-start gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
        <TextInput
          label="Test recipient"
          type="email"
          value={form.recipient}
          onChange={form.setRecipient}
          maxLength={254}
          {...(form.recipientError === undefined
            ? {}
            : { errorMessage: form.recipientError })}
        />
        <Button
          variant="secondary"
          className="sm:mt-6 sm:h-9.5"
          onPress={form.sendTest}
          isPending={form.isTesting}
        >
          Send test mail
        </Button>
      </div>
      <TestRefusal outcome={form.outcome} />
      <TestStatus id={resultId} outcome={form.outcome} />
      <FormError
        error={form.testError}
        {...(form.testMessage === undefined
          ? {}
          : { message: form.testMessage })}
      />
    </div>
  );
}

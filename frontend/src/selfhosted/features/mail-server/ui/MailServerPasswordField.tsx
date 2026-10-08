import { useEffect, useRef } from "react";

import { Button } from "@/components/ui/Button";
import { TextInput } from "@/components/ui/TextInput";
import type { MailServerView } from "@/selfhosted/features/mail-server/domain/mail-server";
import type { MailServerDraft } from "@/selfhosted/features/mail-server/domain/mail-server-form";
import { ReadOnlyField } from "@/selfhosted/features/mail-server/ui/ReadOnlyField";

type Props = Readonly<{
  password: MailServerView["password"];
  draft: MailServerDraft;
  error: string | undefined;
  /** Replace can be undone: the stored password still serves the draft (`canKeepStored`). */
  keepable: boolean;
  onChange: (patch: Partial<MailServerDraft>) => void;
}>;

/** Always the same eight dots, hidden from assistive tech, which hears what they stand for: a
 *  stored password is never sent to the browser. */
function Dots({ meaning }: Readonly<{ meaning: string }>) {
  return (
    <>
      <span aria-hidden>••••••••</span>
      <span className="sr-only">{meaning}</span>
    </>
  );
}

function passwordHint(
  password: MailServerView["password"],
  draft: MailServerDraft,
): string | undefined {
  if (!draft.replaceRequested) {
    return "The stored password is kept only for the saved server.";
  }
  return password.set && !password.readable
    ? "The stored password can’t be read. Enter it again."
    : undefined;
}

/** Replace swaps the dots for an empty input and moves focus into it, and Keep stored swaps them
 *  back and returns focus to Replace; nothing else moves it. */
function useFocusOnSwap(replacing: boolean) {
  const box = useRef<HTMLDivElement>(null);
  const pressed = useRef(false);
  useEffect(() => {
    if (!pressed.current) return;
    pressed.current = false;
    box.current?.querySelector(replacing ? "input" : "button")?.focus();
  }, [replacing]);
  return {
    box,
    pressSwap: () => {
      pressed.current = true;
    },
  };
}

type SwapProps = Readonly<{
  label: string;
  other: string;
  onPress: () => void;
}>;

/** Replace or Keep stored, as tall as the input beside it as Send test mail is. It lays the other
 *  word under its own, unseen, so it is as wide in both states and the input keeps its width. */
function SwapButton({ label, other, onPress }: SwapProps) {
  return (
    <Button variant="secondary" className="h-9.5" onPress={onPress}>
      <span className="grid justify-items-center">
        <span className="col-start-1 row-start-1">{label}</span>
        <span aria-hidden className="invisible col-start-1 row-start-1">
          {other}
        </span>
      </span>
    </Button>
  );
}

export function MailServerPasswordField({
  password,
  draft,
  error,
  keepable,
  onChange,
}: Props) {
  const { box, pressSwap } = useFocusOnSwap(draft.replacingPassword);
  const shown = error === undefined ? {} : { error };
  if (password.lockedBy !== null) {
    return (
      <ReadOnlyField
        label="Password"
        value={<Dots meaning="Set" />}
        lockedBy={password.lockedBy}
        {...shown}
      />
    );
  }
  if (!draft.replacingPassword) {
    const replace = (
      <SwapButton
        label="Replace"
        other="Keep stored"
        onPress={() => {
          pressSwap();
          onChange({ replaceRequested: true, password: "" });
        }}
      />
    );
    return (
      <div ref={box} className="min-w-0">
        <ReadOnlyField
          label="Password"
          value={<Dots meaning="Stored" />}
          action={replace}
          {...shown}
        />
      </div>
    );
  }
  const keep = (
    <SwapButton
      label="Keep stored"
      other="Replace"
      onPress={() => {
        pressSwap();
        onChange({ replaceRequested: false, password: "" });
      }}
    />
  );
  const hint = error === undefined ? passwordHint(password, draft) : undefined;
  return (
    <div ref={box} className="min-w-0">
      <TextInput
        label="Password"
        type="password"
        value={draft.password}
        onChange={(typed) => onChange({ password: typed })}
        maxLength={1024}
        {...(hint === undefined ? {} : { description: hint })}
        {...(error === undefined ? {} : { errorMessage: error })}
        {...(keepable ? { action: keep } : {})}
      />
    </div>
  );
}

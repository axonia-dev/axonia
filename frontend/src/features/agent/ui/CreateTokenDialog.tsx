import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { DatePicker } from "@/components/ui/DatePicker";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { ShowOnceSecret } from "@/components/ui/ShowOnceSecret";
import { todayIso } from "@/lib/today-iso";

import { useCreateToken } from "../api/use-personal-access-tokens";
import {
  type AgentScopeCode,
  type CreateTokenBody,
  createTokenBodySchema,
} from "../domain/personal-access-token";
import { TokenFields } from "./TokenFields";

interface CreateTokenDialogProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CreateTokenDialog({
  isOpen,
  onOpenChange,
}: Readonly<CreateTokenDialogProps>) {
  const [secret, setSecret] = useState<string | null>(null);

  function handleOpenChange(open: boolean) {
    if (!open) {
      setSecret(null);
    }
    onOpenChange(open);
  }

  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={handleOpenChange}
      title={secret === null ? "Create API key" : "Key created"}
      role={secret === null ? "dialog" : "alertdialog"}
    >
      {({ close }) =>
        secret === null ? (
          <CreateTokenForm onDone={close} onCreated={setSecret} />
        ) : (
          <NewKeyHandover secret={secret} onDone={close} />
        )
      }
    </Dialog>
  );
}

function NewKeyHandover({
  secret,
  onDone,
}: Readonly<{ secret: string; onDone: () => void }>) {
  return (
    <div className="flex flex-col gap-4">
      <ShowOnceSecret
        label="New API key"
        note="Shown once. It cannot be retrieved after this dialog closes."
        secret={secret}
      />
      <DialogFooter>
        <Button onPress={onDone}>Done</Button>
      </DialogFooter>
    </div>
  );
}

/**
 * Builds the create-token request body from raw form state, before validation. A blank `expiresOn`
 * becomes `null`, which is what the backend reads as "lasts until it is revoked"; an empty string
 * would be a date it could not parse.
 */
function buildCreateBody(
  name: string,
  scopes: AgentScopeCode[],
  expiresOn: string,
): unknown {
  return {
    name,
    scopes,
    expiresAt: expiresOn === "" ? null : `${expiresOn}T23:59:59Z`,
  };
}

interface CreateTokenFormProps {
  onDone: () => void;
  onCreated: (token: string) => void;
}

function CreateTokenForm({
  onDone,
  onCreated,
}: Readonly<CreateTokenFormProps>) {
  const create = useCreateToken();
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<AgentScopeCode[]>([]);
  const [expiresOn, setExpiresOn] = useState("");
  const [error, setError] = useState<string | null>(null);

  function onSubmitSuccess(body: CreateTokenBody) {
    create.mutate(body, {
      onSuccess: (res) => onCreated(res.token),
      onError: () => setError("Could not create the key. Try again."),
    });
  }

  function submit() {
    const parsed = createTokenBodySchema.safeParse(
      buildCreateBody(name, scopes, expiresOn),
    );
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Check the form.");
      return;
    }
    setError(null);
    onSubmitSuccess(parsed.data);
  }

  return (
    <div className="flex flex-col gap-4">
      <TokenFields
        name={name}
        onNameChange={setName}
        scopes={scopes}
        onScopesChange={setScopes}
      />

      <DatePicker
        label="Expires on (optional)"
        value={expiresOn}
        onChange={setExpiresOn}
        minValue={todayIso()}
      />

      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}

      <DialogFooter>
        <Button variant="ghost" onPress={onDone}>
          Cancel
        </Button>
        <Button onPress={submit} isDisabled={create.isPending}>
          Create
        </Button>
      </DialogFooter>
    </div>
  );
}

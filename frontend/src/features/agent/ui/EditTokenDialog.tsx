import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";

import { useUpdateToken } from "../api/use-personal-access-tokens";
import type {
  AgentScopeCode,
  PersonalToken,
  UpdateTokenBody,
} from "../domain/personal-access-token";
import {
  knownScopes,
  unknownScopes,
  updateTokenBodySchema,
} from "../domain/personal-access-token";
import { tokenTimeline } from "../domain/token-timeline";
import { TokenFields } from "./TokenFields";

interface EditTokenDialogProps {
  token: PersonalToken | null;
  now: Date;
  onOpenChange: (open: boolean) => void;
}

export function EditTokenDialog({
  token,
  now,
  onOpenChange,
}: Readonly<EditTokenDialogProps>) {
  return (
    <Dialog
      isOpen={token !== null}
      onOpenChange={onOpenChange}
      title="Edit API key"
    >
      {({ close }) =>
        token === null ? null : (
          <EditTokenForm token={token} now={now} onDone={close} />
        )
      }
    </Dialog>
  );
}

interface EditTokenFormProps {
  token: PersonalToken;
  now: Date;
  onDone: () => void;
}

function EditTokenForm({ token, now, onDone }: Readonly<EditTokenFormProps>) {
  const update = useUpdateToken();
  const [name, setName] = useState(token.name);
  const [scopes, setScopes] = useState<AgentScopeCode[]>(
    knownScopes(token.scopes),
  );
  const [error, setError] = useState<string | null>(null);
  const preservedScopes = unknownScopes(token.scopes);

  function submit() {
    const parsed = updateTokenBodySchema.safeParse({ name, scopes });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Check the form.");
      return;
    }
    setError(null);
    const body: UpdateTokenBody = {
      ...parsed.data,
      scopes: [...parsed.data.scopes, ...preservedScopes],
    };
    update.mutate(
      { id: token.id, body },
      {
        onSuccess: onDone,
        onError: () => setError("Could not save the key. Try again."),
      },
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <TokenFields
        name={name}
        onNameChange={setName}
        scopes={scopes}
        onScopesChange={setScopes}
      />

      <p className="text-muted-foreground text-xs">
        {tokenTimeline(token, now).expiry}. The expiry is fixed when a key is
        created.
      </p>

      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}

      <DialogFooter>
        <Button variant="ghost" onPress={onDone}>
          Cancel
        </Button>
        <Button onPress={submit} isDisabled={update.isPending}>
          Save
        </Button>
      </DialogFooter>
    </div>
  );
}

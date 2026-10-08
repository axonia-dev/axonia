import { Plus } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { EmptyState } from "@/components/patterns/EmptyState";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { PageHeader } from "@/components/patterns/PageHeader";
import { Button } from "@/components/ui/Button";
import { CopyField } from "@/components/ui/CopyField";
import { failedToLoad, type RetryableRead } from "@/lib/failed-to-load";
import { useNow } from "@/lib/use-wake-at";

import { useMyTokens, useRevokeToken } from "../api/use-personal-access-tokens";
import { mcpEndpointUrl } from "../domain/mcp";
import type { PersonalToken } from "../domain/personal-access-token";
import { ConnectedApps } from "./ConnectedApps";
import { CreateTokenDialog } from "./CreateTokenDialog";
import { EditTokenDialog } from "./EditTokenDialog";
import { RevokeTokenDialog } from "./RevokeTokenDialog";
import { TokenList } from "./TokenList";

function TokenListSection({
  isLoading,
  failure,
  tokens,
  now,
  onRevoke,
  onEdit,
}: Readonly<{
  isLoading: boolean;
  failure: RetryableRead | undefined;
  tokens: PersonalToken[];
  now: Date;
  onRevoke: (token: PersonalToken) => void;
  onEdit: (token: PersonalToken) => void;
}>) {
  if (failure !== undefined) {
    return (
      <LoadFailed
        inline
        message="Could not load the API keys."
        query={failure}
      />
    );
  }
  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (tokens.length === 0) {
    return (
      <EmptyState
        title="No keys yet"
        description="A key lets an AI agent act on behalf of this account, limited to the permissions it is granted."
      />
    );
  }
  return (
    <TokenList tokens={tokens} now={now} onRevoke={onRevoke} onEdit={onEdit} />
  );
}

export function ApiKeysView() {
  const tokensQuery = useMyTokens();
  const { data, isLoading } = tokensQuery;
  const revoke = useRevokeToken();
  const tokens = data ?? [];
  const now = useNow();

  const [creating, setCreating] = useState(false);
  const [pendingRevoke, setPendingRevoke] = useState<PersonalToken | null>(
    null,
  );
  const [editing, setEditing] = useState<PersonalToken | null>(null);

  function beginRevoke(token: PersonalToken) {
    revoke.reset();
    setPendingRevoke(token);
  }

  function confirmRevoke() {
    if (pendingRevoke === null) {
      return;
    }
    revoke.mutate(pendingRevoke.id, {
      onSuccess: () => setPendingRevoke(null),
    });
  }

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        title="API keys"
        action={
          <Button onPress={() => setCreating(true)}>
            <Plus size={16} aria-hidden /> New key
          </Button>
        }
      />

      <div className="flex flex-col gap-2">
        <CopyField
          label="MCP endpoint"
          value={mcpEndpointUrl()}
          copyLabel="Copy the MCP endpoint"
        />
        <Link
          to="/docs/connect-your-agent"
          className="text-primary text-xs font-semibold"
        >
          Read the connect guide
        </Link>
      </div>

      <ConnectedApps now={now} />

      <section className="flex flex-col gap-3">
        <h2 className="text-foreground text-sm font-semibold">Keys</h2>
        <TokenListSection
          isLoading={isLoading}
          failure={failedToLoad(tokensQuery) ? tokensQuery : undefined}
          tokens={tokens}
          now={now}
          onRevoke={beginRevoke}
          onEdit={setEditing}
        />
      </section>

      <CreateTokenDialog isOpen={creating} onOpenChange={setCreating} />

      <EditTokenDialog
        token={editing}
        now={now}
        onOpenChange={(open) => {
          if (!open) {
            setEditing(null);
          }
        }}
      />

      <RevokeTokenDialog
        token={pendingRevoke}
        isRevoking={revoke.isPending}
        isError={revoke.isError === true}
        onCancel={() => setPendingRevoke(null)}
        onConfirm={confirmRevoke}
      />
    </section>
  );
}

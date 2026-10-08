import { useState } from "react";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import { PageHeader } from "@/components/patterns/PageHeader";
import { Button } from "@/components/ui/Button";
import { CopyField } from "@/components/ui/CopyField";
import { DatePicker } from "@/components/ui/DatePicker";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useGenerateScimToken,
  useRevokeScimToken,
  useScimTokens,
} from "@/features/admin/api/use-scim-token";
import type {
  ScimToken,
  ScimTokenStatus,
} from "@/features/admin/domain/scim-token";
import { generateTokenBodySchema } from "@/features/admin/domain/scim-token";
import { RequirePermission } from "@/features/admin/ui/RequirePermission";
import { ShowOnceTokenPanel } from "@/features/admin/ui/ShowOnceTokenPanel";
import { apiBaseUrl } from "@/lib/env";
import { failedToLoad } from "@/lib/failed-to-load";
import { todayIso } from "@/lib/today-iso";

const STATUS_LABELS: Record<ScimTokenStatus, string> = {
  ACTIVE: "Active",
  REVOKED: "Revoked",
  EXPIRED: "Expired",
};

interface TokenRowProps {
  token: ScimToken;
  canWrite: boolean;
  onRevoke: (tokenId: number) => void;
  isRevoking: boolean;
}

function TokenRow({
  token,
  canWrite,
  onRevoke,
  isRevoking,
}: Readonly<TokenRowProps>) {
  const createdOn = new Date(token.createdAt).toLocaleDateString();
  return (
    <div className="border-border flex items-start justify-between gap-4 rounded-md border p-4">
      <div className="flex flex-col gap-1">
        <span className="text-foreground text-sm font-medium">
          {token.label}
        </span>
        <span className="text-muted-foreground text-xs">
          {STATUS_LABELS[token.status]} · created {createdOn}
        </span>
        {token.lastUsedAt !== null && (
          <span className="text-muted-foreground text-xs">
            Last used: {new Date(token.lastUsedAt).toLocaleDateString()}
          </span>
        )}
      </div>
      {canWrite && token.status === "ACTIVE" && (
        <Button
          variant="danger"
          size="sm"
          onPress={() => onRevoke(token.id)}
          isDisabled={isRevoking}
          aria-label={`Revoke ${token.label} created ${createdOn}`}
        >
          Revoke
        </Button>
      )}
    </div>
  );
}

interface GenerateFormProps {
  onGenerated: (token: string) => void;
}

function GenerateForm({ onGenerated }: Readonly<GenerateFormProps>) {
  const [label, setLabel] = useState("");
  const [expiresOn, setExpiresOn] = useState("");
  const [error, setError] = useState<string | null>(null);
  const generateMutation = useGenerateScimToken();

  function submit() {
    const parsed = generateTokenBodySchema.safeParse({
      label,
      expiresAt: expiresOn === "" ? expiresOn : `${expiresOn}T23:59:59Z`,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Check the form.");
      return;
    }
    setError(null);
    generateMutation.mutate(parsed.data, {
      onSuccess: (res) => onGenerated(res.token),
      onError: (err) =>
        setError(
          err instanceof Error ? err.message : "Failed to generate token.",
        ),
    });
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <label
          htmlFor="scim-label"
          className="text-foreground text-xs font-medium"
        >
          Label
        </label>
        <input
          id="scim-label"
          type="text"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          placeholder="e.g. Entra provisioning"
          className="border-border bg-background text-foreground rounded-md border px-2 py-1 text-sm"
        />
      </div>
      <DatePicker
        label="Expires on"
        value={expiresOn}
        onChange={setExpiresOn}
        isRequired
        minValue={todayIso()}
      />
      <Button
        onPress={submit}
        isDisabled={generateMutation.isPending || label.trim().length === 0}
      >
        {generateMutation.isPending ? "Generating…" : "Generate token"}
      </Button>
      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
    </div>
  );
}

interface TokenSectionProps {
  tokens: ScimToken[];
  isPending: boolean;
  newToken: string | null;
  canWrite: boolean;
  showGenerate: boolean;
  onRevoke: (tokenId: number) => void;
  onGenerated: (token: string) => void;
  isRevoking: boolean;
}

function TokenSection({
  tokens,
  isPending,
  newToken,
  canWrite,
  showGenerate,
  onRevoke,
  onGenerated,
  isRevoking,
}: Readonly<TokenSectionProps>) {
  if (isPending) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  return (
    <>
      {newToken !== null && <ShowOnceTokenPanel token={newToken} />}
      {canWrite && showGenerate && (
        <div className="border-border flex flex-col gap-4 rounded-md border p-4">
          <p className="text-sm font-medium">Generate a new SCIM token</p>
          <GenerateForm onGenerated={onGenerated} />
        </div>
      )}
      {tokens.length === 0 && newToken === null && (
        <p className="text-muted-foreground text-sm">
          No SCIM token configured for this organization.
        </p>
      )}
      {tokens.length > 0 && (
        <div className="flex flex-col gap-3">
          {tokens.map((t) => (
            <TokenRow
              key={t.id}
              token={t}
              canWrite={canWrite}
              onRevoke={onRevoke}
              isRevoking={isRevoking}
            />
          ))}
        </div>
      )}
    </>
  );
}

function DirectorySyncPageContent() {
  const tokensQuery = useScimTokens();
  const { data: tokens, isPending } = tokensQuery;
  const { can } = usePermissions();
  const canWrite = can("scim:write");

  const revokeMutation = useRevokeScimToken();
  const [newToken, setNewToken] = useState<string | null>(null);
  const [showGenerate, setShowGenerate] = useState(false);

  const tokenList = tokens ?? [];

  function handleGenerated(token: string) {
    setNewToken(token);
    setShowGenerate(false);
  }

  function handleRevoke(tokenId: number) {
    setNewToken(null);
    revokeMutation.mutate(tokenId);
  }

  const generateAction =
    canWrite && !showGenerate && tokens !== undefined ? (
      <Button onPress={() => setShowGenerate(true)}>
        {tokenList.length > 0 ? "Rotate token" : "New token"}
      </Button>
    ) : undefined;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Directory sync" action={generateAction} />
      <CopyField
        label="SCIM base URL"
        value={`${apiBaseUrl()}/scim/v2`}
        copyLabel="Copy SCIM base URL"
        hint="Paste this into the identity provider (Okta, Entra, Keycloak) as the SCIM endpoint."
      />
      {failedToLoad(tokensQuery) ? (
        <LoadFailed
          inline
          message="Could not load the SCIM tokens."
          query={tokensQuery}
        />
      ) : (
        <TokenSection
          tokens={tokenList}
          isPending={isPending}
          newToken={newToken}
          canWrite={canWrite}
          showGenerate={showGenerate}
          onRevoke={handleRevoke}
          onGenerated={handleGenerated}
          isRevoking={revokeMutation.isPending}
        />
      )}
    </div>
  );
}

export function DirectorySyncPage() {
  return (
    <RequirePermission permission="scim:read" title="Directory sync">
      <DirectorySyncPageContent />
    </RequirePermission>
  );
}

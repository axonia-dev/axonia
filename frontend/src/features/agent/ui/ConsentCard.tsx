import { type ReactNode, useState } from "react";

import { Button } from "@/components/ui/Button";
import { DialogFooter } from "@/components/ui/DialogFooter";

import { initialScopes } from "../domain/authorization-request";
import type { AgentScopeCode } from "../domain/personal-access-token";
import { ConsentOrganization } from "./ConsentOrganization";
import { ScopeFields } from "./TokenFields";

function Fact({
  label,
  children,
}: Readonly<{ label: string; children: ReactNode }>) {
  return (
    <div className="flex min-h-14 min-w-0 items-center justify-between gap-3 py-2 text-sm">
      <dt className="text-muted-foreground flex-none">{label}</dt>
      <dd className="text-foreground min-w-0 truncate text-right">
        {children}
      </dd>
    </div>
  );
}

interface ConsentCardProps {
  clientName: string;
  clientHost: string;
  requestedScopes: string[];
  actsAs: string;
  returnsTo: string | null;
  isPending: boolean;
  error: string | null;
  onAllow: (scopes: AgentScopeCode[]) => void;
  onDeny: () => void;
}

export function ConsentCard({
  clientName,
  clientHost,
  requestedScopes,
  actsAs,
  returnsTo,
  isPending,
  error,
  onAllow,
  onDeny,
}: Readonly<ConsentCardProps>) {
  const [scopes, setScopes] = useState<AgentScopeCode[]>(() =>
    initialScopes(requestedScopes),
  );
  return (
    <div className="border-border bg-surface flex flex-col gap-5 rounded-xl border p-6">
      <div className="flex flex-col gap-1">
        <h1 className="font-display text-foreground text-2xl font-bold">
          Connect {clientName}
        </h1>
        <p className="text-muted-foreground font-mono text-xs">{clientHost}</p>
      </div>
      <dl className="divide-border flex flex-col divide-y">
        <Fact label="Acts as">{actsAs}</Fact>
        <Fact label="Organization">
          <ConsentOrganization />
        </Fact>
        {returnsTo !== null && <Fact label="Returns to">{returnsTo}</Fact>}
      </dl>
      <div className="flex flex-col gap-4">
        <ScopeFields scopes={scopes} onScopesChange={setScopes} />
      </div>
      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={onDeny} isDisabled={isPending}>
          Deny
        </Button>
        <Button
          onPress={() => onAllow(scopes)}
          isDisabled={scopes.length === 0}
          isPending={isPending}
        >
          Allow
        </Button>
      </DialogFooter>
    </div>
  );
}

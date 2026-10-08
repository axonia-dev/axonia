import { useState } from "react";

import { Button } from "@/components/ui/Button";

import type { PersonalToken } from "../domain/personal-access-token";
import { TokenRow } from "./TokenRow";

function isTerminal(token: PersonalToken): boolean {
  return token.status !== "ACTIVE";
}

function toggleLabel(showing: boolean, count: number): string {
  return `${showing ? "Hide" : "Show"} revoked and expired (${count})`;
}

interface TokenListProps {
  tokens: PersonalToken[];
  now: Date;
  onRevoke: (token: PersonalToken) => void;
  onEdit: (token: PersonalToken) => void;
}

function TokenRows({
  tokens,
  now,
  onRevoke,
  onEdit,
}: Readonly<TokenListProps>) {
  return (
    <ul className="flex flex-col gap-2">
      {tokens.map((token) => (
        <TokenRow
          key={token.id}
          token={token}
          now={now}
          onRevoke={onRevoke}
          onEdit={onEdit}
        />
      ))}
    </ul>
  );
}

export function TokenList({
  tokens,
  now,
  onRevoke,
  onEdit,
}: Readonly<TokenListProps>) {
  const active = tokens.filter((token) => !isTerminal(token));
  const terminal = tokens.filter(isTerminal);
  const [showing, setShowing] = useState(false);

  if (active.length === 0) {
    return (
      <div className="flex flex-col gap-2">
        <TokenRows
          tokens={terminal}
          now={now}
          onRevoke={onRevoke}
          onEdit={onEdit}
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <TokenRows
        tokens={active}
        now={now}
        onRevoke={onRevoke}
        onEdit={onEdit}
      />
      {terminal.length > 0 && (
        <Button
          variant="ghost"
          size="sm"
          className="self-start"
          aria-expanded={showing}
          onPress={() => setShowing(!showing)}
        >
          {toggleLabel(showing, terminal.length)}
        </Button>
      )}
      {showing && (
        <TokenRows
          tokens={terminal}
          now={now}
          onRevoke={onRevoke}
          onEdit={onEdit}
        />
      )}
    </div>
  );
}

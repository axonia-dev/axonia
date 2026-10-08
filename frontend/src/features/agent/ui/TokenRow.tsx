import { Pencil, Trash2 } from "lucide-react";

import { SettingsListRow } from "@/components/patterns/SettingsListRow";
import { IconButton } from "@/components/ui/IconButton";
import { StatusChip } from "@/components/ui/StatusChip";
import { cn } from "@/lib/cn";

import type { PersonalToken } from "../domain/personal-access-token";
import {
  knownScopes,
  scopeLabel,
  unknownScopes,
} from "../domain/personal-access-token";
import { tokenStatusView } from "../domain/token-status";
import { tokenTimeline } from "../domain/token-timeline";

/** Ten scopes exist. Past four, the pills stop being read and start being wallpaper. */
const SCOPE_CHIP_LIMIT = 4;

const chipClass =
  "border-border bg-surface-sunken text-muted-foreground inline-flex items-center rounded-full border px-2 py-0.5 font-mono text-[11px]";

export function ScopeChips({ scopes }: Readonly<{ scopes: string[] }>) {
  const ordered = [...knownScopes(scopes), ...unknownScopes(scopes)];
  const shown = ordered.slice(0, SCOPE_CHIP_LIMIT);
  const hidden = ordered.slice(SCOPE_CHIP_LIMIT);
  return (
    <div className="mt-1.5 flex flex-wrap gap-1">
      {shown.map((code) => (
        <span key={code} title={scopeLabel(code)} className={chipClass}>
          {code}
        </span>
      ))}
      {hidden.length > 0 && (
        <span title={hidden.join(", ")} className={chipClass}>
          +{hidden.length}
        </span>
      )}
    </div>
  );
}

interface TokenRowProps {
  token: PersonalToken;
  now: Date;
  onRevoke: (token: PersonalToken) => void;
  onEdit: (token: PersonalToken) => void;
}

export function TokenRow({
  token,
  now,
  onRevoke,
  onEdit,
}: Readonly<TokenRowProps>) {
  const status = tokenStatusView(token.status);
  const timeline = tokenTimeline(token, now);
  return (
    <SettingsListRow
      actions={
        <>
          <StatusChip tone={status.tone} label={status.label} />
          {token.status === "ACTIVE" && (
            <div className="flex flex-none items-center gap-0.5">
              <IconButton
                label={`Edit ${token.name}`}
                onPress={() => onEdit(token)}
              >
                <Pencil />
              </IconButton>
              <IconButton
                tone="danger"
                label={`Revoke ${token.name}`}
                onPress={() => onRevoke(token)}
              >
                <Trash2 />
              </IconButton>
            </div>
          )}
        </>
      }
    >
      <p className="text-foreground truncate font-semibold">{token.name}</p>
      <ScopeChips scopes={token.scopes} />
      <p className="text-muted-foreground mt-1.5 text-xs">
        {timeline.created} · {timeline.lastUsed} ·{" "}
        <span className={cn(timeline.expiringSoon && "text-warning")}>
          {timeline.expiry}
        </span>
      </p>
    </SettingsListRow>
  );
}

import { Unplug } from "lucide-react";

import { SettingsListRow } from "@/components/patterns/SettingsListRow";
import { IconButton } from "@/components/ui/IconButton";

import type { AgentConnection } from "../domain/agent-connection";
import { connectionTimeline } from "../domain/connection-timeline";
import { ScopeChips } from "./TokenRow";

interface ConnectionRowProps {
  connection: AgentConnection;
  now: Date;
  onDisconnect: (connection: AgentConnection) => void;
}

export function ConnectionRow({
  connection,
  now,
  onDisconnect,
}: Readonly<ConnectionRowProps>) {
  const timeline = connectionTimeline(connection, now);
  return (
    <SettingsListRow
      actions={
        <IconButton
          tone="danger"
          label={`Disconnect ${connection.clientName}`}
          onPress={() => onDisconnect(connection)}
        >
          <Unplug />
        </IconButton>
      }
    >
      <p className="text-foreground truncate font-semibold">
        {connection.clientName}
      </p>
      <p className="text-muted-foreground truncate font-mono text-xs">
        {connection.clientHost}
      </p>
      <ScopeChips scopes={connection.scopes} />
      <p className="text-muted-foreground mt-1.5 text-xs">
        {timeline.connected} · {timeline.lastUsed} · {timeline.ends}
      </p>
    </SettingsListRow>
  );
}

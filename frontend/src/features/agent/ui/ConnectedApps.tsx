import { type ReactNode, useState } from "react";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import { failedToLoad } from "@/lib/failed-to-load";

import {
  useDisconnectConnection,
  useMyConnections,
} from "../api/use-agent-connections";
import type { AgentConnection } from "../domain/agent-connection";
import { ConnectionRow } from "./ConnectionRow";
import { DisconnectDialog } from "./DisconnectDialog";

function ConnectedAppsSection({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <section
      className="flex flex-col gap-3"
      aria-labelledby="connected-apps-heading"
    >
      <h2
        id="connected-apps-heading"
        className="text-foreground text-sm font-semibold"
      >
        Connected apps
      </h2>
      {children}
    </section>
  );
}

/** Absent without an active connection, but present when the list could not load: an unread list
 *  is not an empty one. */
export function ConnectedApps({ now }: Readonly<{ now: Date }>) {
  const connections = useMyConnections();
  const { data } = connections;
  const disconnect = useDisconnectConnection();
  const [pending, setPending] = useState<AgentConnection | null>(null);
  const active = (data ?? []).filter(
    (connection) => connection.status === "ACTIVE",
  );

  if (failedToLoad(connections)) {
    return (
      <ConnectedAppsSection>
        <LoadFailed
          inline
          message="Could not load the connected apps."
          query={connections}
        />
      </ConnectedAppsSection>
    );
  }
  if (active.length === 0) return null;

  function begin(connection: AgentConnection) {
    disconnect.reset();
    setPending(connection);
  }

  function confirm() {
    if (pending === null) return;
    disconnect.mutate(pending.id, { onSuccess: () => setPending(null) });
  }

  return (
    <ConnectedAppsSection>
      <ul className="flex flex-col gap-2">
        {active.map((connection) => (
          <ConnectionRow
            key={connection.id}
            connection={connection}
            now={now}
            onDisconnect={begin}
          />
        ))}
      </ul>
      <DisconnectDialog
        connection={pending}
        isDisconnecting={disconnect.isPending}
        isError={disconnect.isError === true}
        onCancel={() => setPending(null)}
        onConfirm={confirm}
      />
    </ConnectedAppsSection>
  );
}

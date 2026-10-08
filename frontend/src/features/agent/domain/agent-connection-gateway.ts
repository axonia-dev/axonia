import type { AgentConnection } from "./agent-connection";
import type {
  AuthorizationOutcome,
  AuthorizationRequest,
} from "./authorization-request";

/** Port for the member's MCP connections and the consent step that creates one. */
export interface AgentConnectionGateway {
  listMine(): Promise<AgentConnection[]>;
  disconnect(id: number): Promise<void>;
  review(request: AuthorizationRequest): Promise<AuthorizationOutcome>;
  approve(
    request: AuthorizationRequest,
    scopes: string[],
    organizationId: string,
  ): Promise<AuthorizationOutcome>;
  deny(request: AuthorizationRequest): Promise<AuthorizationOutcome>;
}

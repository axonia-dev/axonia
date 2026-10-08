import type { AgentAccess } from "./agent-access";
import type {
  CreateTokenBody,
  CreateTokenResponse,
  PersonalToken,
  UpdateTokenBody,
} from "./personal-access-token";

/** Port for the member's own personal access tokens. Implemented by the HTTP adapter. */
export interface PersonalAccessTokenGateway {
  listMine(): Promise<PersonalToken[]>;
  create(body: CreateTokenBody): Promise<CreateTokenResponse>;
  revoke(id: number): Promise<void>;
  /** Replaces an active token's name and scope set. The secret is untouched. */
  update(id: number, body: UpdateTokenBody): Promise<void>;
  /** Whether this organization may connect agents at all. */
  agentAccess(): Promise<AgentAccess>;
}

import type { useAgentAccess } from "@/features/agent/api/use-personal-access-tokens";
import type { RetryableRead } from "@/lib/failed-to-load";

type AgentAccessAnswer = ReturnType<typeof useAgentAccess>;

/** The four states `useAgentAccess` can be in, named once so a test says which one it means
 *  instead of spelling out three booleans whose combination is easy to get wrong. Their
 *  consumers deliberately disagree about the last two: a gate redirects on a refusal and says so
 *  on a failure, while the documentation notice speaks only on a resolved refusal. */
export const AGENT_ACCESS: AgentAccessAnswer = {
  available: true,
  isLoading: false,
  resolved: true,
  failure: undefined,
};

export const NO_AGENT_ACCESS: AgentAccessAnswer = {
  available: false,
  isLoading: false,
  resolved: true,
  failure: undefined,
};

export const AGENT_ACCESS_LOADING: AgentAccessAnswer = {
  available: false,
  isLoading: true,
  resolved: false,
  failure: undefined,
};

const UNANSWERED_READ: RetryableRead = {
  isFetching: false,
  refetch: () => new Promise<never>(() => undefined),
};

/** The query errored: any request can drop. Neither loading nor answered. */
export const AGENT_ACCESS_UNKNOWN: AgentAccessAnswer = {
  available: false,
  isLoading: false,
  resolved: false,
  failure: UNANSWERED_READ,
};

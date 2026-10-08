import { Info } from "lucide-react";

import { useEdition } from "@/edition-context";
import { useAgentAccess } from "@/features/agent/api/use-personal-access-tokens";

/**
 * The one part of the agent documentation that depends on who is reading it, which is why it
 * is a component rather than prose in the .mdx file beside it.
 *
 * Renders only on a resolved refusal: silent while the answer is loading, so a reader whose
 * organization does have the capability never sees a refusal flash on screen, and silent on an
 * error too, because an unanswered query is not a refusal.
 *
 * The open core names no tier and no remedy: the endpoint answers with a bare boolean precisely
 * so no reason lives in a second place. An edition that sells the capability names how to get it
 * through its `AgentAccessNotice`; without one, the notice says nothing.
 *
 * Placed explicitly in the two agent pages rather than injected by group name in DocPageView:
 * that would couple the reading layout to a string in the page registry.
 */
export function AgentAvailability() {
  const { AgentAccessNotice } = useEdition();
  const { available, resolved } = useAgentAccess();

  if (available || !resolved || AgentAccessNotice === null) return null;

  return (
    <aside className="border-border bg-surface-sunken my-6 flex gap-3 rounded-lg border p-4">
      <Info
        aria-hidden
        className="text-muted-foreground mt-0.5 size-4 shrink-0"
      />
      <p className="text-foreground text-sm">
        <AgentAccessNotice />
      </p>
    </aside>
  );
}

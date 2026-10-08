import { mcpEndpointUrl } from "@/features/agent/domain/mcp";

import { claudeCodeOAuthCommand } from "./config-snippets";
import { SnippetBlock } from "./SnippetBlock";

export function OAuthCommand() {
  return (
    <div className="border-border my-4 overflow-hidden rounded-lg border shadow-sm">
      <SnippetBlock
        snippet={claudeCodeOAuthCommand(mcpEndpointUrl())}
        testId="oauth-command"
      />
    </div>
  );
}

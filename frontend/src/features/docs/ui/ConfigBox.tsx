import { useState } from "react";

import { mcpEndpointUrl } from "@/features/agent/domain/mcp";
import { cn } from "@/lib/cn";

import type { Provider } from "./config-snippets";
import { DEFAULT_PROVIDER, PROVIDERS } from "./config-snippets";
import { SnippetBlock } from "./SnippetBlock";

export function ConfigBox() {
  const endpoint = mcpEndpointUrl();
  const [active, setActive] = useState<Provider>(DEFAULT_PROVIDER);

  return (
    <div className="border-border bg-surface my-4 overflow-hidden rounded-lg border shadow-sm">
      <div
        role="tablist"
        className="border-border bg-surface-sunken flex flex-wrap gap-1 border-b px-2 pt-2"
      >
        {PROVIDERS.map((provider) => {
          const on = provider.id === active.id;
          return (
            <button
              key={provider.id}
              role="tab"
              type="button"
              aria-selected={on}
              onClick={() => setActive(provider)}
              className={cn(
                "rounded-t-lg px-4 py-2 text-sm font-semibold",
                on
                  ? "bg-surface text-foreground border-border border border-b-0"
                  : "text-muted-foreground",
              )}
            >
              {provider.label}
            </button>
          );
        })}
      </div>
      <SnippetBlock
        snippet={active.snippet(endpoint)}
        testId="config-snippet"
      />
      <p className="border-border bg-surface text-muted-foreground border-t px-4 py-3 text-xs">
        {active.note}
      </p>
    </div>
  );
}

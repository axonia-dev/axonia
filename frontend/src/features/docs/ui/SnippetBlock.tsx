import { useState } from "react";

/** A ready-to-paste snippet with a copy button, shared by every code box in the guides. */
export function SnippetBlock({
  snippet,
  testId,
}: Readonly<{ snippet: string; testId: string }>) {
  const [copiedSnippet, setCopiedSnippet] = useState<string | null>(null);

  async function copy() {
    await navigator.clipboard.writeText(snippet);
    setCopiedSnippet(snippet);
  }

  return (
    <div className="bg-surface-sunken flex items-start">
      <pre
        data-testid={testId}
        className="text-foreground min-w-0 flex-1 overflow-x-auto p-4 font-mono text-xs leading-relaxed whitespace-pre"
      >
        {snippet}
      </pre>
      <button
        type="button"
        onClick={() => void copy()}
        className="border-border-strong bg-surface text-muted-foreground hover:text-primary mt-3 mr-3 shrink-0 rounded-md border px-3 py-1 text-xs font-semibold"
      >
        {copiedSnippet === snippet ? "Copied" : "Copy"}
      </button>
    </div>
  );
}

import { useState } from "react";

import { Button } from "./Button";

interface ShowOnceSecretProps {
  label: string;
  note: string;
  secret: string;
}

export function ShowOnceSecret({
  label,
  note,
  secret,
}: Readonly<ShowOnceSecretProps>) {
  const [copied, setCopied] = useState(false);

  function handleCopy() {
    void navigator.clipboard.writeText(secret).then(() => {
      setCopied(true);
    });
  }

  return (
    <div
      role="region"
      aria-label={label}
      className="border-border bg-surface-sunken flex flex-col gap-3 rounded-md border p-4"
    >
      <p className="text-sm font-medium">{label}</p>
      <p role="note" className="text-warning text-xs">
        {note}
      </p>
      <div className="flex items-center gap-2">
        <code className="bg-background text-foreground min-w-0 flex-1 overflow-x-auto rounded px-2 py-1 font-mono text-xs break-all">
          {secret}
        </code>
        <Button variant="outline" size="sm" onPress={handleCopy}>
          {copied ? "Copied!" : "Copy"}
        </Button>
      </div>
    </div>
  );
}

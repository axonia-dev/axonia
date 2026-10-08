import type { IconName } from "lucide-react/dynamic";
import { DynamicIcon, iconNames } from "lucide-react/dynamic";
import { useState } from "react";

import { cn } from "@/lib/cn";

const QUICK_PICKS: IconName[] = [
  "file-text",
  "notebook-pen",
  "presentation",
  "file-check",
  "newspaper",
  "circle-play",
  "git-branch",
  "link",
  "code",
  "book-open",
  "video",
  "list-checks",
];

const MAX_RESULTS = 60;

interface IconPickerProps {
  value: string | null;
  onChange: (name: string) => void;
}

function IconButton({
  name,
  active,
  onChange,
}: Readonly<{
  name: IconName;
  active: boolean;
  onChange: (name: string) => void;
}>) {
  return (
    <button
      type="button"
      aria-label={name}
      aria-pressed={active}
      onClick={() => onChange(name)}
      className={cn(
        "flex size-9 items-center justify-center rounded-lg border transition-colors",
        active
          ? "border-primary text-primary"
          : "border-border text-muted-foreground hover:border-border-strong",
      )}
    >
      <DynamicIcon name={name} size={18} />
    </button>
  );
}

export function IconPicker({ value, onChange }: Readonly<IconPickerProps>) {
  const [query, setQuery] = useState("");
  const trimmed = query.trim().toLowerCase();

  const results: IconName[] = trimmed
    ? iconNames.filter((name) => name.includes(trimmed)).slice(0, MAX_RESULTS)
    : QUICK_PICKS;

  return (
    <div className="flex flex-col gap-3">
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={`Search all ${iconNames.length} icons…`}
        aria-label="Search icons"
        className="border-border-strong w-full rounded-md border px-2.5 py-1.5 text-sm"
      />
      {!trimmed && (
        <p className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
          Quick picks
        </p>
      )}
      {results.length === 0 ? (
        <p className="text-muted-foreground text-sm">No icons match</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {results.map((name) => (
            <IconButton
              key={name}
              name={name}
              active={value === name}
              onChange={onChange}
            />
          ))}
        </div>
      )}
    </div>
  );
}

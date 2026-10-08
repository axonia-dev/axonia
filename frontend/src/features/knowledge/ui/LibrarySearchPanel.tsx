import { Check } from "lucide-react";
import { useEffect, useRef } from "react";

import { SearchField } from "@/components/ui/SearchField";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";

type RowState = "idle" | "attaching" | "attached";

interface Props {
  slug: string;
  query: string;
  attachedArtifactIds: number[];
  attachingArtifactId: number | null;
  onQueryChange: (query: string) => void;
  onAttach: (artifactId: number) => void;
  onCreate: () => void;
}

export function LibrarySearchPanel({
  slug,
  query,
  attachedArtifactIds,
  attachingArtifactId,
  onQueryChange,
  onAttach,
  onCreate,
}: Readonly<Props>) {
  const searchRef = useRef<HTMLInputElement>(null);
  const results = useArtifacts(slug, { q: query.trim(), size: 5 });

  useEffect(() => {
    searchRef.current?.focus();
  }, []);
  const attached = new Set(attachedArtifactIds);
  const trimmed = query.trim();

  function stateOf(artifactId: number): RowState {
    if (attached.has(artifactId)) {
      return "attached";
    }
    return artifactId === attachingArtifactId ? "attaching" : "idle";
  }

  // What is already on the slot sinks below what is still on offer, so a reopened
  // dialog leads with the artifacts the curator can still act on.
  const items = [...(results.data?.content ?? [])].sort(
    (a, b) => Number(attached.has(a.id)) - Number(attached.has(b.id)),
  );

  const createLabel = trimmed
    ? `+ Create "${trimmed}" as a new artifact`
    : "+ Create a new artifact";

  return (
    <div className="flex flex-col gap-3">
      <SearchField
        label="Search the library"
        value={query}
        onChange={onQueryChange}
        placeholder="Title…"
        inputRef={searchRef}
      />

      {items.length > 0 ? (
        <ul className="border-border divide-border divide-y overflow-hidden rounded-md border">
          {items.map((artifact) => (
            <li key={artifact.id}>
              <ResultRow
                artifact={artifact}
                state={stateOf(artifact.id)}
                onAttach={() => onAttach(artifact.id)}
              />
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-muted-foreground px-1 text-sm">
          {trimmed
            ? "No matches in the library."
            : "Type to search the library."}
        </p>
      )}

      {results.data !== undefined &&
        results.data.totalElements > items.length &&
        items.length > 0 && (
          <p className="text-muted-foreground px-1 text-xs">
            Showing {items.length} of {results.data.totalElements}: refine the
            search to narrow the list.
          </p>
        )}

      <button
        type="button"
        onClick={onCreate}
        className="border-border-strong text-primary hover:bg-surface-sunken focus-visible:ring-focus-ring flex w-full cursor-pointer items-center gap-2 rounded-md border border-dashed px-3 py-2 text-left text-sm font-semibold transition-colors outline-none focus-visible:ring-2"
      >
        {createLabel}
      </button>
    </div>
  );
}

interface RowProps {
  artifact: Artifact;
  state: RowState;
  onAttach: () => void;
}

/**
 * A row that is no longer on offer says so with aria-disabled, and stays a button in every
 * state. Were it to change element or leave the tab order the moment the attach lands, the
 * browser would drop the curator's focus onto <body> and the dialog would stop answering Escape.
 */
function ResultRow({ artifact, state, onAttach }: Readonly<RowProps>) {
  const spent = state !== "idle";

  return (
    <button
      type="button"
      aria-disabled={spent}
      onClick={() => {
        if (!spent) {
          onAttach();
        }
      }}
      className={`focus-visible:ring-focus-ring flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm transition-colors outline-none focus-visible:ring-2 focus-visible:ring-inset ${
        state === "attached"
          ? "bg-surface-sunken text-muted-foreground cursor-default"
          : "hover:bg-surface-sunken cursor-pointer"
      }`}
    >
      <span className="flex min-w-0 items-center gap-2.5">
        <TypeIcon
          typeId={artifact.typeId}
          label={artifact.type}
          size={15}
          className="p-1"
        />
        <span className="truncate">
          {artifact.title}{" "}
          <span className="text-muted-foreground">· {artifact.type}</span>
        </span>
      </span>
      <RowAction state={state} />
    </button>
  );
}

function RowAction({ state }: Readonly<{ state: RowState }>) {
  if (state === "attached") {
    return (
      <span className="text-success flex shrink-0 items-center gap-1 text-xs font-semibold">
        <Check size={13} aria-hidden="true" />
        Attached
      </span>
    );
  }
  if (state === "attaching") {
    return (
      <span className="text-muted-foreground shrink-0 text-xs font-semibold">
        Attaching…
      </span>
    );
  }
  return (
    <span className="text-primary shrink-0 text-xs font-semibold">
      ＋ Attach
    </span>
  );
}

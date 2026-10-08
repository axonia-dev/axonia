import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { Pagination } from "@/components/patterns/Pagination";
import { SearchField } from "@/components/ui/SearchField";
import { Select } from "@/components/ui/Select";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import type { ArtifactSearchParams } from "@/features/knowledge/domain/knowledge-gateway";
import { ArtifactList } from "@/features/knowledge/ui/ArtifactList";
import { LibraryHeader } from "@/features/knowledge/ui/LibraryHeader";
import { SessionContextStrip } from "@/features/knowledge/ui/SessionContextStrip";
import {
  ANY_SESSION,
  NO_SESSION,
  SessionFilter,
} from "@/features/knowledge/ui/SessionFilter";
import { ALL_TYPES, TypeChips } from "@/features/knowledge/ui/TypeChips";

const PAGE_SIZE = 20;

type Sort = "newest" | "mostAppreciated";
const DEFAULT_SORT: Sort = "newest";
const SORT_OPTIONS: ReadonlyArray<{ id: Sort; label: string }> = [
  { id: "newest", label: "Newest" },
  { id: "mostAppreciated", label: "Most loved" },
];

interface FilterState {
  q: string;
  typeFilter: number;
  tag: string | null;
  session: number;
  sort: Sort;
  page: number;
}

function buildSearchParams(filters: FilterState): ArtifactSearchParams {
  const { q, typeFilter, tag, session, sort, page } = filters;
  return {
    page,
    size: PAGE_SIZE,
    ...(q.trim() !== "" ? { q: q.trim() } : {}),
    ...(typeFilter !== ALL_TYPES ? { type: typeFilter } : {}),
    ...(tag !== null ? { tag } : {}),
    ...(session !== ANY_SESSION ? { session } : {}),
    ...(sort !== DEFAULT_SORT ? { sort } : {}),
  };
}

function narrowsResults(params: ArtifactSearchParams): boolean {
  return [params.q, params.type, params.tag, params.session].some(
    (filter) => filter !== undefined,
  );
}

function sortFromParams(params: URLSearchParams): Sort {
  return params.get("sort") === "mostAppreciated"
    ? "mostAppreciated"
    : DEFAULT_SORT;
}

function focusedIdFromParams(params: URLSearchParams): number | undefined {
  const n = Number(params.get("focus"));
  return n || undefined;
}

function ActiveTag({
  tag,
  onClear,
}: Readonly<{ tag: string; onClear: () => void }>) {
  return (
    <button
      type="button"
      onClick={onClear}
      className="border-primary text-primary inline-flex items-center gap-1 self-start rounded-full border px-2 py-0.5 text-xs"
    >
      #{tag} ✕
    </button>
  );
}

interface ArtifactRenderers {
  renderArtifactActions?: (artifact: Artifact) => ReactNode;
  renderArtifactReactions?: (artifact: Artifact) => ReactNode;
  renderArtifactManageActions?: (artifact: Artifact) => ReactNode;
}

/** `exactOptionalPropertyTypes` refuses an explicit `undefined`, so an absent renderer has to
 *  be dropped rather than forwarded. Module level because three conditional spreads at the
 *  call site push `LibraryView` past the complexity ceiling on their own. */
function definedRenderers(renderers: ArtifactRenderers): ArtifactRenderers {
  return Object.fromEntries(
    Object.entries(renderers).filter(([, render]) => render !== undefined),
  );
}

export function LibraryView({
  slug,
  ...renderers
}: Readonly<{ slug: string } & ArtifactRenderers>) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [q, setQ] = useState(() => searchParams.get("q") ?? "");
  const [typeFilter, setTypeFilter] = useState<number>(ALL_TYPES);
  const [tag, setTag] = useState<string | null>(() => searchParams.get("tag"));
  const [session, setSession] = useState<number>(ANY_SESSION);
  const [sort, setSort] = useState<Sort>(() => sortFromParams(searchParams));
  const focusedId = focusedIdFromParams(searchParams);
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (q.trim() !== "") {
            next.set("q", q.trim());
          } else {
            next.delete("q");
          }
          return next;
        },
        { replace: true },
      );
    }, 300);
    return () => clearTimeout(timer);
  }, [q, setSearchParams]);

  function resetTo<T>(set: (value: T) => void) {
    return (value: T) => {
      set(value);
      setPage(0);
    };
  }

  const params = buildSearchParams({ q, typeFilter, tag, session, sort, page });
  const artifacts = useArtifacts(slug, params);
  const data = artifacts.data;
  const totalPages = data?.totalPages ?? 0;

  return (
    <section className="flex flex-col gap-6">
      <LibraryHeader slug={slug} />

      <div className="border-border bg-surface-sunken flex flex-col gap-3 rounded-lg border p-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <SearchField
            label="Search the library"
            value={q}
            onChange={resetTo(setQ)}
            placeholder="Search talks, RFCs, notes…"
            className="flex-1"
          />
          <SessionFilter
            slug={slug}
            value={session}
            onChange={resetTo(setSession)}
          />
          <Select
            label="Sort"
            hideLabel
            value={sort}
            options={SORT_OPTIONS}
            onChange={resetTo(setSort)}
          />
        </div>

        <TypeChips selected={typeFilter} onSelect={resetTo(setTypeFilter)} />
      </div>

      {tag !== null && <ActiveTag tag={tag} onClear={() => setTag(null)} />}

      {session > NO_SESSION && (
        <SessionContextStrip slug={slug} sessionId={session} />
      )}

      <ArtifactList
        slug={slug}
        artifacts={data?.content ?? []}
        isLoading={artifacts.isLoading}
        isError={artifacts.isError}
        isFiltered={narrowsResults(params)}
        onTagClick={resetTo(setTag)}
        focusedId={focusedId}
        {...definedRenderers(renderers)}
      />

      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
        />
      )}
    </section>
  );
}
